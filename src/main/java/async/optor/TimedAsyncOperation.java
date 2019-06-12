package async.optor;


import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.support.AbstractAsyncOperation;
import net.jcip.annotations.GuardedBy;
import utils.exception.Throwables;
import utils.thread.CamusExecutor;
import utils.thread.ExecutorAware;


/**
 * <code>TimedAsyncOperation</code>은 주어진 비동기 연산을 주어진 시간동안만 수행시키는
 * 비동기 연산 클래스를 정의한다.
 * <p>
 * 주어진 대상 비동기 연산(target_aop)이 정해진 제한 시간(timout) 내에 수행 여부에 따라 본 연산의 수행 결과는
 * 다음과 같이 정의된다.
 * <dl>
 * 	<dt> 제한된 시간 내에 종료된 경우</dt>
 * 	<dd> target_aop의 수행 종료 상태에 따라,
 * 		<dl>
 * 		<dt> 성공적으로 완료된 경우:
 * 			<dd> 본 연산은 성공적으로 완료된 것으로 설정되고, target_aop의 결과 값을 결과 값으로 한다.
 * 		<dt> 실패한 경우:
 * 			<dd> 본 연산은 실패한 것으로 설정되고, target_aop에서 발생된 예외를 실패 원인 예외로 설정한다.
 * 		<dt> 취소된 경우:
 * 			<dd> 본 연산은 취소된 것으로 설정된다.
 * 		</dl>
 *	<dt> 제한된 시간 내에 종료되지 못한 경우
 *	<dd> 시간 초과 처리 비동기 연산 (timout_aop)의 유무에 따라,
 *		<dl>
 *		<dt> timout_aop이 설정되지 않은 경우.
 *			<dd> 본 연산은 취소된 것으로 간주된다.
 *		<dt> timout_aop이 설정된 경우.
 *			<dd> timeout_aop를 시작시키고, 그 수행이 종료될 때까지 본 연산은 수행이 지속된다.
 *				이 시기에 본 연산을 강제 종료시키면 timeout_aop가 종료된다.
 *				timeout_aop가 성공적으로 완료되면 전체 연산을 성공적으로 완료된 것으로 간주되고,
 *				timeout_aop의 결과 값이 전체 연산의 결과 값으로 설정된다.
 *				만일 timeout_aop의 수행 결과가 중단되거나 실패하는 경우는 전체 연산은 중단된 것으로
 *				간주된다.
 *		</dl>
 * </dl>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TimedAsyncOperation<T> extends AbstractAsyncOperation<T> implements AsyncOperation<T> {
	static final Logger s_logger = LoggerFactory.getLogger("AOP.TIMED");

	private static final int STATE_RUNNING = 0;				// 비동기 연산이 수행중인 상태.
	private static final int STATE_TIMEOUT_STOP_BEGIN = 1;	// 시간초과가 발생하여 이를 처리하기 중인 상태.
	private static final int STATE_TIMEOUT_STOP_END = 2;	// 시간초과가 발생하여 이에 대한 처리가 완료된 상태.
	private static final int STATE_TIMEDOUT = 3;
	private static final int STATE_STOPPED = 4;
	private static final int STATE_COMPLETED = 5;
	private static final int STATE_FAILED = 6;

	private final AsyncOperation<T> m_aop;
	private final long m_timeout;
	private volatile Supplier<AsyncOperation<T>> m_timeoutAopSupplier;
	private volatile CamusExecutor m_cexecutor;

	private final Object m_imutex = new Object();
	@GuardedBy("m_imutex") private int m_istate;
	@GuardedBy("m_imutex") private Future<?> m_future;
	@GuardedBy("m_imutex") private AsyncOperation<T> m_timeoutAop = null;
	
	public static <T> TimedAsyncOperation<T> newInstance(AsyncOperation<T> aop, Integer timeout) {
		return new TimedAsyncOperation<T>(aop, timeout);
	}

	/**
	 * 시간 제한 수행 비동기 연산 객체를 생성한다.
	 *
	 * @param aop	제한 시간 동안 수행될 대상 비동기 연산 객체.
	 * @param timeout	제한 시간 (단위: millisecond)
	 * @throws IllegalArgumentException	{@literal aop}, <code>executor</code>가 <code>null</code>인 경우거나
	 * 								{@literal timeout}이 음수이거나 0인 경우.
	 */
	public TimedAsyncOperation(AsyncOperation<T> aop, long timeout) {
		if ( aop == null ) {
			throw new IllegalArgumentException("target aop was null");
		}
		if ( timeout <= 0 ) {
			throw new IllegalArgumentException("timeout should be greater than zero: timeout="
												+ timeout);
		}

		m_aop = aop;
		m_timeout = timeout;
	}
	
	public final AsyncOperation<T> getTargetOperation() {
		return m_aop;
	}

	public final long getTimeout() {
		return m_timeout;
	}
	
	public boolean isTimedout() {
		synchronized ( m_imutex ) {
			return m_istate == STATE_TIMEOUT_STOP_END || m_istate == STATE_TIMEDOUT;
		}
	}
	
	/**
	 * 시간 초과 처리 비동기 연산 생성자를 설정한다.
	 * <p>
	 * 설정된 생성자는 시간 제한 비동기 연산의 수행이 시간 제한으로 중단된 경우 호출될 비동기 연산을
	 * 생성한다.
	 * 
	 * @param timeoutAopFact	시간 초과 처리 비동기 연산 생성자.
	 * 					시간 제한이 발생되었을 때 수행될 비동기 연산을 생성하는 생성자.
	 */
	public void setTimeoutOperationSupplier(Supplier<AsyncOperation<T>> supplier) {
		m_timeoutAopSupplier = supplier;
	}

	/**
	 * 내부적으로 사용할 {@link CamusExecutor}를 설정한다.
	 * 
	 * @param executor	시간 제한 수행에 사용될 쓰레드 풀 객체.
	 */
	public void setCamusExecutor(CamusExecutor executor) {
		super.setExecutor(executor);
		m_cexecutor = executor;
	}

	public String toString() {
		return "Timed[aop=" + m_aop + ", timeout=" + m_timeout + "]";
	}

	@Override
	protected void startOperation() throws Throwable {
		synchronized ( m_imutex ) {
			if ( m_cexecutor == null ) {
				if ( m_aop instanceof ExecutorAware ) {
					Executor exector = ((ExecutorAware)m_aop).getExecutor();
					if ( exector instanceof CamusExecutor ) {
						m_cexecutor = (CamusExecutor)exector;
					}
				}
			}
			if ( m_cexecutor == null ) {
				throw new RuntimeException("CamusExecutor has not been set: class="
													+ getClass().getName());
			}
			
			m_aop.addStateChangeListener(new TargetAopListener());
			m_aop.start();
			m_future = m_cexecutor.schedule(new TimeoutTask(), m_timeout);

			m_istate = STATE_RUNNING;
		}
	}

	@Override
	protected void stopOperation() {
		synchronized ( m_imutex ) {
			// 시간초과 처리 작업 중인 경우는 처리 작업이 완료될 때까지 대기한다.
			while ( m_istate == STATE_TIMEOUT_STOP_BEGIN ) {
				try {
					m_imutex.wait();
				}
				catch ( InterruptedException e ) { }
			}

			if ( m_istate == STATE_RUNNING ) {
				// 대상 작업이 수행 중인 경우는 설정된 timeout future를 취소하고
				// 대상 작업을 취소시킨다. 취소 작업 역시 일정 시간이 소요될 수 있으므로
				// mutex를 놓기 전에 상태를 'STATE_CANCELLING'로 전이시켜 놓는다.
				//
				if ( s_logger.isInfoEnabled() ) {
					s_logger.debug("stopping: " + m_aop);
				}

				m_future.cancel(false);
				m_aop.cancel();
				m_istate = STATE_STOPPED;
			}
			else if ( m_istate == STATE_TIMEOUT_STOP_END ) {
				// 시간 초과 작업이 발생하여 시간초과 처리 작업이 수행 중인 경우는 이 작업을
				// 취소시킨다.
				if ( s_logger.isInfoEnabled() ) {
					s_logger.debug("cancelling: timeout-action=" + m_aop);
				}

				if ( m_timeoutAop != null ) {
					m_timeoutAop.cancel();
				}
			}
		}
	}
	
	private final void setExecutorIfNeeded(AsyncOperation<?> aop) {
		if ( m_cexecutor != null && aop instanceof ExecutorAware ) {
			ExecutorAware prvdr = (ExecutorAware)aop;
			if ( prvdr.getExecutor() == null ) {
				prvdr.setExecutor(m_cexecutor);
			}
		}
	}

	class TargetAopListener implements AsyncOperationListener<T> {
		@Override public void onAsyncOperationStarted(AsyncOperation<T> aop) {
			TimedAsyncOperation.this.notifyOperationStarted();
		}

		@Override
		public void onAsyncOperationFinished(AsyncOperation<T> aop, AsyncOperationState state) {
			final TimedAsyncOperation<T> _this = TimedAsyncOperation.this;

			switch ( state ) {
				case COMPLETED:
					// 대상 작업이 완료된 경우는 본 연산도 완료된 것으로 설정한다.
					_this.notifyOperationCompleted(null);
					
					// 대상 작업 수행이 timeout이 발생하자마자 종료된 경우에는
					// m_istate의 상태가 STATE_TIMEOUT_STOP_BEGIN일 수도 있고,
					// 이때는 timeout이 발생하지 않은 것으로 간주한다.
					synchronized ( m_imutex ) {
						if ( m_istate == STATE_TIMEOUT_STOP_BEGIN ) {
							m_istate = STATE_COMPLETED;
							m_imutex.notifyAll();
						}
					}
					break;
				case FAILED:
					Throwable cause = null;
					try {
						cause = aop.getFailureCause();
					}
					catch ( Throwable ignored ) {
						s_logger.warn("fails to get " + getClass().getSimpleName() + " fault cause, cause="
										+ Throwables.unwrapThrowable(ignored));
					}
					_this.notifyOperationFailed(cause);
					
					// 대상 작업 수행이 timeout이 발생하자마자 종료된 경우에는
					// m_istate의 상태가 STATE_ENTERING_TIMEOUT일 수도 있고,
					// 이때는 timeout이 발생하지 않은 것으로 간주한다.
					synchronized ( m_imutex ) {
						if ( m_istate == STATE_TIMEOUT_STOP_BEGIN ) {
							m_istate = STATE_FAILED;
							m_imutex.notifyAll();
						}
					}
					break;
				case CANCELLED:
					synchronized ( m_imutex ) {
						if ( m_istate == STATE_TIMEOUT_STOP_BEGIN ) {
							// timeout 때문에 cancel된 경우 처리.
							//
							if ( m_timeoutAopSupplier != null ) {
								try {
									m_timeoutAop = m_timeoutAopSupplier.get();
								}
								catch ( Exception e ) {
									Throwable fault = Throwables.unwrapThrowable(e);
									s_logger.warn("failed: creation of timeout execution: cause=" + fault);
									
									_this.notifyOperationFailed(fault);
									return;
								}
								setExecutorIfNeeded(m_timeoutAop);
								m_timeoutAop.addStateChangeListener(new TimeoutAopListener());
								
								m_istate = STATE_TIMEOUT_STOP_END;
								m_timeoutAop.start();
								if ( s_logger.isInfoEnabled() ) {
									s_logger.info("started: timeout execution: " + m_timeoutAop);
								}
							}
							else {
								_this.notifyOperationCompleted(null);
								m_istate = STATE_TIMEDOUT;
							}
						}
						else {
							// 다른 이유로 cancel된 경우.
							_this.notifyOperationCancelled();
							m_istate = STATE_STOPPED;
						}

						m_imutex.notifyAll();
					}
					break;
				default:
					throw new RuntimeException();
			}
		}
	};

	class TimeoutTask implements Runnable {
		public void run() {
			synchronized ( m_imutex ) {
				if ( m_istate != STATE_RUNNING ) {
					return;
				}

				m_istate = STATE_TIMEOUT_STOP_BEGIN;
				m_imutex.notifyAll();

				if ( s_logger.isDebugEnabled() ) {
					s_logger.debug("expired: target operation: " + m_aop + ", timeout="
									+ m_timeout + "ms");
				}

				m_aop.cancel();
			}
		}
	}

	class TimeoutAopListener implements AsyncOperationListener<T> {
		@Override public void onAsyncOperationStarted(AsyncOperation<T> aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation<T> aop, AsyncOperationState state) {
			final TimedAsyncOperation<T> _this = TimedAsyncOperation.this;

			switch ( aop.getState() ) {
				case COMPLETED:
					try {
						_this.notifyOperationCompleted(null);
					}
					catch ( Exception neverHappens ) { }
					break;
				case FAILED:
				case CANCELLED:
					_this.notifyOperationCancelled();
					break;
				default:
					throw new RuntimeException();
			}
		}
	}
}