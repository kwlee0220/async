package async;

import event.Event;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ServiceStateChangeEvent implements Event {
	public static final String PROP_SERVICE = "service";
	public static final String PROP_FROM_STATE = "fromState";
	public static final String PROP_TO_STATE = "toState";
	public static final String PROP_TAG = "tag";
	
	private final Service m_service;
	private final ServiceState m_fromState;
	private final ServiceState m_toState;
	private final String m_tag;
	
	/**
	 * 서비스 상태 변경 이벤트 객체를 생성한다.
	 * 
	 * @param service	상태가 전이된 대상 Service 객체.
	 * @param fromState	이전되기 이전 상태.
	 * @param toState	이전된 상태 
	 */
	public ServiceStateChangeEvent(Service service, ServiceState fromState, ServiceState toState) {
		Utilities.checkNotNullArgument(service, "target service is null");
		Utilities.checkNotNullArgument(fromState, "from state is null");
		Utilities.checkNotNullArgument(toState, "to state is null");
		
		m_service = service;
		m_fromState = fromState;
		m_toState = toState;
		m_tag = null;
	}
	
	/**
	 * 서비스 상태 변경 이벤트 객체를 생성한다.
	 * 
	 * @param service	상태가 전이된 대상 Service 객체.
	 * @param fromState	이전되기 이전 상태.
	 * @param toState	이전된 상태 
	 * @param tag		태그 정보
	 */
	public ServiceStateChangeEvent(Service service, ServiceState fromState, ServiceState toState,
									String tag) {
		Utilities.checkNotNullArgument(service, "target service is null");
		Utilities.checkNotNullArgument(fromState, "from state is null");
		Utilities.checkNotNullArgument(toState, "to state is null");
		Utilities.checkNotNullArgument(tag, "tag is null");
		
		m_service = service;
		m_fromState = fromState;
		m_toState = toState;
		m_tag = tag;
	}

	@Override
	public String[] getEventTypeIds() {
		return new String[]{ ServiceStateChangeEvent.class.getName() };
	}

	@Override
	public String[] getPropertyNames() {
		return new String[]{ PROP_SERVICE, PROP_FROM_STATE, PROP_TO_STATE, PROP_TAG };
	}

	@Override
	public Object getProperty(String name) {
		switch ( name ) {
			case PROP_SERVICE:
				return m_service;
			case PROP_FROM_STATE:
				return m_fromState;
			case PROP_TO_STATE:
				return m_toState;
			case PROP_TAG:
				return m_tag;
			default:
				throw new IllegalArgumentException("property.name=" + name);
		}
	}
	
	public final Service getService() {
		return m_service;
	}
	
	public final ServiceState getFromState() {
		return m_fromState;
	}
	
	public final ServiceState getToState() {
		return m_toState;
	}
	
	public final String getTag() {
		return m_tag;
	}
	
	@Override
	public String toString() {
		return String.format("service: %s, %s -> %s", m_service, m_fromState, m_toState);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != ServiceStateChangeEvent.class ) {
			return false;
		}
		
		ServiceStateChangeEvent other = (ServiceStateChangeEvent)obj;
		return m_service.equals(other.m_service)
				&& m_fromState.equals(other.m_fromState)
				&& m_toState.equals(other.m_toState);
	}
}
