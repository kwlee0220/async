package cv;

import async.AsyncOperation;
import async.OperationStoppedException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Variable<T> {
	public boolean isUpdatable();
	
	public Object getValue();
	
	/**
	 * Context 상태 값 정보를 얻는다.
	 * 
	 * @return	Context 상태 값
	 */
	public ValueInfo<T> getValueInfo();
	
	/**
	 * Context 상태 값이 바뀔 때까지 대기하여 바뀐 상태 값 정보를 얻는다.
	 * <p>
	 * 함수는 대상 상태 값이 바뀔 때까지 대기한다.
	 * 
	 * @param startTimestamp	얻어질 상태 값의 최소 modified 시각 (단위: milli-seconds).
	 * 							주어진 modified 시각보다 크거나 같은 modified 값을 갖는 상태를 반환한다.
	 * @return	변경된 Context 상태 값
	 * @throws OperationStoppedException	강제로 종료된 경우.
	 */
	public ValueInfo<T> waitValueUpdate(long startTimestamp) throws OperationStoppedException;
	
	/**
	 * Context 상태 값이 바뀔 때까지 지정된 대기시간 동안 대기한 후 바뀐 상태 값 정보를 얻는다.
	 * <p>
	 * 함수는 대상 상태 값이 바뀌거나 대기 시간이 경과되면 반환된다. 만일 대기시간 경과로 인해
	 * 반환되는 경우의 반환 값은 <code>null</code>이다.
	 * 
	 * @param startTimestamp	얻어질 상태 값의 최소 modified 시각 (단위: milli-seconds).
	 * 							주어진 modified 시각보다 같거나 큰 값의 상태를 반환한다.
	 * @param timeout		최대 대기 시간 (단위: milli-seconds).
	 * @return	변경된 Context 상태 값. 대기 시간이 경과된 경우는  <code>null</code>
	 * @throws OperationStoppedException	강제로 종료된 경우.
	 */
	public ValueInfo<T> timedWaitValueUpdate(long startTimestamp, long timeout) throws OperationStoppedException;
	
	/**
	 * 주어진 값으로 Context 상태 값을 변경시킨다.
	 * <p>
	 * 상태 값이 즉시 변경되는 경우는 변경 후에 <code>null</code>이 반환되고, 상태 값 변경이
	 * 일정 시간이 소요되는 경우는 해당 변경을 다룰 수 있는 비동기 연산 객체를 반환한다.
	 * 
	 * @return	비동기 연산 객체. 상태 값이 즉시 변경되는 경우는 <code>null</code>.
	 */
	public AsyncOperation<Void> setValue(T value) throws UnsupportedOperationException;
	
	/**
	 * Context 상태 값이 바뀔 때마다 호출된 리스너를 추가한다. 
	 * <p>
	 * 설정된 리스너는 context 상태 값이 바뀔 때마다
	 * {@link ContextVariableListener#valueUpdated(ContextValueInfo)가 호출된다.
	 * 
	 * @param listener	추가할 리스너 객체.
	 * @return	추가 여부. 이미 동일 리스너가 존재하는 경우 등의 이유로 추가에 실패한 경우는
	 * 					<code>false</code>
	 */
	public boolean addUpdateListener(VariableListener listener);
	
	/**
	 * Context 상태 값 리스너를 제거한다.
	 * <p>
	 * 리스너가 제거되면 context 상태 값이 변경되어도 해당 리스너는 호출되지 않는다.
	 * 
	 * @param listener	제거할 리스너 객체.
	 * @return	제거 여부. 동일 리스너가 등록되어 있지 않은 경우 등의 이유로 제거되지 않은
	 * 					<code>false</code>
	 */
	public boolean removeUpdateListener(VariableListener listener);
}
