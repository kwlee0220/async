package async.optor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;

import async.Service;
import async.ServiceState;
import async.ServiceStateChangeListener;
import async.support.AbstractService;
import utils.Errors;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CompositeService extends AbstractService {
	private final List<Service> m_components;
	private final List<ServiceStateChangeListener> m_listeners;
	
	private final ReentrantLock m_compositeLock = new ReentrantLock();
	private final Condition m_cond = m_compositeLock.newCondition();
	
	public CompositeService(Service... components) {
		this(Arrays.asList(components));
	}
	
	public CompositeService(Collection<Service> components) {
		if ( components == null || components.size() == 0 ) {
			throw new IllegalArgumentException("Property 'components' was not specified: class="
											+ getClass().getName());
		}

		m_components = Lists.newArrayList(components);
		m_listeners = IntStream.range(0, m_components.size())
								.mapToObj(idx -> {
									ServiceStateChangeListener listener = new ComonentListener(idx);
									m_components.get(idx).addStateChangeListener(listener);
									return listener;
								})
								.collect(Collectors.toList());
	}

	@Override
	protected void startService() throws Exception {
		List<Service> faileds = Lists.newCopyOnWriteArrayList();
		m_components.parallelStream()
					.forEach(comp -> Errors.toRunnable(()->comp.start(), error->faileds.add(comp)));
		if ( !faileds.isEmpty() ) {
			m_components.parallelStream().forEach(Service::stop);
			
			throw (Exception)faileds.get(0).getFailureCause();
		}
	}

	@Override
	protected void stopService() throws Exception {
		m_components.parallelStream().forEach(Service::stop);
	}
	
	class ComonentListener implements ServiceStateChangeListener {
		private final int m_idx;
		
		ComonentListener(int idx) {
			m_idx = idx;
		}

		@Override
		public void onStateChanged(Service target, ServiceState fromState, ServiceState toState) {
			if ( toState == ServiceState.FAILED ) {
				notifyServiceFailed(target.getFailureCause());
			}
		}
	}
}
