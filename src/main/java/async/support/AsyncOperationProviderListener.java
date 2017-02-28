package async.support;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncOperationProviderListener<T> {
	public void notifyOperationStarted();
	public void notifyOperationCompleted(T result);
	public void notifyOperationCancelled();
	public void notifyOperationFailed(Throwable cause);
}
