package async.optor;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.log4j.Logger;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.support.AbstractAsyncOperation;
import utils.ExceptionUtils;
import utils.Utilities;
import utils.thread.CamusExecutor;


/**
 * <code>PeriodicAsyncOperation</code>은 주어진 비동기 연산을 주기적으로 반복해서
 * 수행시키는 주기 수행 비동기 연산 클래스를 정의한다.
 * <p>
 * 주기적 반복 수행에 반복 횟수를 설정하는 경우는 주어진 횟수 만큼을 반복 수행 후
 * 연산이 종료되며 연산의 결과는 마지막으로 수행된 소속 연산의 결과로 정의된다.
 * 반복 수행되는 소속 연산이 강제 취소되는 경우는 전체 주기 수행 연산이 취소되며
 * 소속 연산이 오류가 발생하여 종료되는 경우도 전체 주기 수행 연산을 종료시키고, 해당 오류 원인이
 * 주기 수행 연산의 오류 원인으로 설정된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class PeriodicAsyncOperation extends AbstractAsyncOperation<Void> implements AsyncOperation<Void> {
	static final Logger s_logger = Logger.getLogger("AOP.PERIODIC");

	public static final int FOREVER = -1;

	private final Supplier<AsyncOperation<?>> m_supplier;
	private final long m_initDelay;
	private final long m_delay;
	private final CamusExecutor m_executor;

	private final Object m_mutex = new Object();
	private AsyncOperation<?> m_aop;				// guarded by m_mutex
	private int m_remains;						// guarded by m_mutex
	private Future<?> m_future;					// guarded by m_mutex
	private final Listener m_listener;

	/**
	 * 주기 수행 비동기 연산 객체를 생성한다.
	 * <p>
	 * 비동기 연산이 시작되면 반복적으로 {@literal fact}를 통해 비동기 연산을 생성하여
	 * 최대 {@literal count}번을 수행시킨다.
	 * 만일 {@literal count}가 {@link #FOREVER}인 경우는 무한 반복을 의미한다.
	 * <br>
	 * {@literal initDelay}가 0인 경우는 본 비동기 연산이 시작되자마자 첫 연산을
	 * 시작시키는 것을 의미하고, {@literal delay}가 0인 경우는 매 연산 수행 후 바로
	 * 다음 비동기 연산을 수행시키는 것을 의미한다.
	 *
	 * @param fact		주기적으로 수행될 비동기 연산 객체를 생성할 비동기 연산 생성기 객체.
	 * @param initDelay	첫 비동기 연산이 수행될 지연 시간 (단위 milli-seconds).
	 * @param delay		한 비동기 수행을 마친 후, 다음 비동기 수행이 시작될 때까지의
	 * 					지연 시간 (단위 milli-seconds).
	 * @param count		주기적으로 반복 수행될 횟수.
	 * @param executor	주기 수행에 사용될 쓰레드 풀 객체.
	 * @throws IllegalArgumentException	{@literal fact} 또는 <code>executor</code>가
	 * 					<code>null</code>인 경우. {@literal initDelay} 또는 {@literal delay}가
	 * 					음수인 경우.
	 */
	public PeriodicAsyncOperation(Supplier<AsyncOperation<?>> supplier, long initDelay, long delay,
									int count, CamusExecutor executor) {
		super(executor);

		if ( supplier == null ) {
			throw new IllegalArgumentException("target aop was null");
		}
		if ( initDelay < 0 ) {
			throw new IllegalArgumentException("initial delay should not be less than zero: delay="
												+ initDelay);
		}
		if ( delay < 0 ) {
			throw new IllegalArgumentException("delay should not be less than zero: delay=" + delay);
		}
		if ( executor == null ) {
			throw new IllegalStateException("executor should not be null");
		}

		m_supplier = supplier;
		m_initDelay = initDelay;
		m_delay = delay;
		m_remains = count;
		m_executor = executor;
		m_listener = new Listener();
	}

	@Override
	protected void startOperation() throws Throwable {
		synchronized ( m_mutex ) {
			if ( m_initDelay > 0 ) {
				m_future = m_executor.schedule(m_task, m_initDelay);
				m_mutex.notifyAll();
			}
			else {
				execute();
			}
		}
		
		notifyOperationStarted();
	}

	@Override
	protected void cancelOperation() {
		synchronized ( m_mutex ) {
			if ( m_initDelay > 0 ) {
				while ( m_future == null ) {
					try {
						m_mutex.wait();
					}
					catch ( InterruptedException e ) {
						Thread.currentThread().interrupt();
						throw new RuntimeException(e);
					}
				}
			}

			if ( m_future != null && m_future.cancel(false) ) {
				super.notifyOperationCancelled();
			}
			else {
				m_aop.cancel();
			}
		}
	}

	private void execute() {
		if ( _getState() > RUNNING ) {
			return;
		}

		synchronized ( m_mutex ) {
			try {
				m_aop = m_supplier.get();
				m_aop.addAsyncOperationListener(m_listener);
				m_aop.start();
			}
			catch ( final Throwable fault ) {
				Utilities.executeAsynchronously(m_executor, new Runnable() {
					public void run() {
						final PeriodicAsyncOperation _this = PeriodicAsyncOperation.this;

						if ( getState() == AsyncOperationState.NOT_STARTED ) {
							_this.notifyOperationStarted();

							try {
								waitForStarted();
							}
							catch ( InterruptedException e ) {
								Thread.currentThread().interrupt();
							}
						}
						_this.notifyOperationFailed(fault);
					}
				});
			}
		}
	}

	private final Runnable m_task = new Runnable() {
		public void run() {
			try {
				execute();
			}
			catch ( Throwable fault ) {
				PeriodicAsyncOperation.this.notifyOperationFailed(fault);
			}
		}
	};

	class Listener implements AsyncOperationListener<Void> {
		@Override public void onAsyncOperationStarted(AsyncOperation<Void> aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation<Void> aop, AsyncOperationState state) {
			final PeriodicAsyncOperation _this = PeriodicAsyncOperation.this;

			switch ( state ) {
				case COMPLETED:
					if ( _getState() == CANCELLING ) {
						_this.notifyOperationCancelled();
					}
					else {
						synchronized ( m_mutex ) {
							// 수행 횟수에 제한이 있는 경우 이를 체크한다.
							if ( --m_remains == 0 ) {
								Void result = null;
								try {
									result = aop.getResult();
								}
								catch ( Throwable ignored ) {
									s_logger.warn("fails to get PeriodicAsyncOperation result, cause="
													+ ExceptionUtils.unwrapThrowable(ignored));
								}
								_this.notifyOperationCompleted(result);
							}
							else {
								// 다음 execution을 시작하기까지 delay 한다.
								m_future = m_executor.schedule(m_task, m_delay);
								m_mutex.notifyAll();
							}
						}
					}
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
						s_logger.warn("fails to get PeriodicAsyncOperation fault cause, cause="
										+ ExceptionUtils.unwrapThrowable(ignored));
					}
					_this.notifyOperationFailed(cause);
					break;
				default:
					throw new RuntimeException();
			}
		}
	}
}