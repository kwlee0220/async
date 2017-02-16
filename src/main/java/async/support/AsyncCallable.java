package async.support;

import java.util.concurrent.Callable;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class AsyncCallable<T> extends AbstractAsyncClosure<T> {
	private final Callable<T> m_task;
	
	AsyncCallable(Callable<T> task, Runnable canceler) {
		super(canceler);
		
		m_task = task;
	}

	@Override
	protected T runClosure() throws Exception {
		return m_task.call();
	}
}
