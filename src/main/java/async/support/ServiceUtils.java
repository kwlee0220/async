package async.support;

import javax.jws.Oneway;

import async.Service;
import async.ServiceState;
import async.ServiceStateChangeListener;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ServiceUtils {
	/**
	 * 주어진 인자의 Service 수행 중 올류가 발생하면 본 Service도 오류가 발생한 것으로 설정한다.
	 */
	public static final void setFailureDependency(Service dependee, AbstractService dependent) {
		dependee.addStateChangeListener(new ServiceStateChangeListener() {
			@Override @Oneway
			public void onStateChanged(Service target, ServiceState fromState, ServiceState toState) {
				if ( toState == ServiceState.FAILED ) {
					dependent.notifyServiceFailed(target.getFailureCause());
				}
			}
		});
	}

	public static final Object addDependencyOn(Service dependee, AbstractService dependent) {
		DependencyLink link = new DependencyLink(dependent);
		dependee.addStateChangeListener(link);
		
		return link;
	}

	public static final void removeDependencyOn(Service dependee, Object dependency) {
		dependee.removeStateChangeListener((ServiceStateChangeListener)dependency);
	}
	
	private static class DependencyLink implements ServiceStateChangeListener {
		private final AbstractService m_dependent;
		
		DependencyLink(AbstractService dependent) {
			m_dependent = dependent;
		}

		@Override
		public void onStateChanged(Service target, ServiceState fromState, ServiceState toState) {
			if ( toState == ServiceState.STOPPED ) {
				Utilities.runAsync(() -> m_dependent.stop(), m_dependent.getExecutor());
			}
			else if ( toState == ServiceState.RUNNING ) {
				Utilities.runAsync(() -> { 
					try {
						m_dependent.start();
					}
					catch ( Exception ignored ) { }
				}, m_dependent.getExecutor());
			}
			else if ( toState == ServiceState.FAILED ) {
				m_dependent.notifyServiceFailed(target.getFailureCause());
			}
		}
		
	}
}
