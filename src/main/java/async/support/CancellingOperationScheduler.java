package async.support;

import javax.annotation.concurrent.GuardedBy;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.SchedulableAsyncOperation;



/**
 * 
 * @author Kang-Woo Lee
 */
public class CancellingOperationScheduler extends AbstractOperationScheduler {
	@GuardedBy("m_schdrMutex") private SchedulableAsyncOperation<?> m_running;
	
	public CancellingOperationScheduler() {
		m_running = null;
	}
	
	@Override
	public final String getSchedulingPolicyId() {
		return "cancel_previous";
	}

	@Override
	public void submit(SchedulableAsyncOperation<?> schedule) {
		schedule.addStateChangeListener(m_listener);
		notifySubmittedToListeners(schedule);
		
		synchronized ( m_schdrMutex ) {
			stopCurrentInGuard();
			
			m_running = schedule;
			m_running.permitToStart();
		}
	}

	@Override
	public void stopAll() {
		synchronized ( m_schdrMutex ) {
			stopCurrentInGuard();
		}
	}

	private void stopCurrentInGuard() {
		if ( m_running != null ) {
			m_running.cancel();
			
			SchedulableAsyncOperation<?> stopped = m_running;
			m_running = null;
			
			try {
				stopped.waitForFinished();
			}
			catch ( InterruptedException e ) {
				Thread.currentThread().interrupt();
				
				return;
			}
		}
	}

	private final AsyncOperationListener m_listener = new AsyncOperationListener() {
		@Override public void onAsyncOperationStarted(AsyncOperation aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation aop, AsyncOperationState state) {
			synchronized ( m_schdrMutex ) {
				m_running = null;
			}
			
			aop.removeStateChangeListener(m_listener);
		}
	};
}