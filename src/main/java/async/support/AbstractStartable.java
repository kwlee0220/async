package async.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.jws.Oneway;

import org.apache.log4j.Logger;

import async.Startable;
import async.StartableListener;
import async.StartableState;
import net.jcip.annotations.GuardedBy;
import utils.ExceptionUtils;
import utils.Utilities;
import utils.thread.ExecutorAware;


/**
 * <code>AbstractStartable</code>는 {@link Startable} 인터페이스를 구현하는
 * 클래스를 쉽게 구현할 수 있도록 해주는 추상 클래스이다.
 * <p>
 * 본 클래스를 상속하여 {@literal Startable} 클래스를 구현하는 경우 반드시 다음과 같은
 * 추상 메소드를 구현하여야 하고, 각각이 호출되는 시점을 다음과 같다.
 * <dl>
 * 	<dt>{@link #startStartable()}:</dt>
 * 	<dd><code>AbstractStartable</code>이 시작되는 경우 필요한 초기화 작업을 구현하여야 한다.
 * 		{@link #start()}가 수행  중에 {@link StartableState#STOPPED} 또는
 * 		{@link StartableState#FAILED}인 상태에서 호출된다. 본 메소드가 예외 없이 성공적으로
 * 		수행되면 상태는 {@link StartableState#RUNNING}으로 전이된다. 만일 본 메소드 수행 중
 * 		예외를 발생시킨 경우는 상태가 {@link StartableState#FAILED}로 전이되고 발생된
 * 		예외는 {@link #getFailureCause()} 메소드를 통해 얻을 수 있다.
 * 		본 함수를 호출하는 쓰레드는 {@link #m_lock}를 획득하지 않은 상태에 호출된다.
 * 	</dd>
 * 	<dt>{@link #stopStartable()}:</dt>
 * 	<dd><code>AbstractStartable</code>이 작업을 중지/종료할 때 필요한 종료화 작업을 구현하여야 한다.
 * 		본 메소드는 작업 수행 중 {@link #stop()} 메소드가 호출되는 경우와 작업 수행 중 예외가 발생하여
 * 		작업이 종료되는 경우에 호출된다. 전자의 경우 메소드가 호출되는 시점에서의 상태는
 * 		{@link StartableState#STOPPED}이고, 후자의 경우는 {@link StartableState#FAILED}이다.
 * 		또한 전자의 경우 {@link #stopStartable()}가 예외를 발생시키면 상태가
 * 		{@link StartableState#FAILED} 상태로 전이되고 발생된 예외는 {@link #getFailureCause()}
 * 		메소드를 통해 얻을 수 있다.
 * 		본 함수를 호출하는 쓰레드는 {@link #m_lock}를 획득하지 않은 상태에 호출된다.
 * 	</dd>
 * </dl>
 * 
 * 또한 본 클래스를 상속하여 {@literal Startable} 작업 수행 중 오류가 발생되면
 * {@link #notifyStartableFailed(Throwable)}를 호출하여 <code>AbstractStartable</code>에게
 * 발생된 예외를 보고한다. 본 메소드가 호출되면 {@literal Startable}는 
 * {@link StartableState#FAILED} 상태로 전이된다. 이때 호출 당시의 상태가 {@link StartableState#RUNNING}
 * 인 경우는 자동으로 {@link #stopStartable()}를 호출한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractStartable implements Startable, ExecutorAware {
	private static final Logger s_logger = Logger.getLogger("STARTABLE");
	
	private static final int STATE_STOPPED = 0;		// 작업이 종료된 상태.
	private static final int STATE_STARTING = 1;	// 작업이 시작 중인 상태.
	private static final int STATE_WORKING = 2;		// 작업이 수행 중인 상태.
	private static final int STATE_STOPPING = 3;	// 작업이 종류 중인 상태.
	private static final int STATE_FAILING = 4;		// 작업 수행 중 오류 발생으로 이를 처리하는 중인 상태.
	private static final int STATE_FAILED = 5;		// 오류로 작업이 중지된 상태.
	
	private static final StartableState[] STATE_MAP = new StartableState[]{
		StartableState.STOPPED, StartableState.STOPPED,
		StartableState.RUNNING, StartableState.RUNNING,
		StartableState.FAILED, StartableState.FAILED,
	};
	
	// properties (BEGIN)
	private volatile Executor m_executor;
	// properties (END)
	
	protected volatile Logger m_logger = s_logger;

	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private int m_state = STATE_STOPPED;
	@GuardedBy("m_lock") private Throwable m_failureCause;
	@GuardedBy("m_lock") private FailureHandler m_failureHandler;
	@GuardedBy("m_lock") private final List<StartableListener> m_listeners;
	
	/**
	 * 작업이 시작할 때 <code>AbstractStartable</code>에 의해
	 * 호출되는 메소드.
	 * <p>
	 * 본 메소드는 {@link #start()}가 호출될 때 정지 상태인 경우만 호출된다.
	 * @throws Exception TODO
	 */
	protected abstract void startStartable() throws Exception;
		
	/**
	 * <code>AbstractStartable</code> 작업이 종료될 때 <code>AbstractStartable</code>에 의해
	 * 호출되는 메소드.
	 * <p>
	 * 본 메소드는 {@link #stop()}가 호출될 때  수행 상태인 경우만 호출된다.
	 * @throws Exception TODO
	 */
	protected abstract void stopStartable() throws Exception;
	
	/**
	 * <code>AbstractStartable</code> 객체를 생성한다.
	 * <p>
	 * 객체가 생성되면 상태는 <code>STOPPED</code>가 된다.
	 */
	protected AbstractStartable() {
		m_state = STATE_STOPPED;
		m_failureCause = null;
		m_listeners = new CopyOnWriteArrayList<StartableListener>();
	}
	
	/**
	 * 쓰레드 풀 객체를 설정한다.
	 * <p>
	 * 설정된 쓰레드 풀은 상태 변화시 해당 리스너 객체들에게 비동기적으로 통보할 때 사용할
	 * 쓰레드를 할당 받을 때 사용한다.
	 * 
	 * @param executor	쓰레드 풀 객체.
	 */
	@Override
	public void setExecutor(Executor executor) {
		m_executor = executor;
	}

	@Override
	public final StartableState getState() {
		m_lock.lock();
		try {
			return STATE_MAP[m_state];
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public final boolean isRunning() {
		m_lock.lock();
		try {
			return m_state == STATE_WORKING || m_state == STATE_STOPPING;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public final boolean isStopped() {
		m_lock.lock();
		try {
			return m_state == STATE_STOPPED;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public final boolean isFailed() {
		m_lock.lock();
		try {
			return m_state == STATE_FAILED;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	public void assertRunning() {
		if ( !isRunning() ) {
			throw new IllegalStateException(String.format("%s: not running", getClass().getSimpleName()));
		}
	}
	
	protected final boolean waitStateAware(long timeout) throws InterruptedException {
		long now = System.currentTimeMillis();
		long due = now + timeout;
		
		m_lock.lock();
		try {
			while ( true ) {
				long remains = due - now;
				if ( remains <= 0 ) {
					return true;
				}
				
				switch ( m_state ) {
					case STATE_STARTING:
					case STATE_WORKING:
						if ( m_cond.await(remains, TimeUnit.MILLISECONDS) ) {
							return true;
						}
						
						now = System.currentTimeMillis();
						break;
					default:
						return false;
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	/**
	 * 작업을 시작시킨다.
	 * <p>
	 * 본 메소드는 {@link StartableState#STOPPED} 또는 {@link StartableState#FAILED}인
	 * 상태에서만 호출 가능하다. 다른 상태이거나 다른 쓰레드에 의해 {@link #start()}가
	 * 수행 중인 경우면 {@link IllegalStateException} 예외를 발생시킨다.
	 * <p>
	 * 메소드가 호출되면 내부적으로 {@link #startStartable()}를 호출하여 메소드가 성공적으로
	 * 반환되는 경우는 {@link StartableState#RUNNING} 상태로 전이되고,
	 * 등록된 모든 상태 변화 리스너들에게 상태 전이를 통보한다.
	 * 그러나 {@link #startStartable()} 호출시 예외를 발생시키거나 수행 과정에서
	 * {@link #notifyStartableFailed(Throwable)}가 호출되어 오류가 보고된 경우는 
	 * {@link StartableState#FAILED} 상태로 전이되고  등록된 모든 상태 변화 리스너들에게
	 * 상태 전이를 통보한다.
	 * 
	 * @throws IllegalStateException	시작시킬 때의 상태가 {@link StartableState#STOPPED}가
	 * 								아닌 경우.
	 */
	@Override
	public void start() throws Exception {
//		if ( m_executor == null ) {
//			throw new UninitializedException("Executor has not been set");
//		}
		
		m_lock.lock();
		try {
			// 'STATE_STOPPED' 또는 'STATE_FAILED' 상태인 경우는 상태를 STATE_STARTING으로 바꾸고
			// 'startStartable()'를 호출할 준비를 한다.
			// 그외의 상태는 상태 오류 예외를 발생시킨다.
			//
			if ( m_state == STATE_STOPPED || m_state == STATE_FAILED ) {
				m_state = STATE_STARTING;
				m_cond.signalAll();
			}
			else {
				throw new IllegalStateException("already started");
			}
		}
		finally {
			m_lock.unlock();
		}
		
		// 'm_mutex'를 갖지 않은 상태에서 'startStartable()'를 호출하여
		// 잠재적인 deadlock 가능성을 떨어뜨린다.
		try {
			startStartable();
			
			m_lock.lock();
			try {
				// 'startStartable()' 호출 동안 다른 상태(예: 고장상태)로 전이했을 수도 있으므로
				// 상태가 STATE_STARTING인 경우만 STATE_WORKING 상태로 전이시킨다.
				if ( m_state == STATE_STARTING ) {
					m_state = STATE_WORKING;
					m_cond.signalAll();
					
					// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_mutex'를 획득한
					// 상태에서 전송한다.
					notifyListenerAll(StartableState.STOPPED, StartableState.RUNNING);
				}
			}
			finally {
				m_lock.unlock();
			}
		}
		catch ( Exception e ) {
			m_lock.lock();
			try {
				m_failureCause = ExceptionUtils.unwrapThrowable(e);
				m_state = STATE_FAILED;
				m_cond.signalAll();
				
				// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_mutex'를 획득한
				// 상태에서 전송한다.
				notifyListenerAll(StartableState.STOPPED, StartableState.FAILED);
			}
			finally {
				m_lock.unlock();
			}
			
			m_logger.warn("start failed: startable=" + this + ", cause=" + m_failureCause);
			
			throw e;
		}
	}

	/**
	 * 작업을 정지시킨다.
	 * <p>
	 * 본 메소드는 {@link StartableState#RUNNING}인 상태에서만 호출 가능하다. 다른 상태에서
	 * 호출되면호출이 무시된다.
	 * <p>
	 * 본 메소드는 내부적으로 먼저 상태를 {@link StartableState#STOPPED} 상태로 전이시키고,
	 * {@link #stopStartable()}를 호출하여 성공적으로 완료되면 등록된 모든 상태 전이 리스너들에게
	 * 통보한다. 만일 {@link #stopStartable()} 수행 중 예외가 발생하거나 수행 중
	 * {@link #notifyStartableFailed(Throwable)}를 통해 오류가 보고된 경우 상태는
	 * {@link StartableState#FAILED} 상태로 전이되고 등록된 모든 상태 전이 리스너들에게 통보한다.
	 */
	@Override
	public void stop() {
		m_lock.lock();
		try {
			while ( m_state == STATE_STOPPING || m_state == STATE_FAILING ) {
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					return;
				}
			}
			
			switch ( m_state ) {
				case STATE_STOPPED:
				case STATE_FAILED:
					return;
				case STATE_WORKING:
					setStateInGuard(STATE_STOPPING);
					break;
			}
		}
		finally {
			m_lock.unlock();
		}

		// 'm_mutex'를 갖지 않은 상태에서 'stopStartable()'를 호출하여
		// 잠재적인 deadlock 가능성을 떨어뜨린다.
		try {
			stopStartable();
			
			m_lock.lock();
			try {
				// 'stopStartable()' 호출 동안 다른 상태(예: 고장상태)로 전이했을 수도 있으므로
				// 상태가 STATE_STOPPING 경우만 STATE_STOPPED 상태로 전이시킨다.
				if ( m_state == STATE_STOPPING ) {
					m_state = STATE_STOPPED;
					m_cond.signalAll();
					
					// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_mutex'를 획득한
					// 상태에서 전송한다.
					notifyListenerAll(StartableState.RUNNING, StartableState.STOPPED);
				}
			}
			finally {
				m_lock.unlock();
			}
		}
		catch ( Throwable e ) {
			m_lock.lock();
			try {
				m_failureCause = ExceptionUtils.unwrapThrowable(e);
				m_state = STATE_FAILED;
				m_cond.signalAll();
				
				// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_mutex'를 획득한
				// 상태에서 전송한다.
				notifyListenerAll(StartableState.RUNNING, StartableState.FAILED);
			}
			finally {
				m_lock.unlock();
			}
			
			m_logger.warn("raised a failure: startable=" + this + ", cause=" + m_failureCause);
		}
	}
	
	@Override
	public final void waitForFinished() throws InterruptedException {
		m_lock.lock();
		try {
			while ( m_state != STATE_STOPPED && m_state != STATE_FAILED ) {
				m_cond.await();
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	@Override
	public final boolean waitForFinished(long timeoutMillis) throws InterruptedException {
		long dueNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		
		m_lock.lock();
		try {
			while ( m_state != STATE_STOPPED && m_state != STATE_FAILED ) {
				long remainNaos = dueNanos - System.nanoTime();
				if ( remainNaos <= 0 ) {
					return false;
				}
				
				m_cond.await(remainNaos, TimeUnit.NANOSECONDS);
			}
			
			return true;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public final Throwable getFailureCause() {
		m_lock.lock();
		try {
			return m_failureCause;
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * 고장 처리기를 등록시킨다.
	 * 고장 처리기가 등록되면 {@link #notifyStartableFailed(Throwable)}가 호출되면
	 * 등록된 고장 처리기를 수행시키고 그 결과에 따라 Startable의 최종 상태를 결정한다.
	 * 고장 처리 과정을 수행하는 쓰레드 (즉, {@link #notifyStartableFailed(Throwable)}를 호출한 쓰레드)만
	 * 해당 startable의 상태를 전이시킬 수 있기 때문에, 이 쓰레드가 startable의 상태를 대기하면
	 * deadlock 상태에 빠지게 된다. 그러므로 고장 처리 쓰레드는 가능하면 고장 처리 과정에서는
	 * wait하지 않도록 한다.
	 * 만일 이미 등록된 고장 처리기를 제거하려는 경우는 {@literal null}을 인자로 호출한다.
	 * 
	 * @param handler	고장 처리 모듈
	 */
	public void setFailureHandler(FailureHandler handler) {
		m_lock.lock();
		try {
			m_failureHandler = handler;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public final void addStartableListener(StartableListener listener) {
		m_lock.lock();
		try {
			m_listeners.add(listener);
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public final void removeStartableListener(StartableListener listener) {
		m_lock.lock();
		try {
			m_listeners.remove(listener);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	class StartableFailureListener implements StartableListener {
		private final FailureListener m_listener;
		
		StartableFailureListener(FailureListener listener) {
			m_listener = listener;
		}
		
		@Override
		public void onStateChanged(Startable target, StartableState fromState, StartableState toState) {
			if ( toState == StartableState.FAILED ) {
				try {
					m_listener.onFailed(AbstractStartable.this, getFailureCause());
				}
				catch ( Exception ignored ) { }
			}
		}
	}
	
	@Override
	public final void addFailureListener(FailureListener listener) {
		addStartableListener(new StartableFailureListener(listener));
	}
	
	@Override
	public final void removeFailureListener(FailureListener listener) {
		m_lock.lock();
		try {
			int idx = -1;
			for ( int i =0; i < m_listeners.size(); ++i ) {
				StartableListener sl = m_listeners.get(i);
				if ( sl instanceof StartableFailureListener
					&& ((StartableFailureListener)sl).m_listener.equals(listener) ) {
					idx = i;
					break;
				}
			}
			if ( idx >= 0 ) {
				m_listeners.remove(idx);
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	public final void addDependencyOn(Startable dependee) {
		dependee.addStartableListener(m_dependencyEnabler);
	}

	public final void removeDependencyOn(Startable dependee) {
		dependee.removeStartableListener(m_dependencyEnabler);
	}
	
	/**
	 * 사용하는 쓰레드 풀을 반환한다.
	 * 
	 * @return	쓰레드 풀 객체.
	 */
	@Override
	public Executor getExecutor() {
		return m_executor;
	}
	
	/**
	 * 사용하는 로거를 반환한다.
	 * 
	 * @return 로거 객체.
	 */
	public final Logger getLogger() {
		return m_logger;
	}
	
	/**
	 * 로거를 설정한다.
	 * 
	 * @param logger	설정할 로거 객체.
	 */
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	protected final ReentrantLock getStateLock() {
		return m_lock;
	}

	/**
	 * 주어진 인자의 Startable 수행 중 올류가 발생하면 본 Startable도 오류가 발생한 것으로 설정한다.
	 */
	protected final void setFailureDependency(final Startable dependee) {
		dependee.addStartableListener(new StartableListener() {
			@Override @Oneway
			public void onStateChanged(Startable target, StartableState fromState, StartableState toState) {
				if ( toState == StartableState.FAILED ) {
					notifyStartableFailed(target.getFailureCause());
				}
			}
		});
	}
	
	/**
	 * Startable 작업 수행 중 오류가 발생했음을 통보한다.
	 * <p>
	 * 상태가 {@link StartableState#FAILED}인 경우는 호출이 무시된다.
	 * 호출되면 상태가 {@link StartableState#FAILED}로 전이되고 등록된
	 * 모든 상태 전이 리스너들에게 통보한다.
	 * 
	 * @param cause	오류 발생 원인 예외 객체.
	 */
	public void notifyStartableFailed(Throwable cause) {
		int prev;
		FailureHandler handler = null;
		
		m_lock.lock();
		try {
			if ( m_state == STATE_FAILED || m_state == STATE_FAILING ) {
				return;
			}

			prev = m_state;
			handler = m_failureHandler;
			setStateInGuard(STATE_FAILING);
		}
		finally {
			m_lock.unlock();
		}
		
		cause = ExceptionUtils.unwrapThrowable(cause);

		if ( handler != null ) {
			// 고장 처리기가 등록된 경우, 해당 처리기를 수행시키고
			// 그 결과에 따라 Startable의 최종 상태를 결정한다.
			// 고장 처리 과정 중 자신 startable의 상태를 기다리면 deadlock에 빠지기 때문에
			// 가능하면 고장 처리 과정에서는 wait하지 않도록 한다.
			//
			StartableState finalState = StartableState.FAILED;
			try {
				finalState = handler.handleFailure(this, cause);
			}
			catch ( Exception e ) {
				getLogger().warn("fails to reover from the failure: failure=" + cause
								+ ", recovery failure=" + e);
			}

			m_lock.lock();
			try {
				if ( finalState == StartableState.RUNNING ) {
					setStateInGuard(STATE_WORKING);
					
					if ( getLogger().isInfoEnabled() ) {
						getLogger().info("failure recovered: " + this + ", failure=" + cause);
					}
				}
				else if ( finalState == StartableState.STOPPED ) {
					setStateInGuard(STATE_STOPPED);
					notifyListenerAll(STATE_MAP[prev], StartableState.FAILED);
				}
				else if ( finalState == StartableState.FAILED ) {
					m_failureCause = cause;
					setStateInGuard(STATE_FAILED);
					notifyListenerAll(STATE_MAP[prev], StartableState.FAILED);
					
					if ( m_logger.isInfoEnabled() ) {
						m_logger.info("failed: " + this + ", cause=" + cause);
					}
				}
			}
			finally {
				m_lock.unlock();
			}
		}
		else {
			if ( prev == STATE_WORKING ) {
				try {
					stopStartable();
				}
				catch ( Throwable ignored ) { }
			}
		
			m_lock.lock();
			try {
				m_failureCause = cause;
				setStateInGuard(STATE_FAILED);
				
				notifyListenerAll(STATE_MAP[prev], StartableState.FAILED);
			}
			finally {
				m_lock.unlock();
			}
			
			if ( m_logger.isInfoEnabled() ) {
				m_logger.info("failed: " + this + ", cause=" + ExceptionUtils.unwrapThrowable(cause));
			}
		}
	}
	
	/**
	 * Startable 작업이 중단되었음을 알린다.
	 * <p>
	 * 이 함수는 'stop()' 호출로 중단되는 것이 아니라, 자체적인 이유로 중단되는 경우에
	 * 상속된 객체에서 작업이 이미 중지되고 호출하는 것으로 간주한다.
	 * 그러므로 별도의 {@link #stopStartable()} 메소드를 호출하지 않는다.
	 */
	protected void notifyStartableInterrupted() {
		m_lock.lock();
		try {
			// startStartable() 호출 직후라서 상태가 아직 STATE_STARTING 일 수도 있기 때문에
			// 상태가 변경될 때까지 대기한다.
			try {
				while ( m_state == STATE_STARTING ) {
					m_cond.await();
				}
			}
			catch ( InterruptedException e ) { }
			
			if ( m_state == STATE_WORKING || m_state == STATE_STOPPING ) {
				setStateInGuard(STATE_STOPPED);
				
				notifyListenerAll(StartableState.RUNNING, StartableState.STOPPED);
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	private final void setStateInGuard(int state) {
		m_state = state;
		m_cond.signalAll();
	}
	
	private final void notifyListenerAll(final StartableState from, final StartableState to) {
		for ( final StartableListener listener: m_listeners ) {
			Utilities.executeAsynchronously(m_executor, new Runnable() {
				@Override public void run() {
					try {
						listener.onStateChanged(AbstractStartable.this, from, to);
					}
					catch ( Throwable ignored ) {
						s_logger.warn("fails to notify 'onStateChanged' to listener=" + listener
										+ ", cause=" + ExceptionUtils.unwrapThrowable(ignored));
					}
				}
			});
		}
	}
	
	private final StartableListener m_dependencyEnabler = new StartableListener() {
		@Override @Oneway
		public void onStateChanged(Startable target, StartableState fromState, StartableState toState) {
			if ( toState == StartableState.STOPPED ) {
				Utilities.executeAsynchronously(getExecutor(), new Runnable() {
					@Override public void run() {
						AbstractStartable.this.stop();
					}
				});
			}
			else if ( toState == StartableState.RUNNING ) {
				Utilities.executeAsynchronously(getExecutor(), new Runnable() {
					@Override public void run() {
						try {
							AbstractStartable.this.start();
						}
						catch ( Exception ignored ) { }
					}
				});
			}
			else if ( toState == StartableState.FAILED ) {
				AbstractStartable.this.notifyStartableFailed(target.getFailureCause());
			}
		}
	};
}
