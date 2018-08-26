package async;


import java.util.Objects;

import event.Event;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncOperationStateChangeEvent<T> implements Event {
	public static final String PROP_AOP = "asyncOperation";
	public static final String PROP_TO_STATE = "toState";
	public static final String PROP_TAG = "tag";
	
	private final AsyncOperation<T> m_aop;
	private final AsyncOperationState m_toState;
	private final String m_tag;
	
	/**
	 * 서비스 상태 변경 이벤트 객체를 생성한다.
	 * 
	 * @param aop	상태가 전이된 대상 Service 객체.
	 * @param fromState	이전되기 이전 상태.
	 * @param toState	이전된 상태 
	 */
	public AsyncOperationStateChangeEvent(AsyncOperation<T> aop, AsyncOperationState toState) {
		Objects.requireNonNull(aop, "target AsyncOperation is null");
		Objects.requireNonNull(toState, "to state is null");
		
		m_aop = aop;
		m_toState = toState;
		m_tag = null;
	}
	
	/**
	 * 서비스 상태 변경 이벤트 객체를 생성한다.
	 * 
	 * @param aop	상태가 전이된 대상 Service 객체.
	 * @param fromState	이전되기 이전 상태.
	 * @param toState	이전된 상태 
	 * @param tag		태그 정보
	 */
	public AsyncOperationStateChangeEvent(AsyncOperation<T> aop, AsyncOperationState toState,
										String tag) {
		Objects.requireNonNull(aop, "target AsyncOperation is null");
		Objects.requireNonNull(toState, "to state is null");
		Objects.requireNonNull(tag, "tag is null");
		
		m_aop = aop;
		m_toState = toState;
		m_tag = tag;
	}

	@Override
	public String[] getEventTypeIds() {
		return new String[]{ AsyncOperationStateChangeEvent.class.getName() };
	}

	@Override
	public String[] getPropertyNames() {
		return new String[]{ PROP_AOP, PROP_TO_STATE, PROP_TAG };
	}

	@Override
	public Object getProperty(String name) {
		switch ( name ) {
			case PROP_AOP:
				return m_aop;
			case PROP_TO_STATE:
				return m_toState;
			case PROP_TAG:
				return m_tag;
			default:
				throw new IllegalArgumentException("property.name=" + name);
		}
	}
	
	public final AsyncOperation<T> getAsyncOperation() {
		return m_aop;
	}
	
	public final AsyncOperationState getToState() {
		return m_toState;
	}
	
	public final String getTag() {
		return m_tag;
	}
	
	@Override
	public String toString() {
		return String.format("aop: %s, to_state=%s", m_aop, m_toState);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != AsyncOperationStateChangeEvent.class ) {
			return false;
		}
		
		AsyncOperationStateChangeEvent<T> other = (AsyncOperationStateChangeEvent<T>)obj;
		return m_aop.equals(other.m_aop)
				&& m_toState.equals(other.m_toState);
	}
}
