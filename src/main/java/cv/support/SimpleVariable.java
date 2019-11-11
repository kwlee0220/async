package cv.support;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;
import javax.jws.Oneway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import async.AsyncOperation;
import async.AsyncOperationState;
import async.OperationStoppedException;
import async.support.AsyncOperationFinishListener;
import async.support.AsyncUtils;
import cv.ValueInfo;
import cv.Variable;
import cv.VariableListener;
import cv.VariableUpdateException;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SimpleVariable<T> implements Variable<T> {
	private static final Logger s_logger = LoggerFactory.getLogger("VAR.SIMPLE");
	
	private volatile boolean m_updateable;
	private final VariableUpdateHandler m_handler;
	protected final VariableSupport<T> m_support = new VariableSupport<>();
	private final ReentrantLock m_lock = new ReentrantLock();
	@GuardedBy("m_lock") protected Set<AsyncOperation<Void>> m_updaters
														= new HashSet<AsyncOperation<Void>>();
	
	/**
	 * <code>SimpleVariable</code>를 생성한다.
	 * <p>
	 * 변경이 지원되지 않는 경우는 {@link #setValue(Object)} 메소드를 호출하는 경우
	 * {@link camus.UnsupportedOperationException} 예외가 발생된다.
	 * 
	 * @param updateable	생성될 변수의 갱신 지원 여부.
	 */
	public SimpleVariable(boolean updateable) {
		m_updateable = updateable;
		m_handler = null;
		
		m_support.setLogger(s_logger);
	}
	
	public SimpleVariable(VariableUpdateHandler handler) {
		m_updateable = true;
		m_handler = handler;
		
		m_support.setLogger(s_logger);
	}
	
	/**
	 * 주어진 변수의 초기값을 설정한다.
	 * <p>
	 * {@link #setValue(Object)}와는 달리 변수 값 변경에 따른 리스너들에게 설정 사실이 전달되지 않는다.
	 * 
	 * @param value	설정할 초기 값.
	 * @throws IllegalStateException	변수의 상태가 {@link StartableState#RUNNING}인 경우.
	 */
	public final void setInitialValue(T value) {
		m_support.setInitialValue(new ValueInfo<>(value, System.currentTimeMillis()));
	}

	@Override
	public final boolean isUpdatable() {
		return m_updateable;
	}

	@Override
	public final T getValue() {
		ValueInfo<T> info = getValueInfo();
		return info != null ? info.value : null;
	}

	@Override
	public ValueInfo<T> getValueInfo() {
		return m_support.getValueInfo();
	}

	@Override
	public ValueInfo<T> waitValueUpdate(long startTimestamp) throws OperationStoppedException {
		return m_support.waitValueUpdate(startTimestamp);
	}
	
	@Override
	public ValueInfo<T> timedWaitValueUpdate(long startTimestamp, long timeout)
		throws OperationStoppedException {
		return m_support.timedWaitValueUpdate(startTimestamp, timeout);
	}

	@Override
	public AsyncOperation<Void> setValue(T value) {
		if ( m_updateable ) {
			AsyncOperation<Void> aop = null;
			
			if ( m_handler != null ) {
				m_lock.lock();
				try {
					if ( (aop = m_handler.update(value)) != null ) {
						m_updaters.add(aop);
						
						// 생성된 aop의 수행이 성공적으로 완료되면 본 변수의 'notifyValueUpdate()' 함수를
						// 호출하도록 설정한다.
						aop.addStateChangeListener(new AopListener(value));
						aop.start();
					}
				}
				catch ( VariableUpdateException e ) {
					throw e;
				}
				catch ( Exception e ) {
					throw new VariableUpdateException("" + e);
				}
				finally {
					m_lock.unlock();
				}
			}
			
			m_support.notifyValueUpdated(new ValueInfo<>(value, System.currentTimeMillis()));
			
			return aop;
		}
		else {
			throw new UnsupportedOperationException(getClass().getSimpleName()
														+ ".setValue(), value=" + value);
		}
	}
	
	public void stopRunningUpdateAll() {
		m_lock.lock();
		try {
			for ( AsyncOperation<Void> update: m_updaters ) {
				AsyncUtils.stopQuietly(update);
			}
		}
		finally {
			m_lock.unlock();
		}
	}

	@Override
	public boolean addUpdateListener(VariableListener listener) {
		return m_support.addUpdateListener(listener);
	}

	@Override
	public boolean removeUpdateListener(VariableListener listener) {
		return m_support.removeUpdateListener(listener);
	}
	
	public void setExecutor(Executor executor) {
		m_support.setExecutor(executor);
	}
	
	@Override
	public String toString() {
		return "ContextVariable[" + m_support.getValueInfo().value + "]";
	}
	
	public final void notifyValueUpdated(ValueInfo<T> value) {
		m_support.notifyValueUpdated(value);
	}
	
	public final Logger getLogger() {
		return m_support.getLogger();
	}
	
	public final void setLogger(Logger logger) {
		m_support.setLogger(logger);
	}
	
	class AopListener extends AsyncOperationFinishListener<Void> {
		private final T m_result;
		
		private AopListener(T result) {
			m_result = result;
		}
		
		@Override @Oneway
		public void onAsyncOperationFinished(AsyncOperation<Void> aop, AsyncOperationState state) {
			switch ( state ) {
				case COMPLETED:
					m_support.notifyValueUpdated(m_result);
				case FAILED:
				case CANCELLED:
					m_lock.lock();
					try {
						m_updaters.remove(aop);
					}
					finally {
						m_lock.unlock();
					}
					break;
			}
		}
	}
}
