package async.support;

import async.AsyncOperation;
import async.AsyncOperationListener;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AsyncOperationFinishListener<T> implements AsyncOperationListener<T> {
	@Override public final void onAsyncOperationStarted(AsyncOperation<T> aop) { }
}
