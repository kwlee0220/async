package async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public enum AsyncOperationState {
	/** 비동기 연산이 아직 시작되지 않은 상태. */
	NOT_STARTED,
	/** 비동기 연산이 수행 중인 상태. */
	RUNNING,
	/** 비동기 연산의 수행이 성공적으로 완료된 상태. */
	COMPLETED,
	/** 비동기 연산이 오류 발생으로 중지된 상태. */
	FAILED,
	/** 비동기 연산 수행 중 중지된 상태. */
	CANCELLED;
}
