package async;

/**
 * <code>SchedulableAsyncOperation</code>의 인터페이스를 정의한다.
 * 
 * 스케줄러에 의해 스케줄링될 모든 비동기 연산의 제공자는 반드시 본 인터페이스를
 * 구현하여야 한다. 만일 스케줄러와 무관한 수행될 비동기 연산의 경우는
 * 본 인터페이스를 반드시 구현할 필요는 없다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface SchedulableAsyncOperation<T> extends AsyncOperation<T> {
	/**
	 * 비동기 연산 수행을 허락한다.
	 * 
	 * 본 메소드는 {@link OperationSchedulerProvider}의 스케줄 정책에 의해
	 * 본 비동기 연산의 수행이 가능해질 때 호출된다.
	 */
	public boolean permitToStart();
}
