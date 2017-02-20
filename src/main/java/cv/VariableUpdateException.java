package cv;

/**
 * <code>VariableUpdateException</code>는 변수 값 갱신 중 예외가 발생되는 경우 이를 정의한 예외 클래스이다.
 * 
 * @author Kang-Woo Lee
 */
public class VariableUpdateException extends RuntimeException {
	private static final long serialVersionUID = -5441593867278650065L;

	public VariableUpdateException(String details) {
		super(details);
	}
}
