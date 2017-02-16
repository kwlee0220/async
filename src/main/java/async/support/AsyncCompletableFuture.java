package async.support;

import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncCompletableFuture<T> extends AbstractAsyncOperation<T> {
	private CompletableFuture<T> m_future;
	
	public static <T> AsyncCompletableFuture<T> get(CompletableFuture<T> future) {
		AsyncCompletableFuture<T> async = new AsyncCompletableFuture<>(future);
		async.start();
		return async;
	}
	
	public AsyncCompletableFuture(CompletableFuture<T> future) {
		m_future = future;
	}

	@Override
	protected void startOperation() throws Throwable {
		m_future.thenAccept((r) -> {
			try {
				this.waitForStarted();
			}
			catch ( InterruptedException ignored ) { }
			this.notifyOperationCompleted(r);
		});
		this.notifyOperationStarted();
	}

	@Override
	protected void cancelOperation() {
		m_future.cancel(true);
		this.notifyOperationCancelled();
	}
}
