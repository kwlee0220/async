package async.optor;

import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.support.AbstractAsyncOperation;
import net.jcip.annotations.GuardedBy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class OnFaultAsyncOperation<T> extends AbstractAsyncOperation<T> implements AsyncOperation<T> {
	static final Logger s_logger = Logger.getLogger("AOP.ON_FAULT");
	
	private final AsyncOperation<T> m_aop;
	private final FaultHandlerFactory<T> m_fact;
	@GuardedBy("this") private AsyncOperation<T> m_faultHandler;
	@GuardedBy("this") private Throwable m_fault;
	
	@FunctionalInterface
	public interface FaultHandlerFactory<T> {
		public AsyncOperation<T> newInstance(Throwable fault);
	}
	
	public OnFaultAsyncOperation(AsyncOperation<T> aop, FaultHandlerFactory<T> handlerFact,
								Executor executor) {
		super(executor);
		
		if ( aop == null ) {
			throw new IllegalArgumentException("target aop was null");
		}
		if ( handlerFact == null ) {
			throw new IllegalArgumentException("fault handler aop was null");
		}
		
		m_aop = aop;
		m_fact = handlerFact;
	}

	@Override
	protected void startOperation() throws Throwable {
		m_aop.addAsyncOperationListener(new Listener());
		m_aop.start();
	}

	@Override
	protected void stopOperation() {
		synchronized ( this ) {
			if ( m_fault != null ) {
				m_faultHandler.cancel();
			}
			else {
				m_aop.cancel();
			}
		}
	}
	
	public String toString() {
		return "OnFault[op=" + m_aop + ", handler=" + m_faultHandler + "]";
	}
	
	class Listener implements AsyncOperationListener<T> {
		@Override public void onAsyncOperationStarted(AsyncOperation<T> aop) {
			OnFaultAsyncOperation.this.notifyOperationStarted();
		}

		@Override
		public void onAsyncOperationFinished(AsyncOperation<T> aop, AsyncOperationState state) {
			final OnFaultAsyncOperation<T> _this = OnFaultAsyncOperation.this;
			
			switch ( state ) {
				case COMPLETED:
					_this.notifyOperationCompleted(aop.getResult());
					break;
				case CANCELLED:
					_this.notifyOperationCancelled();
					break;
				case FAILED:
					Throwable cause = aop.getFailureCause();
					synchronized ( _this ) {
						m_fault = cause;
						m_faultHandler = m_fact.newInstance(m_fault);
					}
					m_faultHandler.start();
					break;
				default:
					throw new RuntimeException();
			}
		}
	}
	
	class FaultListener implements AsyncOperationListener<T> {
		@Override public void onAsyncOperationStarted(AsyncOperation<T> aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation<T> aop, AsyncOperationState state) {
			final OnFaultAsyncOperation<T> _this = OnFaultAsyncOperation.this;
			
			switch ( state ) {
				case COMPLETED:
					_this.notifyOperationCompleted(aop.getResult());
					break;
				case CANCELLED:
				case FAILED:
					_this.notifyOperationFailed(m_fault);
					break;
				default:
					throw new RuntimeException();
			}
		}
	}
}