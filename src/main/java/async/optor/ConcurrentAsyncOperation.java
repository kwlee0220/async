package async.optor;

import java.util.Collection;
import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import async.AsyncOperation;
import async.AsyncOperationListener;
import async.AsyncOperationState;
import async.support.AbstractAsyncOperation;
import net.jcip.annotations.GuardedBy;
import utils.thread.ExecutorAware;


/**
 * <code>ConcurrentAsyncOperation</code>은 주어진 복수개의 {@link AsyncOperation}을 동시에
 * 수행시키는 비동기 연산 클래스를 정의한다.
 * <p>
 * 소속 비동기 연산 수행 중 오류 또는 취소가 발생되는 것은 모두 무시되고 종료된 것으로 간주된다.
 * 본 비동기 연산은 결과 값으로 <code>null</code>을 반환한다. 
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ConcurrentAsyncOperation extends AbstractAsyncOperation
												implements AsyncOperation {
	private static final Logger s_logger = Logger.getLogger("AOP.CONCUR");
	
	private final AsyncOperation<?>[] m_elements;
	private final int m_noOfElmFinishToCompletion;
	@GuardedBy("m_aopLock") private int m_noOfFinisheds = 0;
	@GuardedBy("m_aopLock") private boolean m_stopping = false;

	/**
	 * 동시 수행 비동기 연산 객체를 생성한다.
	 * <p>
	 * 모든 소속 비동기 연산이 완료되어야 전체 동시 수행 비동기 연산이 완료된 것으로 간주하지
	 * 않고, 주어진 갯수의 비동기 연산만 완료되어도 전체 동시 수행 비동기 연산이 완료된 것으로
	 * 간주한다.
	 * <br>
	 * 원소 비동기 연산의 중지 또는 실패는 동시 수행 비동기 연산에 영향을 주지 않는다.
	 * 
	 * @param executor	동시 수행에 사용될 쓰레드 풀 객체.
	 * @param noOfElmFinishToCompletion	종료로 간주할 최소 원소 비동기 연산 완료 수.
	 * @param elements	동시 수행될 비동기 연산 객체 배열.
	 * @throws InvalidArgumentException	<code>elements</code>가 <code>null</code>이거나
	 * 									길이가 0인 경우.
	 */
	public ConcurrentAsyncOperation(Executor executor, int noOfElmFinishToCompletion,
									AsyncOperation<?>... elements) {
		super(executor);
		
		if ( elements == null ) {
			throw new IllegalArgumentException("element aop was null");
		}
		else if ( elements.length == 0 ) {
			throw new IllegalArgumentException("zero element");
		}
		else if ( noOfElmFinishToCompletion < 1 || noOfElmFinishToCompletion >= elements.length ) {
			throw new IllegalArgumentException("noOfElmFinishToCompletion="
												+ noOfElmFinishToCompletion);
		}
		
		m_elements = elements;
		m_noOfElmFinishToCompletion = noOfElmFinishToCompletion;
	}
	
	/**
	 * 동시 수행 비동기 연산 객체를 생성한다.
	 * <p>
	 * 모든 소속 비동기 연산이 종료되어야 종료된 것으로 간주한다.
	 * <br>
	 * 원소 비동기 연산의 중지 또는 실패는 동시 수행 비동기 연산에 영향을 주지 않는다.
	 * 
	 * @param executor	동시 수행에 사용될 쓰레드 풀 객체.
	 * @param elements	동시 수행될 비동기 연산 객체 배열.
	 * @throws InvalidArgumentException	<code>elements</code>가 <code>null</code>이거나
	 * 									길이가 0인 경우.
	 */
	public ConcurrentAsyncOperation(Executor executor, AsyncOperation<?>... elements) {
		super(executor);
		
		if ( elements == null ) {
			throw new IllegalArgumentException("element aop was null");
		}
		else if ( elements.length == 0 ) {
			throw new IllegalArgumentException("zero element");
		}
		
		m_elements = elements;
		m_noOfElmFinishToCompletion = elements.length;
	}
	
	public ConcurrentAsyncOperation(AsyncOperation<?>... elements) {
		if ( elements == null ) {
			throw new IllegalArgumentException("element aop was null");
		}
		else if ( elements.length == 0 ) {
			throw new IllegalArgumentException("zero element");
		}
		
		for ( AsyncOperation<?> elm: elements ) {
			if ( elm instanceof ExecutorAware ) {
				setExecutor(((ExecutorAware)elm).getExecutor());
				break;
			}
		}
		
		m_elements = elements;
		m_noOfElmFinishToCompletion = elements.length;
	}
	
	public ConcurrentAsyncOperation(Collection<AsyncOperation<?>> elements) {
		if ( elements == null ) {
			throw new IllegalArgumentException("element aop was null");
		}
		else if ( elements.size() == 0 ) {
			throw new IllegalArgumentException("zero element");
		}
		
		for ( AsyncOperation<?> elm: elements ) {
			if ( elm instanceof ExecutorAware ) {
				setExecutor(((ExecutorAware)elm).getExecutor());
				break;
			}
		}
		
		m_elements = elements.toArray(new AsyncOperation[elements.size()]);
		m_noOfElmFinishToCompletion = m_elements.length;
	}
	
	public String toString() {
		m_aopLock.lock();
		try {
			return "Concurrent[size=" + m_elements.length + ", noOfFinisheds="
					+ m_noOfFinisheds + "]";
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	protected void startOperation() throws Throwable {
		for ( int i =0; i < m_elements.length; ++i ) {
			m_elements[i].addStateChangeListener(m_listener);
		}
		
		m_aopLock.lock();
		try {
			for ( int i =0; i < m_elements.length; ++i ) {
				if ( m_elements[i].getState() == AsyncOperationState.NOT_STARTED ) {
					m_elements[i].start();
				}
			}
			
			super.notifyOperationStarted();
		}
		finally {
			m_aopLock.unlock();
		}
	}

	@Override
	protected void stopOperation() {
		m_aopLock.lock();
		try {
			// 멤버 aop들의 수행을 중단시킨다.
			for ( int i =0; i < m_elements.length; ++i ) {
				try {
					m_elements[i].cancel();
				}
				catch ( Exception ignored ) { }
			}
			
			// stop요청이 들어 온 것만 마크해 놓고
			// 실제 notifyOperationStarted 호출은 모든 원소 Aop가 종료된 후 호출한다.
			m_stopping = true;
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	private AsyncOperationListener m_listener = new AsyncOperationListener() {
		@Override public void onAsyncOperationStarted(AsyncOperation aop) { }

		@Override
		public void onAsyncOperationFinished(AsyncOperation aop, AsyncOperationState state) {
			final ConcurrentAsyncOperation _this = ConcurrentAsyncOperation.this;
			
			boolean stopRemains = false;
			m_aopLock.lock();
			try {
				++m_noOfFinisheds;
				if ( m_noOfFinisheds == m_elements.length ) {
					if ( m_stopping ) {
						_this.notifyOperationCancelled();
					}
					else {
						_this.notifyOperationCompleted(null);
					}
				}
				else if ( m_noOfFinisheds == m_noOfElmFinishToCompletion ) {
					// 남은 원소 Aop를 취소시킨다.
					stopRemains = true;
					
					if ( s_logger.isDebugEnabled() ) {
						s_logger.debug("finishing: " + _this);
					}
				}
			}
			finally {
				m_aopLock.unlock();
			}
			
			if ( stopRemains ) {
				// 아직 수행이 종료되지 않은 멤버 aop들의 수행을 중단시킨다.
				for ( int i =0; i < m_elements.length; ++i ) {
					try {
						m_elements[i].cancel();
					}
					catch ( Exception ignored ) {
						ignored.printStackTrace();
					}
				}
			}
		}
	};
}