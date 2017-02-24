package async.support;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import net.jcip.annotations.GuardedBy;
import utils.Errors.CheckedRunnable;
import utils.ExceptionUtils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ThreadedService extends AbstractService {
	protected static final int STATE_THRD_STOPPED = 0;
	protected static final int STATE_THRD_STARTING = 1;
	protected static final int STATE_THRD_RUNNING = 2;
	protected static final int STATE_THRD_STOPPING = 3;
	protected static final int STATE_THRD_FAILED = 4;
	
	@FunctionalInterface
	public static interface Work {
		public void run(Callback cb) throws Throwable;
	}
	
	private final Work m_work;
	private final Callback m_callback = new Callback();
	private boolean m_manualStartNotification = false;
	private String m_threadName;
	
	private final ReentrantLock m_threadStateLock = new ReentrantLock();
	@GuardedBy("m_threadStateLock") private final Condition m_threadStateChanged = m_threadStateLock.newCondition();
	@GuardedBy("m_threadStateLock") private int m_threadState = STATE_THRD_STOPPED;
	@GuardedBy("m_threadStateLock") private Exception m_cause;
	
	public static ThreadedService from(Work work) {
		return new ThreadedService(work);
	}
	
	public static ThreadedService from(Runnable runnable) {
		ThreadedService svc = new ThreadedService(new Work() {
			@Override
			public void run(Callback cb) throws Throwable {
				runnable.run();
			}
		});
		svc.setManualStartNotification(false);
		
		return svc;
	}
	
	public static ThreadedService from(CheckedRunnable runnable) {
		ThreadedService svc = new ThreadedService(new Work() {
			@Override
			public void run(Callback cb) throws Throwable {
				runnable.run();
			}
		});
		svc.setManualStartNotification(false);
		
		return svc;
	}
	
	public static ThreadedService run(Work work) throws Exception {
		ThreadedService svc = new ThreadedService(work);
		svc.start();
		
		return svc;
	}
	
	public static ThreadedService run(Runnable runnable) throws Exception {
		ThreadedService svc = ThreadedService.from(runnable);
		svc.start();
		
		return svc;
	}
	
	public static ThreadedService run(CheckedRunnable runnable) throws Exception {
		ThreadedService svc = ThreadedService.from(runnable);
		svc.start();
		
		return svc;
	}
	
	private ThreadedService(Work work) {
		m_work = work;
		m_threadState = STATE_THRD_STOPPED;
	}
	
	protected void setManualStartNotification(boolean flag) {
		m_manualStartNotification = flag;
	}
	
	protected void setThreadName(String name) {
		m_threadName = name;
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
	protected final void startService() throws Exception {
		m_threadStateLock.lock();
		try {
			// Service 상태를 'STARTING' 상태로 만들어 놓고, 쓰레드를 생성한다.
			// 생성된 쓰레드에서 Service의 상태를 RUNNING으로 하기 때문에
			// 여기서는 단순히 상태에서 'STARTING'에서 다른 상태로 바뀔 때까지 대기한다.
			//
			m_threadState = STATE_THRD_STARTING;
			m_threadStateChanged.signalAll();
			
			Thread thread = (m_threadName != null) ? new Thread(m_worker, m_threadName) 
													: new Thread(m_worker);
			thread.start();
			
			// 생성한 thread에서 상태를 'STATE_THRD_RUNNING'로 바꿀 때까지 대기한다.
			while ( m_threadState == STATE_THRD_STARTING ) {
				m_threadStateChanged.await();
			}
			
			// 일반적으로  m_threadState 값은 STATE_THRD_RUNNING가 되지만,
			// 만일 쓰레드 생성 과정에서 문제가 발생하는 경우(m_manualStartNotification가 true인 경우)는
			// m_threadState 값이 STATE_THRD_FAILED일 수도 있다.
			if ( m_threadState == STATE_THRD_FAILED ) {
				throw m_cause;
			}
			else if ( m_threadState != STATE_THRD_RUNNING ) {
				throw new AssertionError("Should not be here");
			}
		}
		finally {
			m_threadStateLock.unlock();
		}
	}

	@Override
	protected final void stopService() throws Exception {
		m_threadStateLock.lock();
		try {
			if ( m_threadState == STATE_THRD_STOPPED
				|| m_threadState == STATE_THRD_FAILED ) {
				return;
			}
			else if ( m_threadState != STATE_THRD_RUNNING ) {
				throw new AssertionError("Should not be here: " + getClass().getName()
											+ ".stopService()");
			}
			
			// assert: m_threadState == STATE_THRD_RUNNING
			
			m_threadState = STATE_THRD_STOPPING;
			m_threadStateChanged.signalAll();
			
			while ( m_threadState == STATE_THRD_STOPPING ) {
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
	
	protected final void notifyThreadStarted() {
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
	
	public class Callback {
		public void notifyStarted() {
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
	}
	
	private final Runnable m_worker = new Runnable() {
		@Override public void run() {
			try {
				if ( !m_manualStartNotification ) {
					setState(STATE_THRD_RUNNING);
				}
				
				m_work.run(m_callback);
				
				setState(STATE_THRD_STOPPED);
				notifyServiceInterrupted();
			}
			catch ( Throwable e ) {
				m_threadStateLock.lock();
				try {
					if ( m_threadState == STATE_THRD_STARTING ) {
						// m_manualStartNotification가 true인 경우, 생성된 쓰레드에서
						// 서비스 초기화 과정 중에서 예외가 발생하여 상태가 STATE_THRD_RUNNING으로
						// 전이되기 이전인 경우에는 서비스 시작 과정 중에 오류가 발생한 것으로 간주한다.
						//
						m_cause = (Exception)ExceptionUtils.unwrapThrowable(e);
						m_threadState = STATE_THRD_FAILED;
						m_threadStateChanged.signalAll();
						
						return;
					}
					else {
						// 상태가 STATE_THRD_RUNNING으로 전이된 후에 발생된 예외는 서비스 수행 과정 중에
						// 발생된 것으로 간주한다.
						// m_threadStateLock 을 release한 후에 'notifyServiceFailed'를 호출한다.
						m_threadState = STATE_THRD_FAILED;
						m_threadStateChanged.signalAll();
					}
				}
				finally {
					m_threadStateLock.unlock();
				}
				
				ThreadedService.this.notifyServiceFailed(e);
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
