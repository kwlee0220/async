package async.support;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import net.jcip.annotations.GuardedBy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractThreadedStartable extends AbstractStartable {
	protected static final int STATE_THRD_STOPPED = 0;
	protected static final int STATE_THRD_STARTING = 1;
	protected static final int STATE_THRD_RUNNING = 2;
	protected static final int STATE_THRD_STOPPING = 3;
	protected static final int STATE_THRD_FAILED = 4;
	
	private boolean m_manualStartNotification = false;
	
	private final ReentrantLock m_threadStateLock = new ReentrantLock();
	@GuardedBy("m_threadStateLock") private final Condition m_threadStateChanged = m_threadStateLock.newCondition();
	@GuardedBy("m_threadStateLock") private int m_threadState = STATE_THRD_STOPPED;
	
	protected abstract void runThreadedWork() throws Throwable;
	
	protected AbstractThreadedStartable() {
		m_threadState = STATE_THRD_STOPPED;
	}
	
	protected void setManualStartNotification(boolean flag) {
		m_manualStartNotification = flag;
	}
	
	protected ReentrantLock getThreadStateLock() {
		return m_threadStateLock;
	}
	
	protected int getThreadStateInGuard() {
		return m_threadState;
	}
	
	protected final boolean isThreadRunning() {
		m_threadStateLock.lock();
		try {
			return m_threadState == STATE_THRD_RUNNING;
		}
		finally {
			m_threadStateLock.unlock();
		}
	}
	
	protected final boolean isThreadStopPending() {
		m_threadStateLock.lock();
		try {
			return m_threadState == STATE_THRD_STOPPING;
		}
		finally {
			m_threadStateLock.unlock();
		}
	}

	@Override
	protected final void startStartable() throws Exception {
		m_threadStateLock.lock();
		try {
			// Startable 상태를 'STARTING' 상태로 만들어 놓고, 쓰레드를 생성한다.
			// 생성된 쓰레드에서 Startable의 상태를 RUNNING으로 하기 때문에
			// 여기서는 단순히 상태에서 'STARTING'에서 다른 상태로 바뀔 때까지 대기한다.
			//
			m_threadState = STATE_THRD_STARTING;
			m_threadStateChanged.signalAll();
			
			Thread thread = new Thread(m_worker);
			thread.start();
			
			while ( m_threadState == STATE_THRD_STARTING ) {
				m_threadStateChanged.await();
			}
			
			// 만일 쓰레드 생성 과정에서 문제가 발생하는 경우는 Startable 상태가
			// 'RUNNING' 외에 다른 상태도 될 수 있기 때문에, 그런 경우는 예외를 발생시킨다.
			if ( m_threadState != STATE_THRD_RUNNING ) {
				throw new IllegalStateException("unexpected: state=" + m_threadState);
			}
		}
		finally {
			m_threadStateLock.unlock();
		}
	}

	@Override
	protected final void stopStartable() throws Exception {
		m_threadStateLock.lock();
		try {
			if ( m_threadState == STATE_THRD_STOPPED
				&& m_threadState == STATE_THRD_FAILED ) {
				return;
			}
			else if ( m_threadState != STATE_THRD_RUNNING ) {
				throw new IllegalStateException("expected=" + m_threadState);
			}
			m_threadState = STATE_THRD_STOPPING;
			m_threadStateChanged.signalAll();
			
			while ( m_threadState != STATE_THRD_STOPPED ) {
				m_threadStateChanged.await();
			}
		}
		finally {
			m_threadStateLock.unlock();
		}
	}
	
	protected final void waitStopPending() throws InterruptedException {
		m_threadStateLock.lock();
		try {
			while ( m_threadState == STATE_THRD_STARTING || m_threadState == STATE_THRD_RUNNING  ) {
				m_threadStateChanged.await();
			}
		}
		finally {
			m_threadStateLock.unlock();
		}
	}
	
	protected final void markStarted() {
		if ( m_manualStartNotification ) {
			m_threadStateLock.lock();
			try {
				if ( m_threadState == STATE_THRD_STARTING ) {
					m_threadState = STATE_THRD_RUNNING;
					m_threadStateChanged.signalAll();
				}
			}
			finally {
				m_threadStateLock.unlock();
			}
		}
	}
	
	private final Runnable m_worker = new Runnable() {
		@Override public void run() {
			try {
				if ( !m_manualStartNotification ) {
					setState(STATE_THRD_RUNNING);
				}
				
				runThreadedWork();
			}
			catch ( Throwable e ) {
				setState(STATE_THRD_FAILED);
				AbstractThreadedStartable.this.notifyStartableFailed(e);
			}
			finally {
				setState(STATE_THRD_STOPPED);
				notifyStartableInterrupted();
			}
		}
	};
	
	private final void setState(int state) {
		m_threadStateLock.lock();
		try {
			m_threadState = state;
			m_threadStateChanged.signalAll();
		}
		finally {
			m_threadStateLock.unlock();
		}
	}
}
