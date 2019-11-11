package async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.annotation.concurrent.GuardedBy;


/**
 * <code>SingleWorkerQueue</code>는 단일 작업자 실행 큐 클래스를 정의한다.
 * <p>
 * 단일 작업자 큐는 요청된 다수의 작업을 요청 순서대로 하나의 작업자에 의해서
 * 처리시키는 기능을 수행한다. 즉 한번에 오직 하나의 작업만을 요청된 차례대로
 * 처리하는 기능을 제공한다.
 * 
 * @author Kang-Woo Lee
 */
public class SingleWorkerQueue {
	private final Executor m_executor;
	@GuardedBy("this") private final List<QueueEntry> m_taskQueue;
	private final boolean m_reserveThread;
	
	/**
	 * 단일 작업자 큐를 생성한다.
	 * 
	 * @param executor	작업 수행에 사용할 {@link Executor} 객체.
	 * @param reserveThread	작업 처리시 앞서 사용했던 쓰레드의 재사용 여부.
	 * 					<code>true</code>인 경우는 재사용을 의미하고, <code>false</code>인
	 * 					경우는 매 작업마다 {@link Executor}에 실행을 요청한다.
	 */
	public SingleWorkerQueue(Executor executor, boolean reserveThread) {
		m_executor = executor;
		m_taskQueue = new ArrayList<QueueEntry>();
		m_reserveThread = reserveThread;
	}
	
	/**
	 * 본 단일 작업자 큐에서 사용하는 {@link Executor} 객체를 반환한다.
	 * 
	 * @return	{@link Executor} 객체.
	 */
	public Executor getExecutor() {
		return m_executor;
	}
	
	/**
	 * 주어진 작업 수행을 요청한다.
	 * <p>
	 * 본 작업 큐가 비어 있는 경우는 바로 작업을 수행하지만, 다른 작업이 대기 중인 경우는
	 * 큐에 넣고 차례를 기다리도록 한다.
	 * 
	 * @param task	수행시킬 작업.
	 */
	public synchronized void enqueue(Runnable task) {
		QueueEntry wrapped = new QueueEntry(new FutureTask<Integer>(task, 1));
		
		m_taskQueue.add(wrapped);
		if ( m_taskQueue.size() == 1 ) {
			m_executor.execute(wrapped);
		}
	}
	
	/**
	 * 주어진 작업 수행을 요청한다.
	 * <p>
	 * 본 작업 큐가 비어 있는 경우는 바로 작업을 수행하지만, 다른 작업이 대기 중인 경우는
	 * 큐에 넣고 차례를 기다리도록 한다.
	 * 
	 * @param task	수행시킬 작업.
	 * @return	작업 수행 상태를 확인할 수 있는 Future 객체.
	 */
	public synchronized <T> Future<T> enqueue(Callable<T> callable) {
		FutureTask<T> future = new FutureTask<T>(callable);
		QueueEntry wrapped = new QueueEntry(future);
		
		m_taskQueue.add(wrapped);
		if ( m_taskQueue.size() == 1 ) {
			m_executor.execute(wrapped);
		}
		
		return future;
	}
	
	/**
	 * 작업 큐에 수행 차례를 대기하는 모든 작업을 삭제한다.
	 */
	public synchronized void cancelAllPendings() {
		int size = m_taskQueue.size();
		for ( int i = 1; i < size; ++i ) {
			m_taskQueue.get(i).m_cancelled = true;
		}
	}
	
	private class QueueEntry implements Runnable {
		private FutureTask<?> m_task;
		private boolean m_cancelled;
		
		private QueueEntry(FutureTask<?> task) {
			m_task = task;
			m_cancelled = false;
		}

		public void run() {
			while ( true ) {
				try {
					m_task.run();
				}
				catch ( Throwable ignored ) { }
				
				synchronized ( SingleWorkerQueue.this ) {
					m_taskQueue.remove(0);
					
					// task queue에서 cancel되지 않는 가장 오래된 작업 엔트리를 구한다.
					while ( m_taskQueue.size() > 0 ) {
						if ( m_taskQueue.get(0).m_cancelled ) {
							m_taskQueue.remove(0);
						}
						else {
							break;
						}
					}
					
					// m_taskQueue의 크기가 0이면 더 이상 수행시킬 작업 엔트리가 없거나
					// m_taskQueue의 첫번째 작업 엔트리를 수행시킨다.
					// 이때 'm_reserveThread'가 true인 경우는 쓰레드의 생성/삭제의 부하를
					// 줄이기 위해 본 쓰레드에서 바로 수행시킨다. 만일 false인 경우에는
					// m_executor에 작업 수행을 요청한다.
					
					if ( m_taskQueue.size() > 0 ) {
						FutureTask<?> task = m_taskQueue.get(0).m_task;
						if ( m_reserveThread ) {
							m_task = task;
						}
						else {
							m_executor.execute(task);
							return;
						}
					}
					else {
						
						return;
					}
				}
			}
		}
	}
}
