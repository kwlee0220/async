package async.optor;

import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.support.AbstractAsyncOperation;
import net.jcip.annotations.GuardedBy;
import utils.ExceptionUtils;



/**
 * <code>SequentialAsyncOperation</code>은 복수개의 {@link AsyncOperation}을 지정된
 * 순서대로 수행시키는 비동기 연산 클래스를 정의한다.
 * <p>
 * 순차 비동기 연산은 소속 비동기 연산을 등록된 순서대로 차례대로 수행시킨다. 전체 순차 비동기 연산의
 * 수행 결과는 마지막 원소 비동기 연산의 결과로 정의된다.
 * 소속 비동기 연산 수행 중 오류 또는 취소가 발생되면 전체 순차 연산이 오류가 발생되거나 취소된 것으로
 * 간주한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SequentialAsyncOperation extends AbstractAsyncOperation implements AsyncOperation {
	private static final Logger s_logger = Logger.getLogger("AOP.SEQ");
	
	private final Object m_seqMutex = new Object();
	private final AsyncOperation[] m_elements;
	@GuardedBy("m_seqMutex") private int m_cursor = -1;
	@GuardedBy("m_seqMutex") private boolean m_stopRequested = false;
	private final Listener m_listener = new Listener();
	
	/**
	 * 순차 수행 비동기 연산 객체를 생성한다.
	 * <p>
	 * 수행되는 순서는 {@link elements} 배열의 순서로 정해진다.
	 * 
	 * @param executor	순차 수행에 사용될 쓰레드 풀 객체.
	 * @param elements	순차 수행될 비동기 연산 객체 배열.
	 * @throws IllegalArgumentException	<code>elements</code>가 <code>null</code>이거나
	 * 									길이가 0인 경우.
	 */
	public SequentialAsyncOperation(Executor executor, AsyncOperation... elements) {
		super(executor);
		
		if ( elements == null ) {
			throw new IllegalArgumentException("element aop was null");
		}
		else if ( elements.length == 0 ) {
			throw new IllegalArgumentException("zero element");
		}
		
		m_elements = elements;
	}
	
	/**
	 * 순차 수행 비동기 연산 객체를 생성한다.
	 * <p>
	 * 수행되는 순서는 {@link elements} 배열의 순서로 정해진다.
	 * 
	 * @param elements	순차 수행될 비동기 연산 객체 배열.
	 * @throws IllegalArgumentException	<code>elements</code>가 <code>null</code>이거나
	 * 									길이가 0인 경우.
	 */
	public SequentialAsyncOperation(AsyncOperation... elements) {
		if ( elements == null ) {
			throw new IllegalArgumentException("element aop was null");
		}
		else if ( elements.length == 0 ) {
			throw new IllegalArgumentException("zero element");
		}
		
		m_elements = elements;
	}
	
	public AsyncOperation[] getElementOperations() {
		return m_elements;
	}
	
	/**
	 * 현재 수행 중이거나 수행 대기 중인 비동기 연산의 순서를 반환한다.
	 * <p>
	 * 반환되는 값은 0부터 시작된 번호를 반환하고,
	 * 아직 시작 이전인 경우는 -1을 반환하고, 종료된 경우는 비동기 연산 배열의 길이를 반환한다.
	 * 
	 * @return	-1과 n 사이의 수.
	 */
	public int getCurrentOperationIndex() {
		synchronized ( m_seqMutex ) {
			return m_cursor;
		}
	}
	
	public String toString() {
		synchronized ( m_seqMutex ) {
			return "Sequential[current=" + m_cursor + ", size=" + m_elements.length + "]";
		}
	}

	@Override
	protected void startOperation() {
		synchronized ( m_seqMutex ) {
			m_cursor = 0;
			m_elements[0].addStateChangeListener(m_listener);
			m_elements[0].start();
		}
		
		SequentialAsyncOperation.this.notifyOperationStarted();
	}

	@Override
	protected void stopOperation() {
		synchronized ( m_seqMutex ) {
			m_stopRequested = true;
			if ( m_cursor >= 0 && m_cursor < m_elements.length ) {
				m_elements[m_cursor].cancel();
			}
		}
	}
	
	class Listener implements AsyncOperationListener {
		@Override public void onAsyncOperationStarted(AsyncOperation aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation aop, AsyncOperationState state) {
			final SequentialAsyncOperation _this = SequentialAsyncOperation.this;
			
			if ( s_logger.isDebugEnabled() ) {
				s_logger.debug("finished element: " + _this);
			}
			
			switch ( state ) {
				case COMPLETED:
					synchronized ( m_seqMutex ) {
						if ( ++m_cursor < m_elements.length ) {
							// 취소 요청을 했던 소속 비동기 연산이 완료될 수도 있기 때문에
							// 완료 통보가 도착해도 취소 중인지를 확인하여야 한다.
							//
							if ( m_stopRequested ) {
								_this.notifyOperationCancelled();
							}
							else {
								m_elements[m_cursor].addStateChangeListener(m_listener);
								m_elements[m_cursor].start();
							}
						}
						else {
							Object result = null;
							try {
								result = aop.getResult();
							}
							catch ( Throwable ignored ) {
								s_logger.warn("fails to get SequentialAsyncOperation result, cause="
												+ ExceptionUtils.unwrapThrowable(ignored));
							}
							
							_this.notifyOperationCompleted(result);
						}
					}
					break;
				case CANCELLED:
					_this.notifyOperationCancelled();
					break;
				case FAILED:
					Throwable cause = null;
					try {
						cause = aop.getFailureCause();
					}
					catch ( Throwable ignored ) {
						s_logger.warn("fails to get SequentialAsyncOperation fault cause, cause="
										+ ExceptionUtils.unwrapThrowable(ignored));
					}
					_this.notifyOperationFailed(cause);
					break;
			}
		}
	}
}