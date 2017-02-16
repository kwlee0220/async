package async;

/**
 * <code>OperationStoppedException</code>는 서비스 수행이 취소되는 경우 발생되는
 * 예외 클래스를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class OperationStoppedException extends RuntimeException {
	private static final long serialVersionUID = 4771682174877108586L;

	public OperationStoppedException() {
		super("");
	}

	public OperationStoppedException(String msg) {
		super(msg);
	}
}
