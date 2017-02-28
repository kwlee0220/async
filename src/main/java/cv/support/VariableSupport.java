package cv.support;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.OperationStoppedException;
import cv.ValueInfo;
import cv.VariableListener;
import net.jcip.annotations.GuardedBy;
import utils.ExceptionUtils;
import utils.Utilities;
import utils.thread.ExecutorAware;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public final class VariableSupport<T> implements ExecutorAware {
	private static final Logger s_logger = LoggerFactory.getLogger("VAR.SUPPORT");
	
	private volatile Executor m_executor;
	private volatile Logger m_logger = s_logger;
	
	private final Object m_mutex = new Object();
	@GuardedBy("m_mutex") private ValueInfo<T> m_value;
	@GuardedBy("m_mutex") private final Set<VariableListener> m_listeners
															= new HashSet<VariableListener>();
	
	public VariableSupport() { }
	
	/**
	 * 주어진 변수의 초기값을 설정한다.
	 * 
	 * @param value	설정할 초기 값.
	 */
	public final void setInitialValue(ValueInfo<T> value) {
		synchronized ( m_mutex ) {
			m_value = value;
		}
	}
	
	public final void notifyValueUpdated(final ValueInfo<T> value) {
		synchronized ( m_mutex ) {
			m_value = value;
			m_mutex.notifyAll();

			for ( final VariableListener listener: m_listeners ) {
				Utilities.executeAsynchronously(m_executor, new Runnable() {
					@Override public void run() {
						try {
							listener.valueUpdated(value);
						}
						catch ( Throwable ignored ) {
							s_logger.warn("fails to notify Variable update: cause="
											+ ExceptionUtils.unwrapThrowable(ignored));
						}
					}
				});
			}
		}
	}
	
	public final void notifyValueUpdated(T value) {
		notifyValueUpdated(new ValueInfo<>(value, System.currentTimeMillis()));
	}

	public final ValueInfo<T> getValueInfo() {
		return m_value;
	}

	public final ValueInfo<T> waitValueUpdate(long startTimestamp) throws OperationStoppedException {
		synchronized ( m_mutex ) {
			if ( m_value != null && m_value.modified >= startTimestamp ) {
				return m_value;
			}
			
			while ( true ) {
				try {
					m_mutex.wait();
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					
					throw new OperationStoppedException();
				}
				
				if ( m_value != null && m_value.modified >= startTimestamp ) {
					return m_value;
				}
			}
		}
	}
	
	public ValueInfo<T> timedWaitValueUpdate(long startTimestamp, long timeout)
		throws OperationStoppedException {
		long due = System.currentTimeMillis() + timeout;
		if ( due < startTimestamp ) {
			return null;
		}

		synchronized ( m_mutex ) {
			if ( m_value != null && m_value.modified >= startTimestamp ) {
				return m_value;
			}
	
			while ( true ) {
				long remains = due - System.currentTimeMillis();
				if ( remains <= 0 ) {
					return null;
				}
				
				try {
					// 변경이 발생하는 경우, notifyValueUpdate() 메소드를 호출한
					// 쓰레드가 깨우기 때문에 주어진 'remains'보다 빨리 깨어날 수도 있다. 
					m_mutex.wait(remains);
				}
				catch ( InterruptedException e ) {
					Thread.currentThread().interrupt();
					
					throw new OperationStoppedException();
				}
				
				// 본 메소드가 호출된 이후에 발생된 변경인 경우인가 확인
				if ( m_value != null && m_value.modified >= startTimestamp ) {
					return m_value;
				}
			}
		}
	}
	
	public final boolean addUpdateListener(final VariableListener listener) {
		synchronized ( m_mutex ) {
			return m_listeners.add(listener);
		}
	}

	public final boolean removeUpdateListener(VariableListener listener) {
		synchronized ( m_mutex ) {
			return m_listeners.remove(listener);
		}
	}
	
	public final void handleLongUpdate(AsyncOperation<Void> aop, final T result) {
		aop.addStateChangeListener(new AsyncOperationListener<Void>() {
			@Override public void onAsyncOperationStarted(AsyncOperation<Void> targetAop) { }
			
			@Override
			public void onAsyncOperationFinished(AsyncOperation<Void> targetAop, AsyncOperationState state) {
				if ( state == AsyncOperationState.COMPLETED ) {
					notifyValueUpdated(new ValueInfo<>(result, System.currentTimeMillis()));
				}
			}
		});
	}

	@Override
	public Executor getExecutor() {
		return m_executor;
	}

	@Override
	public void setExecutor(Executor executor) {
		m_executor = executor;
	}
	
	public final Logger getLogger() {
		return m_logger;
	}
	
	public final void setLogger(Logger logger) {
		m_logger = (logger != null) ? logger : s_logger;
	}
}
