package async.support;

import java.util.HashSet;
import java.util.Set;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.SchedulableAsyncOperation;
import net.jcip.annotations.GuardedBy;



/**
 * 
 * @author Kang-Woo Lee
 */
public class NoWaitOperationScheduler extends AbstractOperationScheduler {
	@GuardedBy("m_schdrMutex") private final Set<SchedulableAsyncOperation<?>> m_runnings;
	
	public NoWaitOperationScheduler() {
		m_runnings = new HashSet<SchedulableAsyncOperation<?>>();
	}
	
	@Override
	public final String getSchedulingPolicyId() {
		return "nowait";
	}

	@Override
	public void submit(SchedulableAsyncOperation<?> schedule) {
		schedule.addStateChangeListener(m_listener);
		notifySubmittedToListeners(schedule);
		
		synchronized ( m_schdrMutex ) {
			if ( schedule.permitToStart() ) {
				m_runnings.add(schedule);
			}
		}
	}

	@Override
	public void stopAll() {
		synchronized ( m_schdrMutex ) {
			for ( SchedulableAsyncOperation<?> schedule: m_runnings ) {
				schedule.removeStateChangeListener(m_listener);
				schedule.cancel();
			}
			
			for ( SchedulableAsyncOperation<?> schedule: m_runnings ) {
				try {
					schedule.waitForFinished();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					
					return;
				}
			}
			
			m_runnings.clear();
		}
	}

	private final AsyncOperationListener m_listener = new AsyncOperationListener() {
		@Override public void onAsyncOperationStarted(AsyncOperation aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation aop, AsyncOperationState state) {
			synchronized ( m_schdrMutex ) {
				m_runnings.remove(aop);
			}
			
			aop.removeStateChangeListener(m_listener);
		}
	};
}