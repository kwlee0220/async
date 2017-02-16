package async.support;

import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class AsyncSupplier<T> extends AbstractAsyncClosure<T> {
	private final Supplier<T> m_task;
	
	AsyncSupplier(Supplier<T> task, Runnable canceler) {
		super(canceler);
		
		m_task = task;
	}

	@Override
	protected T runClosure() throws Exception {
		return m_task.get();
	}
}
