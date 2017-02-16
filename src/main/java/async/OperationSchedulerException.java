package async;

/**
 * <code>OperationSchedulerException</code>는 비동기 연산 스케줄와 관련된
 * 예외 클래스를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class OperationSchedulerException extends RuntimeException {
	private static final long serialVersionUID = 720544468076299070L;

	public OperationSchedulerException() {
		super("");
	}

	public OperationSchedulerException(String msg) {
		super(msg);
	}
}
