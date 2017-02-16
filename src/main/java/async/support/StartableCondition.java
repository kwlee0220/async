package async.support;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.jws.Oneway;

import async.FutureCondition;
import async.Startable;
import async.StartableListener;
import async.StartableState;
import net.jcip.annotations.GuardedBy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class StartableCondition implements FutureCondition {
	private static final int STATE_WAITING = 0;
	private static final int STATE_DONE = 1;
	
	private final Startable m_target;
	private final StartableState m_targetState;
	
	private final Lock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private int m_state;
	
	public static StartableCondition whenStarted(Startable target) {
		return new StartableCondition(target, StartableState.RUNNING);
	}
	
	public static StartableCondition whenStopped(Startable target) {
		return new StartableCondition(target, StartableState.STOPPED);
	}
	
	public StartableCondition(Startable target, StartableState targetState) {
		m_target = target;
		m_targetState = targetState;
		
		m_state = (target.getState() != targetState) ? STATE_WAITING : STATE_DONE;
		if ( m_state == STATE_WAITING ) {
			m_target.addStartableListener(m_listener);
		}
	}

	@Override
	public boolean evaluateNow() {
		m_lock.lock();
		try {
			return m_state == STATE_DONE;
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void await() throws InterruptedException {
		m_lock.lock();
		try {
			switch ( m_state ) {
				case STATE_WAITING:
					m_cond.await();
					break;
				case STATE_DONE:
					break;
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public void await(long time, TimeUnit unit) throws InterruptedException {
		m_lock.lock();
		try {
			switch ( m_state ) {
				case STATE_WAITING:
					m_cond.await(time, unit);
					break;
				case STATE_DONE:
					break;
			}
		}
		finally {
			m_lock.unlock();
		}
	}
	
	private final StartableListener m_listener = new StartableListener() {
		@Override @Oneway
		public void onStateChanged(Startable target, StartableState fromState, StartableState toState) {
			if ( toState == m_targetState ) {
				m_lock.lock();
				try {
					m_state = STATE_DONE;
					m_cond.signalAll();
					
					m_target.removeStartableListener(this);
				}
				finally {
					m_lock.unlock();
				}
			}
		}
	};
}
