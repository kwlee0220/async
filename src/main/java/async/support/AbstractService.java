package async.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import async.Service;
import async.ServiceState;
import async.ServiceStateChangeListener;
import net.jcip.annotations.GuardedBy;
import utils.Errors;
import utils.ExceptionUtils;
import utils.LoggerSettable;
import utils.Utilities;
import utils.thread.ExecutorAware;


/**
 * <code>AbstractService</code>는 {@link Service} 인터페이스를 구현하는
 * 클래스를 쉽게 구현할 수 있도록 해주는 추상 클래스이다.
 * <p>
 * 본 클래스를 상속하여 {@literal Service} 클래스를 구현하는 경우 반드시 다음과 같은
 * 추상 메소드를 구현하여야 하고, 각각이 호출되는 시점을 다음과 같다.
 * <dl>
 * 	<dt>{@link #startService()}:</dt>
 * 	<dd><code>AbstractService</code>이 시작되는 경우 필요한 초기화 작업을 구현하여야 한다.
 * 		{@link #start()}가 수행  중에 {@link ServiceState#STOPPED} 또는
 * 		{@link ServiceState#FAILED}인 상태에서 호출된다. 본 메소드가 예외 없이 성공적으로
 * 		수행되면 상태는 {@link ServiceState#RUNNING}으로 전이된다. 만일 본 메소드 수행 중
 * 		예외를 발생시킨 경우는 상태가 {@link ServiceState#FAILED}로 전이되고 발생된
 * 		예외는 {@link #getFailureCause()} 메소드를 통해 얻을 수 있다.
 * 		본 함수를 호출하는 쓰레드는 {@link #m_lock}를 획득하지 않은 상태에 호출된다.
 * 	</dd>
 * 	<dt>{@link #stopService()}:</dt>
 * 	<dd><code>AbstractService</code>이 작업을 중지/종료할 때 필요한 종료화 작업을 구현하여야 한다.
 * 		본 메소드는 작업 수행 중 {@link #stop()} 메소드가 호출되는 경우와 작업 수행 중 예외가 발생하여
 * 		서비스가 종료되는 경우에 호출된다. 전자의 경우 메소드가 호출되는 시점에서의 상태는
 * 		{@link ServiceState#STOPPED}이고, 후자의 경우는 {@link ServiceState#FAILED}이다.
 * 		또한 전자의 경우 {@link #stopService()}가 예외를 발생시키면 상태가
 * 		{@link ServiceState#FAILED} 상태로 전이되고 발생된 예외는 {@link #getFailureCause()}
 * 		메소드를 통해 얻을 수 있다.
 * 		본 함수를 호출하는 쓰레드는 {@link #m_lock}를 획득하지 않은 상태에 호출된다.
 * 	</dd>
 * </dl>
 * 
 * 또한 본 클래스를 상속하여 {@literal Service} 작업 수행 중 오류가 발생되면
 * {@link #notifyServiceFailed(Throwable)}를 호출하여 <code>AbstractService</code>에게
 * 발생된 예외를 보고한다. 본 메소드가 호출되면 {@literal Service}는 
 * {@link ServiceState#FAILED} 상태로 전이된다. 이때 호출 당시의 상태가 {@link ServiceState#RUNNING}
 * 인 경우는 자동으로 {@link #stopService()}를 호출한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractService implements Service, ExecutorAware, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger("STARTABLE");

	/** 작업 중지 상태 */
	protected final static int STATE_STOPPED = 0;
	/** 작업 수행을 준비하는 상태 */
	protected final static int STATE_STARTING = 1;
	/** 작업 수행 상태 */
	protected final static int STATE_RUNNING = 2;
	/** 작업 수행 종료를 준비하는 상태 */
	protected final static int STATE_STOPPING = 3;
	/** 작업 수행 중 오류가 발생되어 이상종료를 준비하는 상태 */
	protected final static int STATE_FAILING = 4;
	/** 오류 발생되어 수행이 정지된 상태 */
	protected final static int STATE_FAILED = 5;
	
	private static final ServiceState[] STATE_MAP = new ServiceState[]{
		ServiceState.STOPPED, ServiceState.STOPPED,
		ServiceState.RUNNING, ServiceState.RUNNING,
		ServiceState.FAILED, ServiceState.FAILED,
	};
	
	// properties (BEGIN)
	private volatile Executor m_executor;
	// properties (END)
	
	protected volatile Logger m_logger = s_logger;

	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private int m_state = STATE_STOPPED;
	@GuardedBy("m_lock") private Throwable m_failureCause;
	@GuardedBy("m_lock") private final List<ServiceStateChangeListener> m_listeners;
	
	/**
	 * 서비스가 시작할 때 <code>AbstractService</code>에 의해
	 * 호출되는 메소드.
	 * <p>
	 * 본 메소드는 {@link #start()}가 호출될 때 정지 상태인 경우만 호출된다.
	 * @throws Exception TODO
	 */
	protected abstract void startService() throws Exception;
		
	/**
	 * <code>AbstractService</code> 서비스가 종료될 때 <code>AbstractService</code>에 의해
	 * 호출되는 메소드.
	 * <p>
	 * 본 메소드는 {@link #stop()}가 호출될 때  수행 상태인 경우만 호출된다.
	 * @throws Exception TODO
	 */
	protected abstract void stopService() throws Exception;
	
	protected ServiceState handleFailure(Throwable cause) {
		Errors.runQuietly(()->stopService());
		return ServiceState.FAILED;
	}
	
	/**
	 * <code>AbstractService</code> 객체를 생성한다.
	 * <p>
	 * 객체가 생성되면 상태는 <code>STOPPED</code>가 된다.
	 */
	protected AbstractService() {
		m_state = STATE_STOPPED;
		m_failureCause = null;
		m_listeners = new CopyOnWriteArrayList<ServiceStateChangeListener>();
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
	public final ServiceState getState() {
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
			return m_state == STATE_RUNNING;
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

	/**
	 * 작업을 시작시킨다.
	 * <p>
	 * 본 메소드는 {@link ServiceState#STOPPED} 또는 {@link ServiceState#FAILED}인
	 * 상태에서만 호출 가능하다. 다른 상태이거나 다른 쓰레드에 의해 {@link #start()}가
	 * 수행 중인 경우면 {@link IllegalStateException} 예외를 발생시킨다.
	 * <p>
	 * 메소드가 호출되면 내부적으로 {@link #startService()}를 호출하여 메소드가 성공적으로
	 * 반환되는 경우는 {@link ServiceState#RUNNING} 상태로 전이되고,
	 * 등록된 모든 상태 변화 리스너들에게 상태 전이를 통보한다.
	 * 그러나 {@link #startService()} 호출시 예외를 발생시키거나 수행 과정에서
	 * {@link #notifyServiceFailed(Throwable)}가 호출되어 오류가 보고된 경우는 
	 * {@link ServiceState#FAILED} 상태로 전이되고  등록된 모든 상태 변화 리스너들에게
	 * 상태 전이를 통보한다.
	 * 
	 * @throws IllegalStateException	시작시킬 때의 상태가 {@link ServiceState#STOPPED}가
	 * 								아닌 경우.
	 */
	@Override
	public final void start() throws Exception {
		m_lock.lock();
		try {
			// 'STATE_STOPPED' 또는 'STATE_FAILED' 상태인 경우는 상태를 STATE_STARTING으로 바꾸고
			// 'startService()'를 호출할 준비를 한다.
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
		
		// 'm_lock'를 갖지 않은 상태에서 'startService()'를 호출하여
		// 잠재적인 deadlock 가능성을 떨어뜨린다.
		try {
			startService();
		}
		catch ( Exception e ) {
			m_lock.lock();
			try {
				m_failureCause = ExceptionUtils.unwrapThrowable(e);
				m_state = STATE_FAILED;
				m_cond.signalAll();
				
				// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_mutex'를 획득한
				// 상태에서 전송한다.
				notifyListenerAll(ServiceState.STOPPED, ServiceState.FAILED);
			}
			finally {
				m_lock.unlock();
			}
			
			m_logger.warn("start failed: startable=" + this + ", cause=" + m_failureCause);
			
			throw e;
		}
		
		m_lock.lock();
		try {
			// 'startService()' 호출 동안 다른 상태(예: 고장상태)로 전이했을 수도 있으므로
			// 상태가 STATE_STARTING인 경우만 STATE_RUNNING 상태로 전이시킨다.
			if ( m_state == STATE_STARTING ) {
				m_state = STATE_RUNNING;
				m_cond.signalAll();
				
				// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_lock'를 획득한
				// 상태에서 전송한다.
				notifyListenerAll(ServiceState.STOPPED, ServiceState.RUNNING);
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	/**
	 * 작업을 정지시킨다.
	 * <p>
	 * 본 메소드는 {@link ServiceState#RUNNING}인 상태에서만 호출 가능하다. 다른 상태에서
	 * 호출되면호출이 무시된다.
	 * <p>
	 * 본 메소드는 내부적으로 먼저 상태를 {@link ServiceState#STOPPED} 상태로 전이시키고,
	 * {@link #stopService()}를 호출하여 성공적으로 완료되면 등록된 모든 상태 전이 리스너들에게
	 * 통보한다. 만일 {@link #stopService()} 수행 중 예외가 발생하거나 수행 중
	 * {@link #notifyServiceFailed(Throwable)}를 통해 오류가 보고된 경우 상태는
	 * {@link ServiceState#FAILED} 상태로 전이되고 등록된 모든 상태 전이 리스너들에게 통보한다.
	 */
	@Override
	public final void stop() {
		m_lock.lock();
		try {
			// 임시 상태인 경우는 해당 상태에서 벗어날 때까지 대기한다.
			while ( m_state == STATE_STOPPING || m_state == STATE_FAILING
					|| m_state == STATE_STARTING ) {
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
				case STATE_RUNNING:
					setStateInGuard(STATE_STOPPING);
					break;
				default:
					throw new AssertionError("Should not be here!!");
			}
		}
		finally {
			m_lock.unlock();
		}

		// 'm_lock'를 갖지 않은 상태에서 'stopService()'를 호출하여
		// 잠재적인 deadlock 가능성을 떨어뜨린다.
		try {
			stopService();
		}
		catch ( Throwable e ) {
			m_lock.lock();
			try {
				m_failureCause = ExceptionUtils.unwrapThrowable(e);
				m_state = STATE_FAILED;
				m_cond.signalAll();
				
				// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_lock'를 획득한
				// 상태에서 전송한다.
				notifyListenerAll(ServiceState.RUNNING, ServiceState.FAILED);
			}
			finally {
				m_lock.unlock();
			}
			
			m_logger.warn("raised a failure: startable={}, cause={}", this, m_failureCause);
			return;
		}
		
		// 'stopService()' 호출까지 성공적으로 마무리 된 상태
		
		m_lock.lock();
		try {
			// 'stopService()' 호출 동안 다른 상태(예: 고장상태)로 전이했을 수도 있으므로
			// 상태가 STATE_STOPPING 경우만 STATE_STOPPED 상태로 전이시킨다.
			if ( m_state == STATE_STOPPING ) {
				m_state = STATE_STOPPED;
				m_cond.signalAll();
				
				// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_mutex'를 획득한
				// 상태에서 전송한다.
				notifyListenerAll(ServiceState.RUNNING, ServiceState.STOPPED);
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	/**
	 * Service 작업 수행 중 오류가 발생했음을 통보한다.
	 * <p>
	 * 상태가 {@link ServiceState#FAILED}인 경우는 호출이 무시된다.
	 * 호출되면 상태가 {@link ServiceState#FAILED}로 전이되고 등록된
	 * 모든 상태 전이 리스너들에게 통보한다.
	 * 
	 * @param cause	오류 발생 원인 예외 객체.
	 */
	public void notifyServiceFailed(Throwable cause) {
		m_lock.lock();
		try {
			// 임시 상태인 경우는 해당 상태에서 벗어날 때까지 대기한다.
			while ( m_state == STATE_STOPPING || m_state == STATE_FAILING
					|| m_state == STATE_STARTING ) {
				try {
					m_cond.await();
				}
				catch ( InterruptedException e ) {
					return;
				}
			}
			
			switch ( m_state ) {
				case STATE_FAILED:
					return;
				case STATE_STOPPED:
				case STATE_RUNNING:
					setStateInGuard(STATE_FAILING);
					break;
				default:
					throw new AssertionError("Should not be here!!");
			}
		}
		finally {
			m_lock.unlock();
		}
		
		ServiceState recoveredState = ServiceState.STOPPED;
		cause = ExceptionUtils.unwrapThrowable(cause);
		try {
			recoveredState = handleFailure(cause);
		}
		catch ( Throwable e ) {
			m_lock.lock();
			try {
				m_failureCause = cause;
				m_state = STATE_FAILED;
				m_cond.signalAll();
				
				// 이벤트 순서가 바뀌지 않게 하기 위해 이벤트 전송은 'm_lock'를 획득한
				// 상태에서 전송한다.
				notifyListenerAll(ServiceState.RUNNING, ServiceState.FAILED);
			}
			finally {
				m_lock.unlock();
			}
		}

		m_lock.lock();
		try {
			if ( recoveredState == ServiceState.RUNNING ) {
				setStateInGuard(STATE_RUNNING);

				m_logger.info("failure recovered: {}, failure={}", this, cause);
			}
			else if ( recoveredState == ServiceState.STOPPED ) {
				setStateInGuard(STATE_STOPPED);

				m_logger.info("stopped due to failure: {}, cause={}", this, cause);
				notifyListenerAll(ServiceState.RUNNING, ServiceState.STOPPED);
			}
			else if ( recoveredState == ServiceState.FAILED ) {
				m_failureCause = cause;
				setStateInGuard(STATE_FAILED);
				notifyListenerAll(ServiceState.RUNNING, ServiceState.FAILED);
				
				m_logger.info("failed: {}, cause={}", this, cause);
			}
		}
		finally {
			m_lock.unlock();
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

	@Override
	public final void addStateChangeListener(ServiceStateChangeListener listener) {
		m_lock.lock();
		try {
			m_listeners.add(listener);
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public final void removeStateChangeListener(ServiceStateChangeListener listener) {
		m_lock.lock();
		try {
			m_listeners.remove(listener);
		}
		finally {
			m_lock.unlock();
		}
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
	
	@Override
	public String toString() {
		return String.format("Service[%s]", getState());
	}
	
	protected final ReentrantLock getStateLock() {
		return m_lock;
	}
	
	/**
	 * Service 서비스가 중단되었음을 알린다.
	 * <p>
	 * 이 함수는 'stop()' 호출로 중단되는 것이 아니라, 자체적인 이유로 중단되는 경우에
	 * 상속된 객체에서 서비스가 이미 중지되고 호출하는 것으로 간주한다.
	 * 그러므로 별도의 {@link #stopService()} 메소드를 호출하지 않는다.
	 */
	protected void notifyServiceInterrupted() {
		m_lock.lock();
		try {
			// 임시 상태인 경우는 해당 상태에서 벗어날 때까지 대기한다.
			while ( m_state == STATE_STOPPING || m_state == STATE_FAILING
					|| m_state == STATE_STARTING ) {
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
				case STATE_RUNNING:
					setStateInGuard(STATE_STOPPED);
					notifyListenerAll(ServiceState.RUNNING, ServiceState.STOPPED);
					break;
				default:
					throw new AssertionError("Should not be here!!");
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
	
	private final void notifyListenerAll(final ServiceState from, final ServiceState to) {
		Utilities.runAsync(()-> {
			for ( ServiceStateChangeListener listener: m_listeners ) {
				try {
					listener.onStateChanged(AbstractService.this, from, to);
				}
				catch ( Throwable ignored ) {
					s_logger.warn("(ignored) fails to notify 'onStateChanged' to listener={}, cause={}",
									listener, ExceptionUtils.unwrapThrowable(ignored));
				}
				
			}
		}, m_executor);
	}
}
