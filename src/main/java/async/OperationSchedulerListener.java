package async;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface OperationSchedulerListener {
	/**
	 * 비동기 연산이 연산 스케쥴러에게 시작을 요청했음을 통보한다.
	 * 
	 * @param aop	연산 스케쥴러에게 연산 시작을 요청한 비동기 연산 객체.
	 */
	public void onAsyncOperationSubmitted(AsyncOperation aop);
}
