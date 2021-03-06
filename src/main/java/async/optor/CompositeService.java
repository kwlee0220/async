package async.optor;

import static utils.func.Unchecked.lift;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;

import async.Service;
import async.ServiceState;
import async.ServiceStateChangeEvent;
import async.support.AbstractService;
import utils.func.FailureCase;
import utils.func.Unchecked;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CompositeService extends AbstractService {
	private final List<Service> m_components;
	
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
		IntStream.range(0, m_components.size())
					.forEach(idx -> {
						ComonentListener listener
									= new ComonentListener(CompositeService.this, idx);
						m_components.get(idx).addStateChangeListener(listener);
					});
	}

	@Override
	protected void startService() throws Exception {
		List<FailureCase<Service>> faileds = Lists.newCopyOnWriteArrayList();
		
		m_components.parallelStream()
					.forEach(lift(Service::start, Unchecked.collect(faileds)));
		if ( !faileds.isEmpty() ) {
			m_components.parallelStream().forEach(Service::stop);
			
			throw (Exception)faileds.get(0).getData().getFailureCause();
		}
	}

	@Override
	protected void stopService() throws Exception {
		m_components.parallelStream().forEach(Service::stop);
	}
	
	private static class ComonentListener {
		private final CompositeService m_composite;
		private final int m_idx;
		
		private ComonentListener(CompositeService composite, int idx) {
			m_composite = composite;
			m_idx = idx;
		}

		@Subscribe
		public void onStateChanged(ServiceStateChangeEvent event) {
			if ( event.getToState() == ServiceState.FAILED ) {
				m_composite.notifyServiceFailed(event.getService().getFailureCause());
			}
			else if ( event.getToState() == ServiceState.STOPPED ) {
				m_composite.stop();
			}
		}
	}
}
