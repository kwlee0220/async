package async.optor;

import java.util.concurrent.Future;

import async.OperationSchedulerProvider;
import async.support.AbstractAsyncOperation;
import utils.thread.CamusExecutor;


/**
 * <code>IdleAsyncOperation</code>는 주어진 시간 동안 수면하는 비동기 연산을 정의한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class IdleAsyncOperation extends AbstractAsyncOperation<Void> {
	private final CamusExecutor m_executor;
	private final long m_sleepMillis;
	private final String m_toString;
	private volatile Future<?> m_future;

	/**
	 * 수면 비동기 연산 객체를 생성한다.
	 * 
	 * @param scheduler	비동기 연산이 사용할 연산 스케쥴러.
	 * @param sleepMillis	수면 기간 (단위: milli-second)
	 * @param executor	비동기 연산이 사용할 쓰레드 풀 객체.
	 */
	public IdleAsyncOperation(OperationSchedulerProvider scheduler, long sleepMillis,
								CamusExecutor executor) {
		super(scheduler);
		
		if ( sleepMillis < 0 ) {
			throw new IllegalArgumentException("invalid sleepMillis=" + sleepMillis);
		}

		m_sleepMillis = sleepMillis;
		m_toString = null;
		m_executor = executor;
	}
	

	public IdleAsyncOperation(long sleepMillis, CamusExecutor executor) {
		super(executor);
		
		if ( sleepMillis < 0 ) {
			throw new IllegalArgumentException("invalid sleepMillis=" + sleepMillis);
		}

		m_sleepMillis = sleepMillis;
		m_toString = null;
		m_executor = executor;
	}

	/**
	 * 수면 비동기 연산 객체를 생성한다.
	 * 
	 * @param scheduler	비동기 연산이 사용할 연산 스케쥴러.
	 * @param sleepMillis	수면 기간 (단위: milli-second)
	 * @param toString	비동기 연산의 {@link #toString()} 호출시 사용할 출력 문자열
	 * @param executor	비동기 연산이 사용할 쓰레드 풀 객체.
	 */
	public IdleAsyncOperation(OperationSchedulerProvider scheduler, long sleepMillis,
								String toString, CamusExecutor executor) {
		super(scheduler);
		
		if ( sleepMillis < 0 ) {
			throw new IllegalArgumentException("invalid sleepMillis=" + sleepMillis);
		}
		if ( toString == null ) {
			throw new IllegalArgumentException("'toString' is null");
		}

		m_sleepMillis = sleepMillis;
		m_toString = toString;
		m_executor = executor;
	}
	
	/**
	 * 수면 대기 기간을 반환한다.
	 * 
	 * @return	수면 대기 기간 (단위: milli-second)
	 */
	public final long getSleepMillis() {
		return m_sleepMillis;
	}
	
	@Override
	public String toString() {
		return (m_toString != null) ? m_toString
									: "idle[" + m_sleepMillis + "ms]";
	}

	@Override
	protected void startOperation() throws Throwable {
		m_future = m_executor.schedule(m_task, m_sleepMillis);
		notifyOperationStarted();
	}

	@Override
	protected synchronized void cancelOperation() {
		if ( m_future.cancel(false) ) {
			notifyOperationCancelled();
		}
	}

	private final Runnable m_task = new Runnable() {
		@Override
		public void run() {
			IdleAsyncOperation.this.notifyOperationCompleted(null);
		}
	};
}
