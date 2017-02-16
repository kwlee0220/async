package async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncOperationListener<T> {
	/**
	 * 대상 비동기 연산이 시작됨을 통보한다.
	 * 
	 * @param aop	시작된 비동기 연산 객체.
	 */
	public void onAsyncOperationStarted(AsyncOperation<T> aop);
	
	/**
	 * 대상 비동기 연산이 종료되었음을 알린다.
	 * 
	 * @param aop	종료된 비동기 연산 객체.
	 * @param state	비동기 연산 상태.
	 */
	public void onAsyncOperationFinished(AsyncOperation<T> aop, AsyncOperationState state);
}
