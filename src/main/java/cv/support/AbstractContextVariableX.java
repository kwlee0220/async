package cv.support;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import async.AsyncOperation;
import async.OperationStoppedException;
import async.support.AbstractStartable;
import cv.ContextVariable;
import cv.ValueInfo;
import cv.VariableListener;
import net.jcip.annotations.GuardedBy;
import utils.ExceptionUtils;
import utils.Initializable;
import utils.Utilities;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractContextVariableX<T> extends AbstractStartable
												implements ContextVariable<T>, Initializable {
	private static final int STATE_CLOSED = 0;
	private static final int STATE_IN_TRANSIT = 1;
	private static final int STATE_OPEN = 2;
	
	private final ReentrantLock m_cvLock = new ReentrantLock();
	private final Condition m_cvCond = m_cvLock.newCondition();
	@GuardedBy("m_cvLock") private int m_state = STATE_CLOSED;
	@GuardedBy("m_cvLock") private ValueInfo<T> m_value;
	@GuardedBy("m_cvLock") private final Set<VariableListener> m_listeners
															= new HashSet<VariableListener>();
	
	protected abstract void startContextVariable() throws Throwable;
	protected abstract void stopContextVariable();

	public AbstractContextVariableX() { }

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
		m_cvLock.lock();
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
			m_cvLock.unlock();
		}
	}

	@Override
	public ValueInfo<T> timedWaitValueUpdate(long startTimestamp, long timeout)
		throws OperationStoppedException {
		final Date due = new Date(System.currentTimeMillis() + timeout);

		m_cvLock.lock();
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
			m_cvLock.unlock();
		}
	}

	@Override
	public boolean addUpdateListener(final VariableListener listener) {
		boolean doOpen = false;
		m_cvLock.lock();
		try {
			// 첫번째 리스너 등록이고, 본 상황변수가 이미 시작된 경우는
			// 상황 변수 동작으로 진짜로 시작시킨다.
			if ( m_listeners.add(listener) ) {
				if ( m_listeners.size() == 1 && isRunning() ) {
					if ( waitForStableInGuard() == STATE_CLOSED ) {
						doOpen = true;
						m_state = STATE_IN_TRANSIT;
					}
				}
			
				return true;
			}
			else {
				return false;
			}
		}
		finally {
			m_cvLock.unlock();
			
			if ( doOpen ) {
				try {
					startContextVariable();
					markStable(STATE_OPEN);
				}
				catch ( Throwable e ) {
					m_listeners.remove(listener);
					markStable(STATE_CLOSED);
					
					throw new RuntimeException("fails to start ContextVariable: " + this);
				}
			}
		}
		
		
	}

	@Override
	public boolean removeUpdateListener(VariableListener listener) {
		boolean doClose = false;
		
		m_cvLock.lock();
		try {
			if ( m_listeners.remove(listener) ) {
				if ( m_listeners.size() == 0 && isRunning() ) {
					if ( waitForStableInGuard() == STATE_OPEN ) {
						doClose = true;
						m_state = STATE_IN_TRANSIT;
					}
				}
				
				return true;
			}
			else {
				return false;
			}
		}
		finally {
			m_cvLock.unlock();
			
			if ( doClose ) {
				try {
					stopContextVariable();
				}
				catch ( Throwable e ) {
					throw new RuntimeException("fails to start ContextVariable: " + this);
				}
				finally {
					markStable(STATE_CLOSED);
				}
			}
		}
	}

	@Override
	protected final void startStartable() throws Exception {
		boolean doOpen = false;
		
		m_cvLock.lock();
		try {
			if ( m_listeners.size() > 0 ) {
				if ( waitForStableInGuard() == STATE_CLOSED ) {
					doOpen = true;
					m_state = STATE_IN_TRANSIT;
				}
			}
		}
		finally {
			m_cvLock.unlock();
			
			if ( doOpen ) {
				try {
					startContextVariable();
					markStable(STATE_OPEN);
				}
				catch ( Throwable e ) {
					markStable(STATE_CLOSED);
					
					throw new RuntimeException("fails to start ContextVariable: " + this);
				}
			}
		}
	}

	@Override
	protected final void stopStartable() throws Exception {
		boolean doClose = false;
		
		m_cvLock.lock();
		try {
			if ( m_listeners.size() > 0 ) {
				if ( waitForStableInGuard() == STATE_OPEN ) {
					doClose = true;
					m_state = STATE_IN_TRANSIT;
				}
			}
		}
		finally {
			m_cvLock.unlock();
			
			if ( doClose ) {
				try {
					stopContextVariable();
				}
				catch ( Throwable e ) {
					throw new RuntimeException("fails to start ContextVariable: " + this);
				}
				finally {
					markStable(STATE_CLOSED);
				}
			}
		}
	}
	
	protected final void notifyValueUpdated(T value) {
		notifyValueUpdated(new ValueInfo<>(value));
	}
	
	protected final void notifyValueUpdated(final ValueInfo<T> value) {
		m_cvLock.lock();
		try {
			m_value = value;
			m_cvCond.signalAll();

			for ( final VariableListener listener: m_listeners ) {
				Utilities.executeAsynchronously(getExecutor(), new Runnable() {
					@Override public void run() {
						try {
							listener.valueUpdated(value);
						}
						catch ( Throwable ignored ) {
							removeUpdateListener(listener);
							
							getLogger().warn("remove the error-raising VariableListener: error="
											+ ExceptionUtils.unwrapThrowable(ignored));
						}
					}
				});
			}
		}
		finally {
			m_cvLock.unlock();
		}
	}
	
	private final void markStable(int state) {
		m_cvLock.lock();
		try {
			m_state = state;
			m_cvCond.signalAll();
		}
		finally {
			m_cvLock.unlock();
		}
	}
	
	private final int waitForStableInGuard() {
		try {
			while ( m_state == STATE_IN_TRANSIT ) {
				m_cvCond.await();
			}
			
			return m_state;
		}
		catch ( InterruptedException e ) {
			throw new RuntimeException("" + e);
		}
	}
}
