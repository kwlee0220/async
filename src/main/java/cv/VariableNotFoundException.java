package cv;

/**
 * <code>VariableNotFoundException</code>는 변수가 존재하지 않는 경우 발생되는 예외 클래스를 정의한다.
 * 
 * @author Kang-Woo Lee
 */
public class VariableNotFoundException extends Exception {
	private static final long serialVersionUID = 9181066454935519703L;

	public VariableNotFoundException(String details) {
		super(details);
	}
}
