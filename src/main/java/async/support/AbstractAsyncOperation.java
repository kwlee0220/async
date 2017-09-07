package async.support;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.AsyncEventBus;

import async.AsyncOperation;
import async.AsyncOperationState;
import async.AsyncOperationStateChangeEvent;
import async.OperationSchedulerException;
import async.OperationSchedulerProvider;
import async.SchedulableAsyncOperation;
import net.jcip.annotations.GuardedBy;
import utils.Throwables;
import utils.Utilities;
import utils.thread.ExecutorAware;


/**
 * AbstractAsyncOperation는 비동기 연산 객체를 구현을 위한 비동기 연산 추상 객체를 정의한다.
 * <p>
 * 사용자는 AbstractAsyncOperation를 상속받아 다음과 같은 (추상) 메소드를 구현하여
 * 비동기 연산 클래스를 구현한다.
 * <dl>
 * 	<dt>{@link #startOperation()}:</dt>
 * 	<dd>비동기 연산이 시작될 때 수행할 작업을 구현해야 할 추상 메소드.
 * 		{@link #start()}가 호출되어 비동기 연산이 시작되면 <code>AbstractAsyncOperation</code>에
 * 		의해 호출된다. 만일 비동기 연산 스케쥴러가 설정된 경우는 <code>start()</code> 호출 즉시
 * 		호출되지 않고, 설정된 연산 스케쥴러가 {@link #permitToStart()}를 호출하게 되면 호출된다.
 * 		본 메소드가 호출될 때의 내부 상태 변수 {@link #m_state}는 {@link #STARTING}이다.
 * 		메소드 구현자는 연산이 시작된 경우는 반드시 {@link #notifyOperationStarted()}를
 * 		호출하여 비동기 연산이 성공적으로 시작됨을 알려야 한다.
 * 		통보 메소드는  본 메소드 수행 쓰레드에서 호출될 필요는 없고, 비동기적으로 메소드 반환 후
 * 		다른 쓰레드에서 호출되어도 무방하다.
 * 		본 메소드는  '{@link #m_aopLock}'을 획득하지 않은 상태에서 호출된다.</dd>
 * 	<dt>{@link #stopOperation()}:</dt>
 * 	<dd>비동기 연산을 중지시킬 때 구현하는 추상 메소드. {@link #cancel()} 메소드 호출로 인해 호출된다.
 * 		메소드 구현시 연산 취소를 요청한 후 바로 반환되어도 무방하다. 그러나 비동기 연산이 성공적으로
 * 		중지되면 {@link #notifyOperationCancelled()}를 호출하여 이를 알려야 한다.
 * 		본 메소드는  '{@link #m_aopLock}'을 획득하지 않은 상태에서 호출된다.
 * 	<dt>{@link #getRemoteInterfaces()}:</dt>
 * 	<dd>구현할 비동기 객체가 {@link AsyncOperation} 외에 다른 타입으로 외부로 export하는 경우
 * 		해당 타입을 반환하도록 재정의한다.
 * 	<dt>{@link #onOperationMarkCancelled()}:</dt>
 * 	<dd>{@link #markCancelled()} 호출로 인해 연산 취소가 mark되었을 때 수행할 작업을 재정의 구현한다.
 * 		만일 별도의 작업이 필요 없는 경우는재정의할 필요없다.
 * </dl>
 * 
 * 비동기 연산의 시작/완료/중지/고장의 시점은 <code>AbstractAsyncOperation</code>에서 알 수 없기 때문에
 * 클래스 구현자가 다음의 메소드를 호출하여 통보한다.
 * <dl>
 * 	<dt>{@link #notifyOperationStarted()}:</dt>
 * 	<dd>비동기 연산이 시작되었음을 통보하기 위해 제공되는 메소드이다. 일반적으로
 * 		<code>startOperation()</code>가 호출되었을 때 호출되고, 성공적으로 호출되면
 * 		비동기 연산의 상태는 {@link AsyncOperationState#RUNNING}이 된다.
 * 	<dt>{@link #notifyOperationCompleted(Object)}:</dt>
 * 	<dd>비동기 연산이 수행이 완료되었음을 통보하기 위해 제공되는 메소드이다. 비동기 연산이 시작된 후
 * 		종료되기 전에는 언제든지 호출될 수 있으며, 메소드가 성공적으로 호출되면 비동기 연산의 상태는
 * 		{@link AsyncOperationState#COMPLETED}가 된다. 만일 이미 종료된 상태에서 호출되면 무시된다.
 * 	<dt>{@link #notifyOperationCancelled()}:</dt>
 * 	<dd>비동기 연산이 수행이 중지되었음을 통보하기 위해 제공되는 메소드이다. 비동기 연산이 시작된 후
 * 		종료되기 전에는 언제든지 호출될 수 있으며, 메소드가 성공적으로 호출되면 비동기 연산의 상태는
 * 		{@link AsyncOperationState#CANCELLED}가 된다. 만일 이미 종료된 상태에서 호출되면 무시된다.
 * 	<dt>{@link #notifyOperationFailed(Throwable)}:</dt>
 * 	<dd>비동기 연산이 수행이 예외 발생으로 중지되었음을 통보하기 위해 제공되는 메소드이다.
 * 		비동기 연산이 시작된 후 종료되기 전에는  언제든지 호출될 수 있으며, 메소드가 성공적으로 호출되면
 * 		비동기 연산의 상태는 {@link AsyncOperationState#FAILED}가 된다.
 * 		만일 이미 종료된 상태에서 호출되면 무시된다.
 * </dl>
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractAsyncOperation<T> implements SchedulableAsyncOperation<T>, ExecutorAware {
	private static final Logger s_logger = LoggerFactory.getLogger("AOP");
	
	/** 연산이 시작되지 않은 상태. */
	protected static final int NOT_STARTED = 0;
	/** 연산이 시작이 요청되어 스케쥴러의 시작 허락을 대기하고 있는 상태. */
	protected static final int SCHEDULING = 1;
	/** 연산이 시작 중인 상태. 'start()' 메소드 호출 후, '{@link #notifyOperationStarted()}' 호출 이전인 상태. */
	protected static final int STARTING = 2;
	/** 연산이 수행 중인 상태. */
	protected static final int RUNNING = 3;
	/** 연산이 성공적으로 수행 완료된 상태. */ 
	protected static final int COMPLETED = 4;
	/** 연산의 수행이 예외 발생으로 수행 실패된 상태. */
	protected static final int FAILED = 5;
	/** 연산의 수행이 강제 종료 중인 상태. */
	protected static final int CANCELLING = 6;
	/** 연산 수행이 강제 종료된 상태. */
	protected static final int CANCELLED = 7;
	/** 연산 수행이 시작되기 전에 종료된 상태. */
	protected static final int CANCEL_PENDED = 8;
	/** 'CANCEL_PENDED'를 위해 취소 중인 상태. */
	protected static final int DELAYED_CANCELLING = 9;

	private static final String[] STATE_STR = new String[]{
		"NOT_STARTED", "SCHEDULING", "STARTING", "RUNNING", "COMPLETED", "FAILED",
		"CANCELLING", "CANCELLED", "CANCEL_PENDED", "DELAYED_CANCELLING",
	};
	private static final AsyncOperationState STATES[] = {
		AsyncOperationState.NOT_STARTED,
		AsyncOperationState.NOT_STARTED,
		AsyncOperationState.NOT_STARTED,
		AsyncOperationState.RUNNING,
		AsyncOperationState.COMPLETED,
		AsyncOperationState.FAILED,
		AsyncOperationState.CANCELLED,
		AsyncOperationState.CANCELLED,
		AsyncOperationState.NOT_STARTED,
		AsyncOperationState.CANCELLED,
	};

	/**
	 * 공유 멤버들의 접근을 제어하기 위한 lock 객체.
	 */
	protected final ReentrantLock m_aopLock = new ReentrantLock();
	@GuardedBy("m_aopLock") protected final Condition m_aopCond = m_aopLock.newCondition();
	@GuardedBy("m_aopLock") protected int m_state;	
	@GuardedBy("m_aopLock") private T m_result;
	@GuardedBy("m_aopLock") private Throwable m_fault;
	@GuardedBy("m_aopLock") private String[] m_capaNameList = null;
	private volatile OperationSchedulerProvider m_scheduler = null;
	private volatile AsyncEventBus m_changeNotifier;
	private volatile Executor m_executor;
	private Logger m_logger = s_logger;
	
	/**
	 * 주어진 연산을 시작시킨다.
	 * <p>
	 * 본 메소드는 연산에 대한 시작 요청을 내고 실제로 연산이 시작되기 전에 바로 반환되기 때문에,
	 * 반환 후 {@link #getState()}의 값은 일정 기간동안 계속
	 * {@link AsyncOperationState#NOT_STARTED}일 수 있다.
	 * <br>
	 * 본 메소드의 호출로 연산이 성공적으로 시작된 경우는 본 클래스를 상속한 클래스는
	 * {@link #notifyOperationStarted()}를 호출하여 연산이 시작됨을 알려야 한다.
	 * 그러나 통보 메소드는 반드시 본 메소드 수행 중에 호출될 필요는 없고,
	 * 비동기적으로 메소드 반환 후 다른 쓰레드에서 호출되어도 무방하다.
	 * <br>
	 * 본 메소드는 '{@link #m_aopLock}'을 획득하지 않은 상태에서 호출된다.
	 * 
	 * @throws Throwable 비동기 연산 시작 중 오류가 발생된 경우.
	 */
	protected abstract void startOperation() throws Throwable;
	
	/**
	 * 주어진 비동기 연산을 취소시킨다.
	 * <p>
	 * 본 메소드는 취소를 요청한 후 바로 반환되기 때문에, 실제 연산 실행 취소가 완료되기 이전에
	 * 반환될 수 있다.
	 * 또한 취소 요청 처리 중에 대상 연산의 수행이 완료되거나 실패될 수 있기 때문에
	 * 메소드 호출 이후 본 연산의 상태는 반드시 {@link AsyncOperationState#CANCELLED} 이지 않다.
	 * <br>
	 * 본 메소드 수행 중 발생되는 오류는 모두 무시된다.
	 * <br>
	 * 본 메소드는 '{@link #m_aopLock}'을 획득하지 않은 상태에서 호출된다.
	 */
	protected abstract void stopOperation();

	/**
	 * 비동기 연산 객체를 초기화한다.
	 */
	protected AbstractAsyncOperation() {
		m_state = NOT_STARTED;
		m_result = null;
		m_fault = null;
		m_executor = null;
		m_scheduler = null;
		m_changeNotifier = new AsyncEventBus(Executors.newCachedThreadPool());
	}

	/**
	 * 비동기 연산 객체를 초기화한다.
	 * <p>
	 * 비동기 연산 수행 중 쓰레드가 필요한 경우는 제공되는 쓰레드 풀의 쓰레드를 사용한다.
	 * 
	 * @param executor	비동기 연산 수행 중 사용할 쓰레드 풀 객체.
	 */
	protected AbstractAsyncOperation(Executor executor) {
		Preconditions.checkNotNull(executor, "executor was null");
		
		m_state = NOT_STARTED;
		m_result = null;
		m_fault = null;
		m_executor = executor;
		m_scheduler = null;
		m_changeNotifier = new AsyncEventBus(executor);
	}

	/**
	 * 비동기 연산 객체를 초기화한다.
	 * <p>
	 * 비동기 연산 수행 중 쓰레드가 필요한 경우는 연산 스케쥴러의 쓰레드 풀의 쓰레드를 사용한다.
	 * 
	 * @param scheduler	연동될 비동기 연산 스케쥴러
	 */
	protected AbstractAsyncOperation(OperationSchedulerProvider scheduler) {
		Preconditions.checkNotNull(scheduler, "scheduler was null");
		
		m_state = NOT_STARTED;
		m_result = null;
		m_fault = null;
		m_executor = scheduler.getExecutor();
		m_scheduler = scheduler;
		m_changeNotifier = new AsyncEventBus(m_executor != null ? m_executor : Executors.newCachedThreadPool());
	}

	@Override
	public final void setExecutor(Executor executor) {
		Preconditions.checkNotNull(executor, "executor was null");
		
		m_executor = executor;
		m_changeNotifier = new AsyncEventBus(executor);
	}
	
	/**
	 * 비동기 연산을 관리할 비동기 연산 스케쥴러를 설정한다.
	 */
	public void setOperationScheduler(OperationSchedulerProvider scheduler) {
		Preconditions.checkNotNull(scheduler, "scheduler was null");
		
		m_scheduler = scheduler;
	}
	
	/**
	 * 비동기 연산이 사용하는 쓰레드 풀({@link Executor}) 객체를 반환한다.
	 * 
	 * @return {@literal Executor} 객체.
	 */
	@Override
	public final Executor getExecutor() {
		return m_executor;
	}
	
	@Override
	public void start() throws IllegalStateException, OperationSchedulerException {
// TODO: 나중에 이것을 comment out한 것이 옳은 것인지 확인해야 함.
//		if ( m_executor == null ) {
//			throw new UninitializedException("Property 'executor' was not set, class="
//												+ getClass().getName());
//		}
		
		m_aopLock.lock();
		try {
			if ( m_state == CANCELLED ) {	// start() 호출 이전에 stop될 수 있기 때문에 확인 필요
				return;
			}
			else if ( m_state != NOT_STARTED ) {
				throw new IllegalStateException("invalid aop state=" + STATE_STR[m_state]);
			}

			setStateInGuard(SCHEDULING);
			if ( m_scheduler != null ) {
				m_aopLock.unlock();
				try {
					m_scheduler.submit(this);
				}
				catch ( OperationSchedulerException fault ) {
					setStateInGuard(FAILED);
					
					throw fault;
				}
				catch ( Throwable fault ) {
					setStateInGuard(FAILED);

					final Throwable cause = Throwables.unwrapThrowable(fault);
					throw new RuntimeException(cause.getMessage());
				}
				finally {
					m_aopLock.lock();
				}
			}
			else {
				permitToStart();
			}
			
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public boolean permitToStart() {
		m_aopLock.lock();
		try {
			if ( getLogger().isDebugEnabled() ) {
				getLogger().debug("starting: aop=" + this + ", state=" + STATE_STR[m_state]);
			}
			
			if ( m_state == SCHEDULING ) {
				setStateInGuard(STARTING);
			}
			else if ( m_state == CANCELLED ) {
				return false;
			}
			else if ( m_state > RUNNING ) {
				setStateInGuard(CANCELLED);
				
				return false;
			}
			else {
				// 이미 시작된 연산을 다시 시작시켜려는 경우.
				throw new IllegalStateException("invalid aop state=" + STATE_STR[m_state]);
			}
		}
		finally {
			m_aopLock.unlock();
		}

		getLogger().debug("starting: aop={}", this);
		
		try {
			startOperation();
		}
		catch ( Throwable fault ) {
			final Throwable cause = Throwables.unwrapThrowable(fault);
			getLogger().warn("fails to start AOP: class=" + getClass().getName(), cause);
			
			Utilities.executeAsynchronously(m_executor, new Runnable() {
				@Override public void run() {
//					if ( _getState() == STARTING ) {
//						AbstractAsyncOperation.this.notifyOperationStarted();
//					}
					
					AbstractAsyncOperation.this.notifyOperationFailed(cause);
				}
			});
		}
		
		return true;
	}
	
	@Override
	public void cancel() {
		boolean invokeStopCall = true;
		
		m_aopLock.lock();
		try {
			getLogger().debug("stopping: aop={}, state={}", this, STATE_STR[m_state]);
			
			if ( m_state > RUNNING ) {
				// 이미 종료된 경우거나 취소 중인 경우는 바로 리턴한다.
				return;
			}
			// 남은 상태: NOT_STARTED, SCHEDULING, STARTING, RUNNING

			if ( m_state == NOT_STARTED || m_state == SCHEDULING || m_state == STARTING ) {
				// notifyOperationStarted()가 호출되지 않은 상태에서
				// stop되는 경우는 'stopOperation()'이 호출되지 않도록 한다.
				// 경우에 따라서 notifyOperationStarted()는 호출되었으나 m_state 값을 변경시키기
				// 전에 'stop()'가  호출되는 경우도 있으나 이 경우도 'notifyOperationStarted()'가
				// 호출되지 않은 것으로 간주한다.
				// 또한, stopOperation()이 호출되지 않기 때문에 자체적으로 notifyOperationStopped()
				// 메소드를 직접 호출한다.
				invokeStopCall = false;
				m_state = CANCELLED;
			}
			else {
				m_state = CANCELLING;
			}
		}
		finally {
			m_aopLock.unlock();
		}
		
		if ( invokeStopCall ) {
			getLogger().debug("cancelling: {}", this);
			stopOperation();
			
			// 원래 코드에는 없었는데, stop()이 호출될 때 'STOPPED' 상태로 전이되지 않아서
			// 최근들어 수정된 부분 좀 더 자세한 체크가 필요하다.
			m_aopLock.lock();
			try {
				setStateInGuard(CANCELLED);
			}
			finally {
				m_aopLock.unlock();
			}
		}
		
		// TODO: stop() 함수에서 직접 notifyStopped() 를 부르는 것이 좀 이상하지만,
		// 이미 많은 코드가 이것에 의존하기 때문에 지금을 그냥 두고, 나중에
		// 큰 맘 먹고 수행해야 할 것 같다.
    	postStateChangeEvent(AsyncOperationState.CANCELLED);
	}

	@Override
	public AsyncOperationState getState() {
		m_aopLock.lock();
		try {
			return STATES[m_state];
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public Throwable getFailureCause() throws IllegalStateException {
		m_aopLock.lock();
		try {
			if ( m_state == FAILED ) {
				return m_fault;
			}
			else {
				throw new IllegalStateException("not failed: " + this);
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public T getResult() throws IllegalStateException {
		m_aopLock.lock();
		try {
			if ( m_state == COMPLETED ) {
				return m_result;
			}
			else {
				throw new IllegalStateException("not completed: state=" + STATE_STR[m_state]
												+ ", aop=" + this);
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public void waitForStarted() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( m_state < RUNNING ) {
				m_aopCond.await();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public boolean waitForStarted(long timeout) throws IllegalArgumentException,
													InterruptedException {
		if ( timeout < 0 ) {
			throw new IllegalArgumentException("timeout should be greater than (or equal to) zero");
		}

	    long timeoutNano = TimeUnit.MILLISECONDS.toNanos(timeout);
	    m_aopLock.lock();
		try {
			while ( true ) {
	            if ( m_state > RUNNING ) {
					return true;
	            }

	            if ( timeoutNano <= 0 ) {
	                return false;
	            }
	            timeoutNano = m_aopCond.awaitNanos(timeoutNano);
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public void waitForFinished() throws InterruptedException {
		m_aopLock.lock();
		try {
			while ( !isReallyFinished() ) {
				m_aopCond.await();
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public boolean waitForFinished(long timeout) throws IllegalArgumentException,
														InterruptedException {
		if ( timeout < 0 ) {
			throw new IllegalArgumentException("timeout should be greater than (or equal to) zero");
		}

	    long timeoutNano = TimeUnit.MILLISECONDS.toNanos(timeout);

	    m_aopLock.lock();
		try {
			while ( true ) {
	            if ( isReallyFinished() ) {
					return true;
	            }
	            
	            if ( timeoutNano <= 0 ) {
	                return false;
	            }
	            
	            timeoutNano = m_aopCond.awaitNanos(timeoutNano);
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	/**비동기 연산이 시작되었음을 통보하기 위해 제공되는 메소드이다.
	 * <p>
	 * 일반적으로 <code>startOperation()</code>가 호출되었을 때 호출되고, 성공적으로 호출되면
	 * 비동기 연산의 상태는 {@link AsyncOperationState#RUNNING}이 된다.
	 */
	public final AsyncOperationState notifyOperationStarted() {
		// 여기에 올 수 있는 상태는
		//	'PERMIT_WAITING': 정상적으로 시작 작업이 완료된 경우
		//	'DELAYED_STOPPING': 지연 취소 중인 경우.
		//	'COMPLETED', 'STOPPED', 'FAILED': STARTED 통보 지연으로 강제 종료된 경우.
		//
		m_aopLock.lock();
		try {
			switch ( m_state ) {
				case STARTING:
					setStateInGuard(RUNNING);
					break;
				case DELAYED_CANCELLING:
					break;
				case COMPLETED:
				case CANCELLED:
				case FAILED:
					getLogger().debug("notifyOperationStarted() is called but already finished: aop={}", this);
					return STATES[m_state];
				default:
					throw new AssertionError("Illegal aop state=" + m_state);
			}
        	postStateChangeEvent(AsyncOperationState.RUNNING);
		}
		finally {
			m_aopLock.unlock();
		}

		getLogger().debug("started: aop={}", this);
		return STATES[m_state];
	}
	
	/**
	 * 비동기 연산이 수행이 완료되었음을 통보한다.
	 * <p>
	 * 비동기 연산이 시작된 후 종료되기 전에는 언제든지 호출될 수 있으며,
	 * 메소드가 성공적으로 호출되면 비동기 연산의 상태는 {@link AsyncOperationState#COMPLETED}가 된다.
	 * 만일 이미 종료된 상태에서 호출되면 무시된다.
	 * 
	 * @param result	비동기 연산 수행 결과 객체. 수행 결과 값이 없는 경우는 <code>null</code>.
	 */
	public void notifyOperationCompleted(T result) {
		// 여기에 올 수 있는 상태는
		//	'STARTING': started 통보보다 이전에 호출된 경우 (started 상태가 될 때까지 대기)
		//	'RUNNING': 정상적으로 완료하는 경우 (=> 완료시킴)
		//	'STOPPING': 취소 과정 중에 완료되는 경우. (=> 완료시킴)
		//	'STOPPED': 연산이 이미 취소된  경우. (=> 완료 통보 무시)
		//	'COMPLETED': 개발자 오류로 COMPLETED 통보를 보낼 수 있음. (=> 완료 통보 무시)
		//	'FAILED': 개발자 오류로 COMPLETED 통보를  보낼 수 있음. (=> 완료 통보 무시)
		//
		m_aopLock.lock();
		try {
			// 'STARTED' 통보가 오기도 전에 완료될 수도 있기 때문
			if ( m_state == STARTING ) {
				// STARTED 통보를 대기한다.
				waitStartedNotification();
			}
			
			switch ( m_state ) {
				case RUNNING:
				case CANCELLING:
					m_result = result;
					setStateInGuard(COMPLETED);

	            	postStateChangeEvent(AsyncOperationState.COMPLETED);
					break;
				case COMPLETED:
				case FAILED:
					if ( getLogger().isDebugEnabled() ) {
						getLogger().debug("duplicate 'COMPLETED' notification: current="
										+ STATE_STR[m_state] + " -> notification ignored");
					}
				case CANCELLED:
					return;
				default:
					getLogger().error("Illegal AOP state=" + m_state);
					throw new RuntimeException("Illegal AOP state=" + STATE_STR[m_state]);
			}
		}
		finally {
			m_aopLock.unlock();
		}

		if ( getLogger().isInfoEnabled() ) {
			// ScheduledAsyncOperation인 경우는 원래 aop complete시 Logging되어서
			// 미관상 중복 logging을 막으려 log를 출력하지 않도록 한다.
			//
			if ( !(this instanceof SchedulableAsyncOperation) ) {
				getLogger().info("completed: aop=" + this + ", result=" + result);
			}
		}
	}
		 
	 /**
	  * 비동기 연산이 수행이 중지되었음을 통보한다.
	  * <p>
	  * 비동기 연산이 시작된 후 종료되기 전에는 언제든지 호출될 수 있으며, 메소드가 성공적으로
	  * 호출되면 비동기 연산의 상태는 {@link AsyncOperationState#CANCELLED}가 된다.
	  * 만일 이미 종료된 상태에서 호출되면 무시된다.
	  */
	public void notifyOperationCancelled() {
		// 여기에 올 수 있는 상태는
		//	'STARTING': STARTED 통보보다 이전에 호출된 경우, 또는 start()이 호출된 뒤
		//		'notifyOperationStarted()' 메소드가  호출되기 이전에 취소되는 경우.
		//	'RUNNING': 수행 중 취소된  경우 (=> 취소시킴)
		//	'STOPPING': 취소를 요청되었을 때 이미 취소 과정 중인 경우. (=> 취소시킴)
		//	'STOPPED': 취소를 요청되었을 때  연산이 이미 취소된  경우. (=> 취소 통보 무시)
		//	'DELAYED_STOPPING': 지연 취소 중인 경우. (=> 취소시킴)
		//
		m_aopLock.lock();
		try {
			switch ( m_state ) {
				case STARTING:	// 이 경우는 notifyOperationStarted() 메소드 호출 없이 바로
								// notifyOperationCancelled()가 호출되게 된다.
				case RUNNING:
				case CANCELLING:
				case DELAYED_CANCELLING:
					setStateInGuard(CANCELLED);
	            	postStateChangeEvent(AsyncOperationState.CANCELLED);
					break;
				case CANCELLED:
					break;
				default:
					getLogger().error("Illegal AOP state=" + m_state);
					throw new RuntimeException("Illegal AOP state=" + STATE_STR[m_state]);
			}
		}
		finally {
			m_aopLock.unlock();
		}

		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("stopped: aop=" + this);
		}
	}
		
	/**
	 * 비동기 연산이 수행이 예외 발생으로 중지되었음을 통보한다.
	 * <p>
	 * 비동기 연산이 시작된 후 종료되기 전에는  언제든지 호출될 수 있으며, 메소드가 성공적으로
	 * 호출되면 비동기 연산의 상태는 {@link AsyncOperationState#FAILED}가 된다.
	 * 만일 이미 종료된 상태에서 호출되면 무시된다.
	 * 
	 * @param fault	발생된 예외 객체.
	 */
	public void notifyOperationFailed(Throwable fault) {
		// 여기에 올 수 있는 상태는
		//	'STARTING': 'startOperation()' 호출 중에 발생된 경우 (=> 실패시킴)
		//	'RUNNING': 수행 중 오류가 발생된  경우 (=> 실패시킴)
		//	'STOPPING': 오류가 발생될 때 취소 과정 중인 경우. (=> 실패시킴)
		//	'STOPPED': 오류가 발생될 때 연산이 이미 취소된  경우. (=> 실패 통보 무시)
		//	'COMPLETED': 개발자 오류로 FAILED 통보를 보낼 수 있음. (=> 완료 통보 무시)
		//	'FAILED': 개발자 오류로 FAILED 통보를  보낼 수 있음. (=> 완료 통보 무시)
		//
		m_aopLock.lock();
		try {
			switch ( m_state ) {
				case RUNNING:
				case CANCELLING:
				case STARTING:
					m_fault = fault;
					setStateInGuard(FAILED);

	            	postStateChangeEvent(AsyncOperationState.FAILED);
					break;
				case COMPLETED:
				case FAILED:
					if ( getLogger().isDebugEnabled() ) {
						getLogger().debug("late 'FAILED' notification: current="
										+ STATE_STR[m_state] + " -> notification ignored");
					}
				case CANCELLED:
					return;
				default:
					getLogger().error("Illegal AOP state=" + m_state);
					throw new RuntimeException("Illegal AOP state=" + STATE_STR[m_state]);
			}
		}
		finally {
			m_aopLock.unlock();
		}

		getLogger().info("failed: aop={}, cause={}", this, fault);
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
	public final void setLogger(Logger logger) {
		m_logger = logger;
	}

	@Override
	public final void addStateChangeListener(Object listener) {
		m_aopLock.lock();
		try {
			m_changeNotifier.register(listener);
			
			if ( m_state >= RUNNING ) {
            	postStateChangeEvent(AsyncOperationState.RUNNING);
			}
			
			if ( isReallyFinished() ) {
            	postStateChangeEvent(getState());
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	public final void removeStateChangeListener(Object listener) {
		m_aopLock.lock();
		try {
			m_changeNotifier.unregister(listener);
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	/**
	 * Gets the internal state of this operation.
	 * <p>
	 * The return value is one of the following integer:
	 * <ul>
	 * 	<li> {@link #NOT_STARTED}
	 * 	<li> {@link #STARTING}
	 * 	<li> {@link #RUNNING}
	 * 	<li> {@link #COMPLETED}
	 * 	<li> {@link #FAILED}
	 * 	<li> {@link #CANCELLING}
	 * 	<li> {@link #CANCELLED}
	 * </ul>
	 */
	protected int _getState() {
		m_aopLock.lock();
		try {
			return m_state;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	protected static String getStateString(int state) {
		return STATE_STR[state];
	}
	
	/**
	 * Set the internal state of this operation.
	 * <p>
	 * This method should be called while the caller holds the lock {@link #m_aopLock}.
	 * 
	 * @param state	new state value.
	 */
	protected final void setStateInGuard(int state) {
		m_state = state;
		m_aopCond.signalAll();
	}
	
	private void postStateChangeEvent(final AsyncOperationState state) {
		AsyncOperationStateChangeEvent<T> changed = new AsyncOperationStateChangeEvent<>(this, state);
		AsyncUtils.runAsyncRTE(() -> m_changeNotifier.post(changed), m_executor);
	}
	
	private boolean isReallyFinished() {
		m_aopLock.lock();
		try {
			return m_state > RUNNING && m_state != CANCELLING;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	private static final long MAX_OUT_OF_ORDER_DELAY = TimeUnit.SECONDS.toNanos(3);
	// STARTED 통보가 도착할 때까지 최대 3초간 대기한다.
	// 만일 3초가 경과할 때까지 STARTED 통보가 도착하지 않으면 STARTED 통보를 무시하고,
	// 강제로 'RUNNING' 상태로 전이시킨다.
	private void waitStartedNotification() {
	    long dueNano = System.nanoTime() + MAX_OUT_OF_ORDER_DELAY;
	    
		while ( true ) {
            if ( m_state >= RUNNING ) {
				return;
            }
            
            long remainsNano = dueNano - System.nanoTime();
            if ( remainsNano <= 0 ) {
            	setStateInGuard(RUNNING);
            	postStateChangeEvent(AsyncOperationState.RUNNING);
    			
    			return;
            }
            
            try {
				m_aopCond.await(remainsNano, TimeUnit.NANOSECONDS);
			}
			catch ( InterruptedException e ) {
            	setStateInGuard(RUNNING);
            	postStateChangeEvent(AsyncOperationState.RUNNING);
			}
		}
	}
}