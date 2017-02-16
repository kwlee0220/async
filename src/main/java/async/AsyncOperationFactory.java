package async;


/**
 * 
 * @author Kang-Woo Lee
 */
public interface AsyncOperationFactory<T> {
	/**
	 * 새로운 비동기 연산({@link AsyncOperation}) 인스턴스를 생성시킨다.
	 * <p>
	 * 생성된 비동기 연산 인스턴스는 시작되지 않은 상태이므로, 해당 연산을
	 * 시작시키기 위해서는 사용자가 명시적으로 {@link AsyncOperation#start()}를 호출하여야 한다.
	 * 
	 * @return	생성된 비동기 연산 인스턴스 객체.
	 */
	public AsyncOperation<T> newInstance() throws Exception;
}
