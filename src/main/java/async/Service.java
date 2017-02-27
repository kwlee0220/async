package async;


/**
 * <code>Service</code>는 서비스의 기본 인터페이스를 정의한다.
 * <p>
 * Service 인테페이스를 따르는 모든 객체는 다음과 같은 세가지 상태 값 중 하나의 값을 갖는다.
 * <ul>
 * 	<li>{@link ServiceState#STOPPED}: 작업이 수행 중지된 상태. 객체의 초기 상태이다.
 * 		{@link ServiceState#RUNNING} 상태에서 {@link #stop()} 메소드 호출을 통해 전이되거나,
 * 		{@link ServiceState#FAILED} 상태에서 고장 처리 과정을 거쳐 전이된다.
 * 	<li>{@link ServiceState#RUNNING}: 지정된 작업을 수행하고 있는 상태.
 * 		{@link ServiceState#STOPPED} 상태에서 {@link #start()} 메소드 호출이 성공된 경우
 * 		본 상태로 전이된다. 명시적인 {@link #stop()} 호출이나 오류 발생으로 인한 정지가 아닌 경우는
 * 		본 상태를 계속 유지하게 된다.
 * 	<li>{@link ServiceState#FAILED}: 작업 수행 중 오류 발생으로 중지된 상태.
 * 		발생된 오류 정보는 {@link #getFailureCause()}를 통해 얻을 수 있다.
 * 		본 상태로 전이된 경우 고장 처리 모듈이 등록된 경우는 자동적으로 해당 모듈 수행 후
 * 		{@link ServiceState#STOPPED} 상태로 전이된다.
 * </ul>
 * 
 * Service 객체의 상태 변화는 {@link ServiceStateChangeListener}를 통해 얻을 수 있다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Service {
	/**
	 * 작업을 시작시킨다.
	 * <p>
	 * 메소드는 서비스의 상태가 {@link ServiceState#RUNNING}으로 전이된 상태에서 반환된다.
	 * 본 서비스의 상태가 {@link ServiceState#STOPPED} 또는 {@link ServiceState#FAILED}가 아닌 경우는
	 * {@link IllegalStateException} 예외가 발생시킨다.
	 * 
	 * @throws IllegalStateException 호출 당시 서비스의 상태가  {@link ServiceState#STOPPED} 또는
	 * 						{@link ServiceState#FAILED}가 아닌 경우.
	 * @throws Exception	서비스 시작 과정 중에 다른 예외가 발생된 경우.
	 */
	public void start() throws Exception;
	
	/**
	 * 작업을 중지시킨다.
	 * <p>
	 * 동작 상태({@link ServiceState#RUNNING})가 아닌 경우는 호출은 무시된다.
	 */
	public void stop();

	/**
	 * 작동 상태를 반환한다.
	 * 
	 * @return	작동 상태.
	 */
	public ServiceState getState();
	
	public default boolean isRunning() {
		return getState() == ServiceState.RUNNING;
	}
	
	public default boolean isStopped() {
		return getState() == ServiceState.STOPPED;
	}
	
	public default boolean isFailed() {
		return getState() == ServiceState.FAILED;
	}
	
	public default boolean isFinished() {
		return getState() == ServiceState.STOPPED || getState() == ServiceState.FAILED;
	}
	
	/**
	 * 고장 상태 ({@link ServiceState#FAILED})인 경우
	 * 고장 원인 예외 객체를 반환한다.
	 * <p>
	 * 고장 상태가 아닌 경우 호출되면 <code>null</code>을 반환한다.
	 * 
	 *  @return	고장 발생 원인 예외 객체.
	 */
	public Throwable getFailureCause();
	
	/**
	 * 작업이 중지될 때까지 대기한다.
	 * <p>
	 * {@link #getState()}의 값이 {@link ServiceState#STOPPED} 또는 
	 * {@link ServiceState#FAILED}가 될 때까지 대기한다.
	 * @throws InterruptedException 대기하던 쓰레드가 강제 종료된 경우.
	 */
	public void waitForFinished() throws InterruptedException;
	
	/**
	 * 작업이 중지될 때까지 주어진 제한시간 동안 대기한다.
	 * <p>
	 * {@link #getState()}의 값이 {@link ServiceState#STOPPED} 또는 
	 * {@link ServiceState#FAILED}가 될 때까지 대기한다.
	 * 만일 주어지 제한 시간 이내에 중지되는 경우는  <code>true</code> 값을 반환하고
	 * 그렇지 않고 제한 시간을 초과하는 경우는 <code>false</code>를 반환한다.
	 * 
	 * @param timeoutMillis	대기 제한 시간 (단위: milli-second)
	 * @return	제한시간 초과 여부.
	 * @throws InterruptedException 대기하던 쓰레드가 강제 종료된 경우.
	 */
	public boolean waitForFinished(long timeoutMillis) throws InterruptedException;
	
	/**
	 * {@link Service} 상태의 변화를 통보 받을 리스너를 추가한다.
	 * 
	 * @param listener	추가할 {@link Service}의 리스너
	 */
	public void addStateChangeListener(Object listener);
	
	/**
	 * 주어진 {@link Service}의 상태 변화 리스너를 제거한다.
	 * 
	 * @param listener	제거할 {@link Service}의 리스너
	 */
	public void removeStateChangeListener(Object listener);
}
