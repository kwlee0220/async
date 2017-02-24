package async.optor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Preconditions;

import async.Service;
import async.ServiceState;
import async.ServiceStateChangeListener;
import async.support.AbstractService;
import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ConcurrentService extends AbstractService {
	private final Service[] m_components;
	private volatile Boolean[] m_done;
	
	private final ReentrantLock m_compositeLock = new ReentrantLock();
	private final Condition m_cond = m_compositeLock.newCondition();
	
	public static class Builder {
		private List<Service> m_components = new ArrayList<Service>();
		private Set<Class<?>> m_intfcs = new HashSet<Class<?>>();
		
		public Builder() {
			m_intfcs.add(Service.class);
		}
		
		public Builder add(Service... components) {
			m_components.addAll(Arrays.asList(components));
			return this;
		}
		
		public Builder addInterface(Class<?> intfc) {
			Preconditions.checkArgument(intfc.isInterface(), "not interface=" + intfc);
			
			m_intfcs.add(intfc);
			return this;
		}
		
		public Service build() throws Exception {
			Service[] comps = m_components.toArray(new Service[m_components.size()]);
			ConcurrentService composite = new ConcurrentService(comps);
			
			if ( m_intfcs.size() == 1 ) {
				return composite;
			}
			
			Class<?>[] intfcs = m_intfcs.toArray(new Class<?>[m_intfcs.size()]); 
			return (Service)Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
													intfcs, new Interceptor(composite));
		}
	}
	
	static class Interceptor implements InvocationHandler {
		private final ConcurrentService m_composite;
		
		Interceptor(ConcurrentService composite) {
			m_composite = composite;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Class<?> declClass = method.getDeclaringClass();
			if ( declClass.equals(Service.class) ) {
				return method.invoke(m_composite, args);
			}
			else {
				return method.invoke(m_composite.m_components[0], args);
			}
		}
	};
	
	public ConcurrentService(Service... components) {
		this(Arrays.asList(components));
	}
	
	public ConcurrentService(Collection<Service> components) {
		if ( components == null || components.size() == 0 ) {
			throw new IllegalArgumentException("Property 'components' was not specified: class="
											+ getClass().getName());
		}

		m_components = components.toArray(new Service[components.size()]);
		
		m_done = new Boolean[m_components.length];
		for ( int i =0; i < m_components.length; ++i ) {
			m_done[i] = new Boolean(false);
			m_components[i].addStateChangeListener(new StartMonitor(i));
		}
	}

	@Override
	protected void startService() throws Exception {
		m_compositeLock.lock();
		try {
			Arrays.fill(m_done, false);
	
			for ( int i =0; i < m_components.length; ++i ) {
				final Service comp = m_components[i];
				comp.addStateChangeListener(new StartMonitor(i));
				
				Utilities.runCheckedAsync(()->comp.start(), getExecutor());
			}
			
			// 모두 다 start() 메소드가 호출되었는지를 확인한다.
			while ( Arrays.asList(m_done).stream().anyMatch(flag->!flag) ) {
				m_cond.await();
			}
		}
		finally {
			m_compositeLock.unlock();
		}
	}

	@Override
	protected void stopService() throws Exception {
		m_compositeLock.lock();
		try {
			Arrays.fill(m_done, false);
	
			for ( int i =0; i < m_components.length; ++i ) {
				final int idx = i;
				final Service comp = m_components[i];
				comp.addStateChangeListener(new StopMonitor(i));
				
				Utilities.runCheckedAsync(() -> {
					comp.stop();
					
					// comp가 이미 종료된 상태이면 StateChangeListener가 호출되지 않기 때문에
					// stop() 메소드 호출 후, 안전하게 종료 상태이면 done을 true로 세팅하도록 한다. 
					if ( comp.isFinished() ) {
						m_done[idx] = true;
					}
				}, getExecutor());
			}
			
			// 모두 다 start() 메소드가 호출되었는지를 확인한다.
			while ( Arrays.asList(m_done).stream().anyMatch(done->!done) ) {
				m_cond.await();
			}
		}
		finally {
			m_compositeLock.unlock();
		}
	}
	
	private void setDone(int idx) {
		m_compositeLock.lock();
		try {
			m_done[idx] = true;
			m_cond.signalAll();
		}
		finally {
			m_compositeLock.unlock();
		}
	}
	
	class StartMonitor implements ServiceStateChangeListener {
		private final int m_idx;
		
		StartMonitor(int idx) {
			m_idx = idx;
		}

		@SuppressWarnings("incomplete-switch")
		@Override
		public void onStateChanged(Service target, ServiceState fromState, ServiceState toState) {
			switch ( toState ) {
				case RUNNING:
				case FAILED:
					setDone(m_idx);
					break;
			}
		}
	}
	
	class StopMonitor implements ServiceStateChangeListener {
		private final int m_idx;
		
		StopMonitor(int idx) {
			m_idx = idx;
		}

		@SuppressWarnings("incomplete-switch")
		@Override
		public void onStateChanged(Service target, ServiceState fromState, ServiceState toState) {
			switch ( toState ) {
				case STOPPED:
				case FAILED:
					setDone(m_idx);
					break;
			}
		}
	}
}
