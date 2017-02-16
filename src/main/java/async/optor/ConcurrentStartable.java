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

import async.Startable;
import async.StartableState;
import async.support.AbstractStartable;
import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ConcurrentStartable extends AbstractStartable {
	private final Startable[] m_components;
	private volatile boolean[] m_done;
	
	private final ReentrantLock m_compositeLock = new ReentrantLock();
	private final Condition m_cond = m_compositeLock.newCondition();
	
	public static class Builder {
		private List<Startable> m_components = new ArrayList<Startable>();
		private Set<Class<?>> m_intfcs = new HashSet<Class<?>>();
		
		public Builder() {
			m_intfcs.add(Startable.class);
		}
		
		public Builder addComponent(Startable... components) {
			m_components.addAll(Arrays.asList(components));
			return this;
		}
		
		public Builder addInterface(Class<?> intfc) {
			if ( !intfc.isInterface() ) {
				throw new IllegalArgumentException("not interface=" + intfc);
			}
			
			m_intfcs.add(intfc);
			return this;
		}
		
		public Startable build() throws Exception {
			Startable[] comps = m_components.toArray(new Startable[m_components.size()]);
			ConcurrentStartable composite = new ConcurrentStartable(comps);
			
			if ( m_intfcs.size() == 1 ) {
				return composite;
			}
			
			Class<?>[] intfcs = m_intfcs.toArray(new Class<?>[m_intfcs.size()]); 
			return (Startable) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
													intfcs, new Interceptor(composite));
		}
	}
	
	static class Interceptor implements InvocationHandler {
		private final ConcurrentStartable m_composite;
		
		Interceptor(ConcurrentStartable composite) {
			m_composite = composite;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Class<?> declClass = method.getDeclaringClass();
			if ( declClass.equals(Startable.class) ) {
				return method.invoke(m_composite, args);
			}
			else {
				return method.invoke(m_composite.m_components[0], args);
			}
		}
	};
	
	public ConcurrentStartable(Startable... components) {
		if ( components == null || components.length == 0 ) {
			throw new IllegalArgumentException("Property 'components' was not specified: class="
											+ getClass().getName());
		}

		m_components = components;
		m_done = new boolean[m_components.length];
		
	}
	
	public ConcurrentStartable(Collection<Startable> components) {
		if ( components == null || components.size() == 0 ) {
			throw new IllegalArgumentException("Property 'components' was not specified: class="
											+ getClass().getName());
		}

		m_components = components.toArray(new Startable[components.size()]);
		m_done = new boolean[m_components.length];
		
	}

	@Override
	protected void startStartable() throws Exception {
		m_compositeLock.lock();
		try {
			Arrays.fill(m_done, false);
	
			for ( int i =0; i < m_components.length; ++i ) {
				Utilities.executeAsynchronously(getExecutor(), new Worker(i, StartableState.RUNNING));
			}
			
			for ( int i =0; i < m_components.length; ++i ) {
				while ( !m_done[i] ) {
					m_cond.await();
				}
			}
		}
		finally {
			m_compositeLock.unlock();
		}
	}

	@Override
	protected void stopStartable() throws Exception {
		m_compositeLock.lock();
		try {
			Arrays.fill(m_done, false);
	
			for ( int i =0; i < m_components.length; ++i ) {
				Utilities.executeAsynchronously(getExecutor(), new Worker(i, StartableState.STOPPED));
			}
			
			for ( int i =0; i < m_components.length; ++i ) {
				while ( !m_done[i] ) {
					m_cond.await();
				}
			}
		}
		finally {
			m_compositeLock.unlock();
		}
	}

	private void setDone(int index, boolean flag) {
		m_compositeLock.lock();
		try {
			m_done[index] = flag;
			m_cond.signalAll();
		}
		finally {
			m_compositeLock.unlock();
		}
	}
	
	class Worker implements Runnable {
		private final int m_index;
		private final StartableState m_tagetState;
		
		Worker(int index, StartableState targetState) {
			m_index = index;
			m_tagetState = targetState;
		}
		
		@Override
		public void run() {
			switch ( m_tagetState ) {
				case RUNNING:
					try {
						m_components[m_index].start();
					}
					catch ( Exception e ) {
					}
					break;
				case STOPPED:
					m_components[m_index].stop();
					break;
				default:
					throw new UnsupportedOperationException("failure propagation");
			}
			
			setDone(m_index, true);
		}
	}
}
