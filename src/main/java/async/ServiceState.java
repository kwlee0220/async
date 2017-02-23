package async;


/**
 * <code>Service</code>의 상태를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public enum ServiceState {
	/** 작업 중지 상태 */
	STOPPED,
	/** 작업 수행 상태 */
	RUNNING,
	/** 오류 발생되어 수행이 정지된 상태 */
	FAILED,
}
