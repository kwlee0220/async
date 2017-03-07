package async.support;

import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import async.OperationSchedulerProvider;
import async.OperationStoppedException;
import net.jcip.annotations.GuardedBy;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ThreadedAsyncOperation2<T> extends AbstractAsyncOperation<T> {
	private static final Logger s_logger = LoggerFactory.getLogger("ASYNC.RUNNABLE");
	
	private  static enum ThreadState {
		NOT_STARTED, RUNNING, COMPLETED, CANCELLING, CANCELLED, FAILED
	};

	private volatile Thread m_worker = null;
	private final Lock m_stateLock = new ReentrantLock();
	private final Condition m_stateChanged = m_stateLock.newCondition();
	@GuardedBy("m_runnableLock") private ThreadState m_thrdState = ThreadState.NOT_STARTED;

	protected abstract T executeOperation() throws OperationStoppedException, Exception;
	protected void cancelOperation() {
		getLogger().debug("interrupt worker thread due to no canceler");
		
		m_worker.interrupt();
	}
	
	protected ThreadedAsyncOperation2() { }
	
	protected ThreadedAsyncOperation2(Executor executor) {
		super(executor);
	}
	
	protected ThreadedAsyncOperation2(OperationSchedulerProvider scheduler) {
		super(scheduler);
	}

	@Override
	protected final void startOperation() throws Throwable {
		Utilities.runAsync(new ThreadedTask(), getExecutor());
	}

	@Override
	protected final void stopOperation() {
		m_stateLock.lock();
		try {
			switch ( m_thrdState ) {
				case NOT_STARTED:
					m_thrdState = ThreadState.CANCELLED;
					m_stateChanged.signalAll();
					break;
				case RUNNING:
					m_thrdState = ThreadState.CANCELLING;
					m_stateChanged.signalAll();
					break;
				default:
					throw new IllegalStateException("not running state");
			}
		}
		finally {
			m_stateLock.unlock();
		}
		
		cancelOperation();
	}
	
	class ThreadedTask implements Runnable {
		public void run() {
			ThreadedAsyncOperation2<T> _this = ThreadedAsyncOperation2.this;

			m_stateLock.lock();
			try {
				if ( m_thrdState == ThreadState.NOT_STARTED ) {
					m_thrdState = ThreadState.RUNNING;
					m_stateChanged.signalAll();
				}
				else if ( m_thrdState == ThreadState.CANCELLED ) {
					// 본 쓰레드가 시작되기 전에 이미 cancel된 경우
					return;
				}
				else {
					throw new IllegalStateException("not idle state");
				}
			}
			finally {
				m_stateLock.unlock();
			}
			
			m_worker = Thread.currentThread();
			try {
				_this.notifyOperationStarted();
			}
			catch ( Exception ignored ) { }

			T result = null;
			Throwable failure = null;
			try {
				result = _this.executeOperation();
				
				m_stateLock.lock();
				try {
					if ( m_thrdState == ThreadState.RUNNING ) {
						m_thrdState = ThreadState.COMPLETED;
						m_stateChanged.signalAll();
					}
					else if ( m_thrdState == ThreadState.CANCELLING ) {
						m_thrdState = ThreadState.CANCELLED;
						m_stateChanged.signalAll();
					}
				}
				finally {
					m_stateLock.unlock();
				}
			}
			catch ( InterruptedException e ) {
				m_stateLock.lock();
				try {
					if ( m_thrdState == ThreadState.CANCELLING ) {
						m_thrdState = ThreadState.CANCELLED;
						m_stateChanged.signalAll();
					}
					else if ( m_thrdState == ThreadState.RUNNING ) {
						m_thrdState = ThreadState.FAILED;
						failure = e;
						m_stateChanged.signalAll();
					}
				}
				finally {
					m_stateLock.unlock();
				}
			}
			catch ( OperationStoppedException e ) {
				// executeOperation() 수행 중 중단됨을 알려온 경우
				m_stateLock.lock();
				try {
					if ( m_thrdState == ThreadState.RUNNING ) {
						m_thrdState = ThreadState.CANCELLED;
						m_stateChanged.signalAll();
					}
				}
				finally {
					m_stateLock.unlock();
				}
			}
			catch ( Throwable e ) {
				m_stateLock.lock();
				try {
					if ( m_thrdState == ThreadState.RUNNING ) {
						m_thrdState = ThreadState.FAILED;
						failure = e;
						m_stateChanged.signalAll();
					}
				}
				finally {
					m_stateLock.unlock();
				}
			}
			
			ThreadState finalState;
			m_stateLock.lock();
			try {
				finalState = m_thrdState;
			}
			finally {
				m_stateLock.unlock();
			}
			
			switch ( finalState ) {
				case COMPLETED:
					_this.notifyOperationCompleted(result);
					break;
				case CANCELLED:
					_this.notifyOperationCancelled();
					break;
				case FAILED:
					_this.notifyOperationFailed(failure);
					break;
			}
		}
	}
}
