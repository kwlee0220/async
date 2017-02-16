package async;




/**
 * {@literal OperationScheduler}는 비동기 연산 스케줄러 인터페이스를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface OperationScheduler {
	/**
	 * 비동기 연산 스케줄러의 스케줄링 정책을 반환한다.
	 * 
	 * @return	스케줄링 정책
	 */
	public String getSchedulingPolicyId();
	
	/**
	 * 연산 스케줄러를 통해 수행 중인 또는 수행 대기 중인 모든 비동기 연산을 취소시킨다.
	 */
	public void stopAll();
	
	/**
	 * 주어진 리스너를 본 연산 스케줄러에 등록시킨다.
	 * <p>
	 * 등록된 리스너는 임의의 비동기 연산 객체가 본 연산 스케쥴러에게 시작을 요청한 경우
	 * {@link OperationSchedulerListener#onAsyncOperationSubmitted(AsyncOperation)} 메소드가
	 * 호출되어 알린다.
	 * 
	 * @param listener	등록시킬 리스너 객체.
	 */
	public void addOperationSchedulerListener(OperationSchedulerListener listener);
	
	/**
	 * 주어진 리스너를 본 연산 스케줄러에서 등록 해제시킨다.
	 * <p>
	 * 만일 해당 리스너가 등록되어 있지 않은 상태면, 바로 반환한다.
	 * 
	 * @param listener	등록 해제시킬 리스너 객체.
	 */
	public void removeOperationSchedulerListener(OperationSchedulerListener listener);
}