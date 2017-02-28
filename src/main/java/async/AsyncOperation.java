package async;

/**
 * <code>AsyncOperation</code>는 비동기 연산의 표준 인터페이스를 정의한다.
 * <p>
 * 일반적으로 장기간 수행 작업은 효율성을 위해 비동기적으로 수행되는 경우가 많고, 이로 인해
 * 많은 경우 각 상황에 따라 add-hoc하게 인터페이스를 정의해 사용하게 된다.
 * 뿐만 아니라 비동기적인 함수 호출은 매우 복잡한 다중 쓰레드 프로그래밍을 유발하는데 이는
 * 대부분의 경우 시스템의 안정성을 해치게 된다.
 * 비동기 연산(<code>AsyncOperation</code>)은 수행 시간이 상대적으로 긴 작업을 수행하는
 * 함수를 호출 및 기타 활용하기 위한 표준 인터페이스를 제공한다. 또한 표준 인터페이스를 통해
 * 이를 따르는 비동기 함수를 쉽게 구현할 수 있는 공용 비동기 함수 구현 방법이 제공될 수 있도록 한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncOperation<T> {
	/**
	 * 본 비동기 연산을 시작시킨다.
	 * <p>
	 * 성공적으로 연산이 시작되면 등록된 리스너들의
	 * {@link AsyncOperationListener#onAsyncOperationStarted(AsyncOperation)}를 호출한다.
	 * 
	 * @throws IllegalStateException 비동기 연산이 이미 시작된 경우.
	 * @throws OperationSchedulerException 비동기 연산을 스케줄러에 등록시 오류가 발생한 경우.
	 */
	public void start() throws IllegalStateException, OperationSchedulerException;
	
	/**
	 * 수행 중인 비동기 연산을 취소 또는 강제 종료시킨다.
	 * <p>
	 * 메소드는 연산 수행 종료 요청 후 즉시 반환되기 때문에, 많은 경우 연산이 완전히 취소되기 전에
	 * 메소드가 반환되기도 한다.
	 * 만일 취소 요청 후, 완전히 취소될 때까지 대기할 필요가 있는 경우는 본 메소드 호출 후
	 * {@link #waitForFinished()}를 이용하여 대기한다.
	 * 만일 메소드 호출시 비동기 연산 실행이 이미 종료(완료, 취소, 오류)된 상태라면 바로 반환된다.
	 * 그러므로 본 메소드 호출 후, {@link #getState()} 호출 결과가
	 * {@link AsyncOperationState#COMPLETED} 또는 {@link AsyncOperationState#FAILED}가 될 수도 있다.
	 * 만일 주어진 연산이 '강제 종료'를 지원하지 않은 경우는 바로 반환되며 별도의 예외가 발생되지 않는다. 
	 */
	public void cancel();
	
	public default boolean isNotStarted() {
		return getState() == AsyncOperationState.NOT_STARTED;
	}
	
	/**
	 * 비동기 연산이 수행 중인가를 반환한다.
	 * 
	 * @return	비동기 연산의 상태가 {@link AsyncOperationState#RUNNING}인 경우는 <code>true</code>,
	 * 			그렇지 않은 경우는 <code>false</code>.
	 */
	public default boolean isRunning() {
		return getState() == AsyncOperationState.RUNNING;
	}
	
	/**
	 * 본 비동기 연산의 종료 여부를 반환한다.
	 * <p>
	 * 비동기 연산의 상태가 {@link AsyncOperationState#COMPLETED}, {@link AsyncOperationState#FAILED},
	 * {@link AsyncOperationState#CANCELLED}인 경우 종료로 간주된다.
	 * 
	 * @return	종료 여부.
	 */
	public default boolean isFinished() {
		switch ( getState() ) {
			case COMPLETED:
			case FAILED:
			case CANCELLED:
				return true;
			default:
				return false;
		}
	}
	
	/**
	 * 본 비동기 연산의 성공적 완료 여부를 반환한다.
	 * <p>
	 * 비동기 연산의 상태가 {@link AsyncOperationState#COMPLETED}인 경우 연산 완료로 간주된다.
	 * 
	 * @return	완료 여부.
	 */
	public default boolean isCompleted() {
		return getState() == AsyncOperationState.COMPLETED;
	}
	
	/**
	 * 본 비동기 연산 수행의 취소 여부를 반환한다.
	 * <p>
	 * 비동기 연산의 상태가 {@link AsyncOperationState#CANCELLED}인 경우 연산 취소로 간주된다.
	 * 
	 * @return	종료 여부.
	 */
	public default boolean isCancelled() {
		return getState() == AsyncOperationState.CANCELLED;
	}
	
	/**
	 * 본 비동기 연산 수행 실패 여부를 반환한다.
	 * <p>
	 * 비동기 연산의 상태가 {@link AsyncOperationState#FAILED}인 경우 연산 실패로 간주된다.
	 * 
	 * @return	실패 여부.
	 */
	public default boolean isFailed() {
		return getState() == AsyncOperationState.FAILED;
	}
	
	/**
	 * 본 비동기 연산의 상태를 반환한다.
	 * <p>
	 * 반환되는 상태 값은 다음의 값 중 하나이다.
	 * <ul>
	 * 	<li> {@link AsyncOperationState#NOT_STARTED}: 연산이 시작되지 않았거나, 시작 중인 경우.
	 * 	<li> {@link AsyncOperationState#RUNNING}: 연산이 수행 중인 경우.
	 * 	<li> {@link AsyncOperationState#COMPLETED}: 연산이 성공적으로 완료된 경우.
	 * 	<li> {@link AsyncOperationState#FAILED}: 연산이 예외 발생으로 실패되어 종료된 경우. 
	 * 	<li> {@link AsyncOperationState#CANCELLED}: 연산의 수행이 강제 종료(취소)된 경우. 
	 * </ul>
	 * 
	 * @return	비동기 연산의 상태 값.
	 */
	public AsyncOperationState getState();
	
	/**
	 * 본 비동기 연산 수행 중 발생된 예외를 반환한다.
	 * <p>
	 * 본 메소드는 오직 연산이 {@link AsyncOperationState#FAILED} 상태인 경우만
	 * 정상적으로 수행하며, 그렇지 않은 경우는 {@link IllegalStateException}
	 * 예외를 발생시킨다.
	 * 
	 * @return	발생된 예외 객체.
	 * @throws IllegalStateException	연산 상태가 {@link AsyncOperationState#FAILED}가 아닌 경우.
	 */
	public Throwable getFailureCause() throws IllegalStateException;

	/**
	 * 본 비동기 연산의 수행 결과를 반환한다.
	 * <p>
	 * 본 메소드는 오직 연산이 {@link AsyncOperationState#COMPLETED} 상태인 경우만
	 * 수행되며, 그렇지 않은 경우는 {@link IllegalStateException}
	 * 예외를 발생시킨다.
	 * 
	 * @return	실행 결과 객체.
	 * @throws IllegalStateException	연산 상태가 {@link AsyncOperationState#COMPLETED}가
	 * 									아닌 경우.
	 */
	public T getResult() throws IllegalStateException;
	
	/**
	 * 본 비동기 연산의 수행이 시작될 때까지 대기한다.
	 * <p>
	 * 만일 연산이 이미 시작된 경우는 바로 반환된다.
	 * 연산이 시작되기 전에 취소되는 경우도 반환된다.
	 * 
	 * @throws	InterruptedException	대기 중 호출 쓰레드가 interrupt된 경우.
	 */
	public void waitForStarted() throws InterruptedException;
	
	/**
	 * 본 비동기 연산이 수행 시작될 때까지 최대 주어진 시간 제한 동안 대기한다.
	 * <p>
	 * 주어진 시간 제한을 초과한 경우는 <code>false</code>를 반환한다.
	 * 만일 연산이 이미 시작된 경우는 바로 반환된다.
	 * 연산이 시작되기 전에 취소되는 경우도 반환된다.
	 * 
	 * @param timeout	최대 대기 기간 (millisecond 단위).
	 * @return 시간 제한 내에 성공적으로 시작된 경우는 true. 시간 제한으로 시작 전에 대기가
	 * 			끝나는 경우는 false.
	 * @throws InterruptedException	대기 중 호출 쓰레드가 interrupt된 경우.
	 * @throws IllegalArgumentException	대기 기간이 잘못된 경우.
	 */
	public boolean waitForStarted(long timeout) throws InterruptedException, IllegalArgumentException;

	/**
	 * 본 비동기 연산이 종료(완료, 취소, 또는 오류)될 때까지 대기한다.
	 * <p>
	 * 만일 연산이 이미 종료된 경우는 바로 반환된다.
	 * 
	 * @throws InterruptedException	대기 중 호출 쓰레드가 interrupt된 경우.
	 */
	public void waitForFinished() throws InterruptedException;

	/**
	 * 본 비동기 연산이 종료(완료, 취소, 또는 오류)될 때까지 최대 주어진 시간 제한 동안 대기한다.
	 * <p>
	 * 주어진 시간 제한을 초과한 경우는 <code>false</code>를 반환하고, 제한시간 내에 종료되는
	 * 경우는 <code>true</code>를 반환한다. 만일 연산이 이미 종료된 경우는 바로 반환된다.
	 * 
	 * @param timeout	최대 대기 기간 (millisecond 단위).
	 * @return 종료 여부. 성공적으로 종료된 경우는 true. 시간 제한으로 종료 전에 대기가 끝나는 경우는
	 * 			false.
	 * @throws InterruptedException	대기 중 호출 쓰레드가 interrupt된 경우.
	 * @throws IllegalArgumentException 대기 기간이 잘못된 경우.
	 */
	public boolean waitForFinished(long timeout) throws InterruptedException, IllegalArgumentException;
	
	/**
	 * 주어진 리스너를 본 비동기 연산에 등록시킨다.
	 * <p>
	 * 등록된 리스너는 본 비동기 연산이 시작되거나 종료될 때 다음과 같은 리스너 메소드가 호출된다.
	 * <ul>
	 * 	<li> {@link AsyncOperationListener#onAsyncOperationStarted(AsyncOperation) onAsyncOperationStarted}
	 * 		비동기 연산이 시작되는 경우.
	 * 	<li> {@link AsyncOperationListener#onAsyncOperationFinished(AsyncOperation, AsyncOperationState) onAsyncOperationFinished}
	 * 		비동기 연산이 종료되는 경우. 이 경우 해당 리스너의 callback 메소드 호출 후, 자동적으로 리스너를
	 * 		비동기 연산에서 등록 해제시킨다.
	 * </ul>
	 * 리스너 등록 당시 비동기 연산이 이미 시작된 경우는 바로
	 * {@link AsyncOperationListener#onAsyncOperationStarted(AsyncOperation) onAsyncOperationStarted}를
	 * 호출하고, 비동기 연산이 이미 종료된 상태면 바로
	 * {@link AsyncOperationListener#onAsyncOperationFinished(AsyncOperation, AsyncOperationState) onAsyncOperationFinished}를
	 * 호출한다.
	 * 
	 * @param listener	등록시킬 리스너 객체.
	 */
	public void addAsyncOperationListener(Object listener);
	
	/**
	 * 주어진 리스너를 본 비동기 연산에서 등록 해제시킨다.
	 * <p>
	 * 만일 해당 리스너가 등록되어 있지 않은 상태면, 바로 반환한다.
	 * 
	 * @param listener	등록 해제시킬 리스너 객체.
	 */
	public void removeAsyncOperationListener(Object listener);
}