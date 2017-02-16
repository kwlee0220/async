package async;


/**
 * <code>Startable</code> 서비스의 상태를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public enum StartableState {
	/** 작업 중지 상태 */
	STOPPED,
	/** 작업 수행 상태 */
	RUNNING,
	/** 오류 발생 상태 */
	FAILED,
}
