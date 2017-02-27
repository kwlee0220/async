package async.support;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import com.google.common.eventbus.Subscribe;

import async.FutureCondition;
import async.Service;
import async.ServiceState;
import async.ServiceStateChangeEvent;
import net.jcip.annotations.GuardedBy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ServiceStateCondition implements FutureCondition {
	private static final int STATE_WAITING = 0;
	private static final int STATE_DONE = 1;
	
	private final Service m_target;
	private final Predicate<ServiceState> m_statePred;
	
	private final Lock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private int m_state;
	
	public static ServiceStateCondition whenStarted(Service target) {
		return new ServiceStateCondition(target, (state) -> state == ServiceState.RUNNING);
	}
	
	public static ServiceStateCondition whenStopped(Service target) {
		return new ServiceStateCondition(target, (state) -> state == ServiceState.STOPPED);
	}
	
	public ServiceStateCondition(Service target, Predicate<ServiceState> statePred) {
		m_target = target;
		m_statePred = statePred;
		
		m_state = statePred.test(target.getState()) ? STATE_WAITING : STATE_DONE;
		if ( m_state == STATE_WAITING ) {
			m_target.addStateChangeListener(this);
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
	
	@Subscribe
	public void onStateChanged(ServiceStateChangeEvent event) {
		if ( m_statePred.test(event.getToState()) ) {
			m_lock.lock();
			try {
				m_state = STATE_DONE;
				m_cond.signalAll();
				
				m_target.removeStateChangeListener(this);
			}
			finally {
				m_lock.unlock();
			}
		}
	}
}
