package async.optor;

import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.AsyncOperationStateChangeEvent;
import async.support.AbstractAsyncOperation;
import net.jcip.annotations.GuardedBy;
import utils.Utilities;
import utils.exception.Throwables;
import utils.thread.CamusExecutor;


/**
 * <code>DelayedAsyncOperation</code>은 지정된 시간 후에 주어진 {@link AsyncOperation}을
 * 수행시키는 지연 비동기 연산 클래스를 정의한다.
 * <p>
 * 본 지연 비동기 연산의 결과는 대상 비동기 연산의 결과로 사용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DelayedAsyncOperation<T> extends AbstractAsyncOperation<T>
										implements AsyncOperation<T> {
	static final Logger s_logger = LoggerFactory.getLogger("AOP.DELAYED");

	private final AsyncOperation<T> m_aop;
	private final long m_delay;
	private final CamusExecutor m_executor;

	@GuardedBy("m_aopLock") private final Condition m_futureCond;
	@GuardedBy("m_aopLock") private volatile Future<?> m_future;

	/**
	 * 지연 수행 비동기 연산 객체를 생성한다.
	 * <p>
	 * aop가 <code>null</code>인 경우는 지연 수행이 완료되고 결과 값은 <code>null</code>로 설정되고,
	 * aop가 <code>null</code>이 아닌 경우는 지정된 시간이 경과된 후 해당 비동기 연산을 시작시킨다.
	 * 이 때의 지연 수행 비동기 연산의 결과는 aop의 결과 값으로 설정된다.
	 *
	 * @param aop		지연 수행시킬 비동기 연산 객체.
	 * @param delay		지연될 시간 (단위: milliseconds).
	 * @param executor	지연 수행에 사용될 쓰레드 풀 객체.
	 * @throws IllegalArgumentException	executor가 <code>null</code>이거나,
	 * 									<code>delay</code>가 0이거나 음수인 경우.
	 */
	public DelayedAsyncOperation(AsyncOperation<T> aop, long delay, CamusExecutor executor) {
		super(executor);

		if ( delay < 0 ) {
			throw new IllegalArgumentException("delay should be greater than zero: delay=" + delay);
		}
		if ( executor == null ) {
			throw new IllegalArgumentException("executor was null");
		}

		m_aop = aop;

		m_delay = delay;
		m_executor = executor;

		m_futureCond = m_aopLock.newCondition();
	}

	/**
	 * 지연 수행 비동기 연산 객체를 생성한다.
	 * <p>
	 * 지정된 시간 동안 대기 후 완료되고 결과 값은 <code>null</code>로 설정된다.
	 *
	 * @param delay		지연될 시간 (단위: milliseconds).
	 * @param executor	지연 수행에 사용될 쓰레드 풀 객체.
	 * @throws IllegalArgumentException	executor가 <code>null</code>이거나,
	 * 									<code>delay</code>가 0이거나 음수인 경우.
	 */
	public DelayedAsyncOperation(long delay, CamusExecutor executor) {
		this(null, delay, executor);
	}
	
	/**
	 * 지연 수행시킨 비동기 연산 객체를 반환한다.
	 * 
	 * @return	비동기 연산 객체.
	 */
	public final AsyncOperation<T> getDelayedAsyncOperation() {
		return m_aop;
	}
	
	/**
	 * 지연 시간을 반환한다.
	 * 
	 * @return	지연 시간 (단위: milli-second).
	 */
	public final long getDelay() {
		return m_delay;
	}

	public String toString() {
		return "Delayed[" + m_delay + "]: op=" + m_aop;
	}

	@Override
	protected void startOperation() throws Throwable {
		m_aopLock.lock();
		try {
			m_future = m_executor.schedule(new DelayedWorker(), m_delay);
			m_futureCond.signal();
		}
		finally {
			m_aopLock.unlock();
		}
		
		super.notifyOperationStarted();
	}

	@Override
	protected void stopOperation() {
		m_aopLock.lock();
		try {
			while ( m_future == null ) {
				m_futureCond.await();
			}

			if ( m_future.cancel(false) ) {
				// 대기 중에 취소된 경우는
				// 대기 future만 취소시키고 종료한다.
				this.notifyOperationCancelled();

				return;
			}
		}
		catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		finally {
			m_aopLock.unlock();
		}

		m_aop.cancel();
	}

	class DelayedWorker implements Runnable {
		@Override public void run() {
			// 지연 대기 중에 중단될 수 있기 때문에, 비동기 연산을 시작시키기 전에
			// 이미 중단된 경우는 바로 반환된다.
			if ( DelayedAsyncOperation.this._getState() > RUNNING ) {
				return;
			}
			
			if ( m_aop == null ) {
				DelayedAsyncOperation.this.notifyOperationCompleted(null);
				return;
			}
			else {
				m_aop.addStateChangeListener(new Listener());
				m_aop.start();
				if ( s_logger.isDebugEnabled() ) {
					s_logger.debug("started: delayed aop=" + m_aop);
				}
			}
		}
	}
	
	@Subscribe
	public void onDelayedAopStateChanged(AsyncOperationStateChangeEvent<T> event) {
		Utilities.checkNotNullArgument(event, "event is null");
		Preconditions.checkArgument(event.getAsyncOperation() == m_aop);

		final AsyncOperationState toState = event.getToState();
		switch ( toState ) {
			case COMPLETED:
				T result = null;
				try {
					result = m_aop.getResult();
				}
				catch ( Throwable ignored ) {
					s_logger.warn("fails to get DelayedAsyncOperation result, cause={}",
									Throwables.unwrapThrowable(ignored));
				}
				notifyOperationCompleted(result);
				break;
			case CANCELLED:
				notifyOperationCancelled();
				break;
			case FAILED:
				Throwable cause = null;
				try {
					cause = m_aop.getFailureCause();
				}
				catch ( Throwable ignored ) {
					s_logger.warn("fails to get DelayedAsyncOperation fault cause, cause={}", 
									Throwables.unwrapThrowable(ignored));
				}
				notifyOperationFailed(cause);
				break;
			default:
				throw new RuntimeException();
		}
	}

	class Listener implements AsyncOperationListener<T> {
		@Override public void onAsyncOperationStarted(AsyncOperation<T> aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation<T> aop, AsyncOperationState state) {
			final DelayedAsyncOperation<T> _this = DelayedAsyncOperation.this;

			switch ( state ) {
				case COMPLETED:
					T result = null;
					try {
						result = aop.getResult();
					}
					catch ( Throwable ignored ) {
						s_logger.warn("fails to get DelayedAsyncOperation result, cause="
										+ Throwables.unwrapThrowable(ignored));
					}
					_this.notifyOperationCompleted(result);
					break;
				case CANCELLED:
					_this.notifyOperationCancelled();
					break;
				case FAILED:
					Throwable cause = null;
					try {
						cause = aop.getFailureCause();
					}
					catch ( Throwable ignored ) {
						s_logger.warn("fails to get DelayedAsyncOperation fault cause, cause="
										+ Throwables.unwrapThrowable(ignored));
					}
					_this.notifyOperationFailed(cause);
					break;
				default:
					throw new RuntimeException();
			}
		}
	}
}