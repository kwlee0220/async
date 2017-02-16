package async;

import java.util.concurrent.Executor;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface OperationSchedulerProvider extends OperationScheduler {
	/**
	 * 주어진 스케쥴 비동기 연산을 스케줄러에 포함시킨다.
	 * <p>
	 * 스케줄에 포함된 비동기 연산은 스케줄 정책에 따라 연산 시작이 가능해지면
	 * {@link SchedulableAsyncOperation#permitToStart()}를 호출하여 해당
	 * 비동기 연산 제공자에게 알린다.
	 * 
	 * @param schedule	스케줄할 비동기 연산 객체.
	 * @throws OperationSchedulerException 비동기 연산 등록에 실패한 경우.
	 */
	public void submit(SchedulableAsyncOperation<?> schedule) throws OperationSchedulerException;
	
	/**
	 * 본 연산 스케줄러가 사용하는 쓰레드 풀을 얻는다.
	 * 
	 * @return 스레드 풀 객체.
	 */
	public Executor getExecutor();
}
