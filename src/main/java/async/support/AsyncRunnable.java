package async.support;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class AsyncRunnable extends AbstractAsyncClosure<Void> {
	private final Runnable m_task;
	
	AsyncRunnable(Runnable task, Runnable canceler) {
		super(canceler);
		
		m_task = task;
	}

	@Override
	protected Void runClosure() throws Exception {
		m_task.run();
		return null;
	}
}
