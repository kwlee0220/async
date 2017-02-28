package async.support;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import async.OperationStoppedException;
import net.jcip.annotations.GuardedBy;
import utils.ExceptionUtils;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractAsyncClosure<T> extends ThreadedAsyncOperation<T> {
	private static final Logger s_logger = Logger.getLogger("ASYNC.RUNNABLE");
	
	private  static enum ThreadState {
		NOT_STARTED, RUNNING, COMPLETED, CANCELLING, CANCELLED, FAILED
	};
	
	private final Runnable m_canceler;
	
	private final Lock m_stateLock = new ReentrantLock();
	private final Condition m_stateChanged = m_stateLock.newCondition();
	@GuardedBy("m_runnableLock") private ThreadState m_thrdState = ThreadState.NOT_STARTED;
	@GuardedBy("m_runnableLock") private Throwable m_failure;
	
	protected abstract T runClosure() throws Exception; 
	
	protected AbstractAsyncClosure(Runnable canceler) {
		m_canceler = canceler;
	}

	@Override
	protected T executeOperation() throws OperationStoppedException, ExecutionException {
		enter();
		try {
			T result = runClosure();
			if ( m_canceler == null && Thread.interrupted() ) {
				markCancelled();
			}
			
			return result;
		}
		catch ( InterruptedException e ) {
			markCancelled();
		}
		catch ( Exception e ) {
			markFailed(ExceptionUtils.unwrapThrowable(e));
		}
		finally {
			leave();
		}
		
		return null;
	}

	@Override
	protected void stopOperation() {
		m_stateLock.lock();
		try {
			waitWhileInGuard(ThreadState.NOT_STARTED);
			
			switch ( m_thrdState ) {
				case RUNNING:
					m_thrdState = ThreadState.CANCELLING;
					m_stateChanged.signalAll();
					break;
				default:
					throw new IllegalStateException("not running state");
			}
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			return;
		}
		finally {
			m_stateLock.unlock();
		}
		
		if ( m_canceler != null ) {
			try {
				m_canceler.run();
			}
			catch ( Exception ignored ) { }
			finally {
				m_stateLock.lock();
				try {
					m_thrdState = ThreadState.CANCELLED;
					m_stateChanged.signalAll();
				}
				finally {
					m_stateLock.unlock();
				}
			}
		}
		else {
			if ( s_logger.isDebugEnabled() ) {
				s_logger.debug("interrupt worker thread due to no canceler");
			}
			m_stateLock.lock();
			try {
				getWorkerThread().interrupt();
				
				m_thrdState = ThreadState.CANCELLED;
				m_stateChanged.signalAll();
			}
			finally {
				m_stateLock.unlock();
			}
		}
	}

	private void enter() throws OperationStoppedException {
		m_stateLock.lock();
		try {
			switch ( m_thrdState ) {
				case NOT_STARTED:
					m_thrdState = ThreadState.RUNNING;
					m_stateChanged.signalAll();
					break;
				case CANCELLED:
					throw new OperationStoppedException();
				default:
					throw new IllegalStateException("not idle state");
			}
		}
		finally {
			m_stateLock.unlock();
		}
	}
	
	private void leave() throws OperationStoppedException, ExecutionException {
		m_stateLock.lock();
		try {
			while ( m_thrdState == ThreadState.CANCELLING  ) {
				try {
					m_stateChanged.await();
				}
				catch ( InterruptedException e ) {
					throw new ExecutionException(e);
				}
			}
			
			switch ( m_thrdState ) {
				case RUNNING:
					m_thrdState = ThreadState.COMPLETED;
					m_stateChanged.signalAll();
					return;
				case CANCELLED:
					throw new OperationStoppedException();
				case FAILED:
					throw new ExecutionException(ExceptionUtils.unwrapThrowable(m_failure));
				default:
					throw new IllegalStateException("not running/cancelled state");
			}
		}
		finally {
			m_stateLock.unlock();
		}
	}
	
	private void markFailed(Throwable failure) {
		m_stateLock.lock();
		try {
			if ( m_thrdState == ThreadState.RUNNING ) {
				m_failure = failure;
				m_thrdState = ThreadState.FAILED;
				m_stateChanged.signalAll();
			}
			else {
				throw new IllegalStateException("not running state");
			}
		}
		finally {
			m_stateLock.unlock();
		}
	}
	
	private void waitWhileInGuard(ThreadState state) throws InterruptedException {
		while ( m_thrdState == state  ) {
			m_stateChanged.await();
		}
	}
}
