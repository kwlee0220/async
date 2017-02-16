package async.optor;

import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.support.AbstractAsyncOperation;
import utils.ExceptionUtils;



/**
 * {@literal BackgroundedAsyncOperation}은 주어진 전방(foreground) 연산 및 후방(background) 연산을
 * 병렬로 수행시키는 비동기 연산 클래스를 정의한다.
 * <p>
 * 본 비동기 연산이 시작되면 지정된 전방 및 후방 비동기 연산이 동시에 시작되어, 전방 연산이 종료될 때까지
 * 수행된다. 만일 전방 연산 종료될 때 후방 연산이 수행 중인 경우면 후방 연산은 강제로 취소되며
 * 후방 연산이 전방 연산보다 먼저 종료되는 경우는 전방 연산만 계속 수행된다.
 * 본 연산이 시작될 때 전방 연산이 시작이 통보될 때 등록된 리스너들에게 시작 통보를 내리고,
 * 본 연산을 취소시키면 수행 중인 전/후방 연산을 모두 취소 요청을 하고, 전방 연산이 취소 완료될 때
 * 본 연산의 리스너들에게 취소 통보를 전달한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class BackgroundedAsyncOperation<T> extends AbstractAsyncOperation<T> implements AsyncOperation<T> {
	private static final Logger s_logger = Logger.getLogger("AOP.BACKGROUND");
	
	private final AsyncOperation<T> m_fgAop;
	private final AsyncOperation<?> m_bgAop;
	
	/**
	 * 전후방 수행 비동기 연산 객체를 생성한다.
	 * 
	 * @param fgAop		수행시킬 전방 비동기 연산
	 * @param bgAop		수행시킬 후방 비동기 연산
	 * @param executor	본 연산 수행에 사용될 쓰레드 풀 객체.
	 * @throws InvalidArgumentException	{@literal fgAop}, {@literal bgAop}가 <code>null</code>인
	 * 					경우.
	 */
	public BackgroundedAsyncOperation(AsyncOperation<T> fgAop, AsyncOperation<?> bgAop,
										Executor executor) {
		super(executor);
		
		if ( fgAop == null ) {
			throw new IllegalArgumentException("foreground aop was null");
		}
		if ( bgAop == null ) {
			throw new IllegalArgumentException("background aop was null");
		}

		m_fgAop = fgAop;
		m_fgAop.addAsyncOperationListener(new ForegroundListener());
		m_bgAop = bgAop;
	}
	
	public final AsyncOperation<T> getForegroundAsyncOperation() {
		return m_fgAop;
	}
	
	public final AsyncOperation<?> getBackgroundAsyncOperation() {
		return m_bgAop;
	}

	@Override
	protected void startOperation() throws Throwable {
		try {
			m_bgAop.start();
		}
		catch ( Exception ignored ) {
			s_logger.warn("failed to start background aop=" + m_bgAop);
		}
		
		m_fgAop.start();
		
		// 시작 보고는 'm_fgAop'의 start listener에서 수행한다.
	}

	@Override
	protected void cancelOperation() {
		m_fgAop.cancel();
		m_bgAop.cancel();
	}
	
	class ForegroundListener implements AsyncOperationListener<T> {
		@Override public void onAsyncOperationStarted(AsyncOperation<T> aop) {
			BackgroundedAsyncOperation.this.notifyOperationStarted();
		}

		@Override
		public void onAsyncOperationFinished(AsyncOperation<T> aop, AsyncOperationState state) {
			final BackgroundedAsyncOperation<T> _this = BackgroundedAsyncOperation.this;
			
			// foreground가 종료되면 무조건 background aop를 종료시킨다.
			m_bgAop.cancel();
			
			switch ( state ) {
				case COMPLETED:
					T result = null;
					try {
						result = aop.getResult();
					}
					catch ( Throwable ignored ) {
						s_logger.warn("fails to get BackgroundedAsyncOperation result, cause="
										+ ExceptionUtils.unwrapThrowable(ignored));
					}
					_this.notifyOperationCompleted(result);
					break;
				case FAILED:
					Throwable cause = null;
					try {
						cause = aop.getFailureCause();
					}
					catch ( Throwable ignored ) {
						s_logger.warn("fails to get BackgroundedAsyncOperation fault cause, cause="
										+ ExceptionUtils.unwrapThrowable(ignored));
					}
					_this.notifyOperationFailed(cause);
					break;
				case CANCELLED:
					_this.notifyOperationCancelled();
					break;
				default:
					throw new RuntimeException();
			}
		}
	}
}