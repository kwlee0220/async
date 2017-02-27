package async.support;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import async.OperationSchedulerProvider;
import async.OperationStoppedException;
import net.jcip.annotations.GuardedBy;
import utils.Utilities;


/**
 * <code>ThreadedAsyncOperation</code>는 비동기 연산 구현을 지원하는 추상 클래스이다.
 * <p>
 * 일반적으로 동기적으로 수행되는 작업을 비동기 연산으로 구현할 때 유용하게 사용될 수 있는
 * 장점이 있으나, {@link AbstractAsyncOperation}을 상속하는 방법에 비해 구현할 수 있는
 * 비동기 연산에 제약을 받는다.
 * <p>
 * ThreadedAsyncOperation을 상속받는 클래스는 반드시 다음의 두가지 메소드를 재정의하여야 한다.
 * <dl>
 * <dt>{@link #executeOperation()}:
 * <dd>
 * 비동기적으로 수행될 작업을 구현할 메소드로 대상 작업을 동기적으로 수행되는 것을 가정한다.
 * 즉 본 메소드가 호출되면 지정된 작업이 시작되고 메소드의 반환은 일반적으로 해당 작업을
 * 모두 완료될 때까지 대기 후 작업의 결과 값을 반환 값으로 반환되어야 한다.
 * ThreadedAsyncOperation은 executeOperation()이 정상적으로 호출되어 반환되는 경우
 * 이를 지정된 작업이 정상적으로 수행 완료된 것으로 간주하기 때문에, 만일 작업 수행 중
 * 타 쓰레드의 stopOperation() 메소드 호출 또는 기타 다른 이유로 인해
 * <b>작업이 중지되는 경우는 반드시 OperationStoppedException 예외를 발생시켜야 한다</b>.
 * 만일 그렇지 않은 경우는 작업 수행이 정상적으로 종료된 것으로 간주된다.
 * 또한 작업 수행 중 여러가지 원인에 의해 작업 수행이 실패하는 경우는 해당 예외를
 * ExecutionException으로 wrapping하여 발생시키고 반환되어야 한다.
 * 대부분의 경우 비동기 연산의 결과로 특정한 값이 생성되지 않는 경우가 많다.
 * 이 경우는 executeOperation() 메소드는 null을 반환한다.
 * <dt>{@link #cancelOperation()}:
 * <dd>
 * executeOperation() 호출로 수행 중인 작업을 중지시키기 위해 필요한 작업을 구현한다.
 * ThreadedAsyncOperation을 상속하여 구현된 비동기 연산의 수행 중 AsyncOperation.stop()이
 * 호출되는 경우 본 메소드가 호출된다. 본 메소드는 일반적으로 수행 중인 작업을 중지시키기 위한
 * 작업을 수행한다. 본 메소드가 호출되면 executeOperation() 메소드를 호출로 pending 중인
 * 쓰레드는 OperationStoppedException 예외를 받게 되어야 한다.
 * 만일 본 메소드 호출 당시 비동기 작업이 수행 중이지 않은 경우는 호출이 무시된다.
 * 또한 메소드 호출 중 발생되는 모든 예외는 모두 내부적으로 처리되어야 한다. stopOperation()를
 * 통한 작업 중지는 *best-effort* 의미를 갖기 때문에 메소드 호출로 반드시 대상 비동기 작업이
 * 중지되지 않아도 된다.
 * </dl>
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ThreadedAsyncOperation<T> extends AbstractAsyncOperation<T> {
	private volatile Thread m_worker = null;
	private final Operation<T> m_op;
	@GuardedBy("m_aopLock") private boolean m_cancelRequested = false;
	
	@FunctionalInterface
	public static interface Operation<T> {
		public T run(Callback cb) throws OperationStoppedException, Exception;
	}
	
	public ThreadedAsyncOperation(Operation<T> op) {
		m_op = op;
	}
	
	public ThreadedAsyncOperation(Operation<T> op, Executor executor) {
		super(executor);
		
		m_op = op;
	}
	
	public ThreadedAsyncOperation(Operation<T> op, OperationSchedulerProvider scheduler) {
		super(scheduler);
		
		m_op = op;
	}
	
	public final Thread getWorkerThread() {
		return m_worker;
	}

	@Override
	protected final void startOperation() throws Throwable {
		Utilities.runAsync(new ThreadedOperation<>(this), getExecutor());
	}
	
	@Override
	protected void cancelOperation() {
		m_aopLock.lock();
		try {
			m_cancelRequested = true;
			m_worker.interrupt();
			
			while ( m_cancelRequested ) {
				try {
					m_aopCond.await();
				}
				catch ( InterruptedException e ) {
					throw new RuntimeException("canceling thread has been interrupted");
				}
			}
		}
		finally {
			m_aopLock.unlock();
		}
	}
	
	public static class Callback {
		private final ThreadedAsyncOperation<?> m_aop;
		
		Callback(ThreadedAsyncOperation<?> aop) {
			m_aop = aop;
		}
		
		@GuardedBy("m_aopLock")
		public boolean isInterrupted() {
			m_aop.m_aopLock.lock();
			try {
				return m_aop.m_cancelRequested;
			}
			finally {
				m_aop.m_aopLock.unlock();
			}
		}
		
		@GuardedBy("m_aopLock")
		public void notifyInterrupted() {
			m_aop.m_aopLock.lock();
			try {
				m_aop.m_cancelRequested = false;
				m_aop.m_aopCond.signalAll();
			}
			finally {
				m_aop.m_aopLock.unlock();
			}
		}
	}
	
	static class ThreadedOperation<T> implements Runnable {
		private final ThreadedAsyncOperation<T> m_aop;
		
		private ThreadedOperation(ThreadedAsyncOperation<T> aop) {
			m_aop = aop;
		}
		
		public void run() {
			m_aop.m_worker = Thread.currentThread();
			
			try {
				m_aop.notifyOperationStarted();
			}
			catch ( Exception ignored ) { }

			try {
				T result = m_aop.m_op.run(new Callback(m_aop));
				
				m_aop.notifyOperationCompleted(result);
			}
			catch ( OperationStoppedException e ) {
				m_aop.notifyOperationCancelled();
			}
			catch ( ExecutionException e ) {
				m_aop.notifyOperationFailed(e.getCause());
			}
			catch ( Throwable e ) {
				m_aop.getLogger().warn("fails to execute ThreadedAsyncOperation: aoo={}, cause={}",
										m_aop, e);
				e.printStackTrace();
			}
		}
	}
}