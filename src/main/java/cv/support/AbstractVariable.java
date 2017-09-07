package cv.support;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.Sets;

import async.AsyncOperation;
import async.OperationStoppedException;
import async.support.AsyncUtils;
import cv.ValueInfo;
import cv.Variable;
import cv.VariableListener;
import net.jcip.annotations.GuardedBy;
import utils.Throwables;
import utils.Initializable;
import utils.LoggerSettable;
import utils.thread.ExecutorAware;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractVariable<T> implements Variable<T>, ExecutorAware, LoggerSettable,
													Initializable {
	private static final int STATE_CLOSED = 0;
	private static final int STATE_IN_TRANSIT = 1;
	private static final int STATE_OPEN = 2;
	
	private final ReentrantLock m_lock = new ReentrantLock();
	private final Condition m_cvCond = m_lock.newCondition();
	@GuardedBy("m_cvLock") private int m_state = STATE_CLOSED;
	@GuardedBy("m_cvLock") private ValueInfo<T> m_value;
	@GuardedBy("m_cvLock") private final Set<VariableListener> m_listeners = Sets.newHashSet();
	
	protected abstract void startContextVariable() throws Throwable;
	protected abstract void stopContextVariable();

	public AbstractVariable() { }

	@Override
	public Object getValue() {
		return getValueInfo() != null ? getValueInfo().value : null;
	}

	@Override
	public final ValueInfo<T> getValueInfo() {
		return m_value;
	}

	@Override
	public boolean isUpdatable() {
		return false;
	}

	@Override
	public AsyncOperation<Void> setValue(Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ValueInfo<T> waitValueUpdate(long startTimestamp) throws OperationStoppedException {
		m_lock.lock();
		try {
			while ( true ) {
				if ( m_value != null && m_value.modified >= startTimestamp ) {
					return m_value;
				}
				
				try {
					m_cvCond.await();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					
					throw new OperationStoppedException();
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public ValueInfo<T> timedWaitValueUpdate(long startTimestamp, long timeout)
		throws OperationStoppedException {
		final Date due = new Date(System.currentTimeMillis() + timeout);

		m_lock.lock();
		try {
			while ( true ) {
				if ( m_value != null && m_value.modified >= startTimestamp ) {
					return m_value;
				}
				
				try {
					if ( !m_cvCond.awaitUntil(due) ) {
						return null;
					}
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					
					throw new OperationStoppedException();
				}
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean addUpdateListener(final VariableListener listener) {
		m_lock.lock();
		try {
			return m_listeners.add(listener);
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean removeUpdateListener(VariableListener listener) {
		m_lock.lock();
		try {
			return m_listeners.remove(listener);
		}
		finally {
			m_lock.unlock();
		}
	}
	
	protected final void notifyValueUpdated(T value) {
		notifyValueUpdated(new ValueInfo<>(value));
	}
	
	protected final void notifyValueUpdated(final ValueInfo<T> value) {
		m_lock.lock();
		try {
			m_value = value;
			m_cvCond.signalAll();

			for ( final VariableListener listener: m_listeners ) {
				AsyncUtils.runAsync(() -> {
					try {
						listener.valueUpdated(value);
					}
					catch ( Throwable ignored ) {
						removeUpdateListener(listener);
						
						getLogger().warn("remove the error-raising VariableListener: error="
										+ Throwables.unwrapThrowable(ignored));
					}
				}, getExecutor());
			}
		}
		finally {
			m_lock.unlock();
		}
	}
}
