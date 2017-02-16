package async.support;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import async.AsyncOperation;
import async.OperationSchedulerListener;
import async.OperationSchedulerProvider;
import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee
 */
public abstract class AbstractOperationScheduler implements OperationSchedulerProvider {
	protected static final Logger s_logger = Logger.getLogger("SCHEDULER");

	// properties (BEGIN)
	private volatile Executor m_executor;	// pseudo property
	// properties (END)
	
	protected final Object m_schdrMutex = new Object();
	private final CopyOnWriteArraySet<OperationSchedulerListener> m_listeners
										= new CopyOnWriteArraySet<OperationSchedulerListener>();

	public void setExecutor(Executor executor) {
		m_executor = executor;
	}

	@Override
	public final Executor getExecutor() {
		return m_executor;
	}

	@Override
	public final void addOperationSchedulerListener(OperationSchedulerListener listener) {
		m_listeners.add(listener);
	}

	@Override
	public final void removeOperationSchedulerListener(OperationSchedulerListener listener) {
		m_listeners.remove(listener);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[policy=" + getSchedulingPolicyId() + "]";
	}
	
	protected void notifySubmittedToListeners(final AsyncOperation<?> aop) {
		for ( final OperationSchedulerListener listener: m_listeners ) {
			Utilities.executeAsynchronously(m_executor, new Runnable() {
				@Override public void run() {
					try {
						listener.onAsyncOperationSubmitted(aop);
					}
					catch ( Throwable ignored ) {
						m_listeners.remove(listener);
					}
				}
			});
		}
	}
}