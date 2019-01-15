package async.support;

import java.util.ArrayList;
import java.util.List;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.SchedulableAsyncOperation;
import net.jcip.annotations.GuardedBy;



/**
 * 
 * @author Kang-Woo Lee
 */
public class QueuedOperationScheduler extends AbstractOperationScheduler {
	@GuardedBy("m_schdrMutex") private final List<SchedulableAsyncOperation<?>> m_queue;
	@GuardedBy("m_schdrMutex") private SchedulableAsyncOperation<?> m_running;
	
	public QueuedOperationScheduler() {
		m_queue = new ArrayList<SchedulableAsyncOperation<?>>();
		m_running = null;
	}
	
	@Override
	public final String getSchedulingPolicyId() {
		return "queued";
	}

	@Override
	public void submit(SchedulableAsyncOperation<?> schedule) {
		schedule.addStateChangeListener(m_listener);
		notifySubmittedToListeners(schedule);
		
		synchronized ( m_schdrMutex ) {
			if ( m_running == null ) {
				startInGuard(schedule);
			}
			else {
				m_queue.add(schedule);
				if ( s_logger.isInfoEnabled() ) {
					s_logger.info("enqueued: " + schedule);
				}
			}
		}
	}

	@Override
	public void stopAll() {
		List<SchedulableAsyncOperation<?>> killeds = new ArrayList<SchedulableAsyncOperation<?>>();
		
		synchronized ( m_schdrMutex ) {
			int size = m_queue.size();
			for ( int i =0; i < size; ++i ) {
				SchedulableAsyncOperation<?> schedule = m_queue.get(i);
				
				schedule.cancel();
				killeds.add(schedule);
			}
			m_queue.clear();
			
			if ( m_running != null ) {
				m_running.cancel();
				
				killeds.add(m_running);
				m_running = null;
			}
			
			for ( SchedulableAsyncOperation<?> schedule: killeds ) {
				try {
					schedule.waitForFinished();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					
					return;
				}
			}
		}
			
	}
	
	private boolean startInGuard(SchedulableAsyncOperation<?> schedule) {
		if ( schedule.permitToStart() ) {
			m_running = schedule;
			
			return true;
		}
		else {
			return false;
		}
	}

	private final AsyncOperationListener m_listener = new AsyncOperationListener() {
		@Override public void onAsyncOperationStarted(AsyncOperation aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation aop, AsyncOperationState state) {
			synchronized ( m_schdrMutex ) {
				if ( m_running == aop ) {
					m_running.cancel();
					m_running = null;
					
					try {
						aop.waitForFinished();
					}
					catch ( InterruptedException e ) {
						Thread.currentThread().interrupt();
						
						return;
					}
					
				}
			}
			
			aop.removeStateChangeListener(m_listener);
		}
	};
}