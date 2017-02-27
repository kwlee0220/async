package async.optor;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;

import async.AsyncOperation;
import async.AsyncOperationState;
import async.AsyncOperationStateChangeEvent;
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
public class BackgroundedAsyncOperation<T> extends AbstractAsyncOperation<T>
											implements AsyncOperation<T> {
	private static final Logger s_logger = LoggerFactory.getLogger("AOP.BACKGROUND");
	
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
		
		Preconditions.checkNotNull(fgAop, "foreground aop was null");
		Preconditions.checkNotNull(bgAop, "background aop was null");

		m_fgAop = fgAop;
		m_fgAop.addAsyncOperationListener(this);
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
			s_logger.warn("failed to start background aop={}", m_bgAop);
		}
		
		m_fgAop.start();
		
		// 시작 보고는 'm_fgAop'의 start listener에서 수행한다.
	}

	@Override
	protected void cancelOperation() {
		m_fgAop.cancel();
		m_bgAop.cancel();
	}
	
	@Subscribe
	public void onForegroundAopStateChanged(AsyncOperationStateChangeEvent<T> event) {
		Preconditions.checkNotNull(event);
		Preconditions.checkArgument(event.getAsyncOperation() == m_fgAop);
		
		final AsyncOperationState toState = event.getToState();
		
		if ( toState == AsyncOperationState.RUNNING ) {
			notifyOperationStarted();
		}
		else if ( toState == AsyncOperationState.COMPLETED ) {
			// foreground가 종료되면 무조건 background aop를 종료시킨다.
			m_bgAop.cancel();
			
			T result = null;
			try {
				result = m_fgAop.getResult();
			}
			catch ( Throwable ignored ) {
				s_logger.warn("fails to get BackgroundedAsyncOperation result, cause={}",
								ExceptionUtils.unwrapThrowable(ignored));
			}
			notifyOperationCompleted(result);
		}
		else if ( toState == AsyncOperationState.FAILED ) {
			// foreground가 종료되면 무조건 background aop를 종료시킨다.
			m_bgAop.cancel();
			
			Throwable cause = null;
			try {
				cause = m_fgAop.getFailureCause();
			}
			catch ( Throwable ignored ) {
				s_logger.warn("fails to get BackgroundedAsyncOperation fault cause, cause={}",
								ExceptionUtils.unwrapThrowable(ignored));
			}
			notifyOperationFailed(cause);
		}
		else if ( toState == AsyncOperationState.CANCELLED ) {
			// foreground가 종료되면 무조건 background aop를 종료시킨다.
			m_bgAop.cancel();
			
			notifyOperationCancelled();
		}
		else {
			throw new AssertionError();
		}
	}
}