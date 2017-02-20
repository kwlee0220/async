package async;

/**
 * <code>StartableListener</code>는 {@link Startable}의 리스너 인터페이스 를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface StartableListener {
	/**
	 * 지정된 {@link Startable} 객체의 상태 이전 정보를 통보한다.
	 * 
	 * @param target	상태가 전이된 대상 Startable 객체.
	 * @param fromState	이전되기 이전 상태.
	 * @param toState	이전된 상태 
	 */
	public void onStateChanged(Startable target, StartableState fromState, StartableState toState);
}