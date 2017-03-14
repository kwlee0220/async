package async.optor;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import async.AsyncOperation;
import async.support.AbstractAsyncOperation;




/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class NopAsyncOperation extends AbstractAsyncOperation<Void> implements AsyncOperation<Void> {
	static final Logger s_logger = LoggerFactory.getLogger("AOP.NOP");
	
	public NopAsyncOperation(Executor executor) {
		super(executor);
	}

	@Override
	protected void startOperation() throws Throwable {
		try {
			super.notifyOperationStarted();
		}
		catch ( Exception ignored ) { }
		
		super.notifyOperationCompleted(null);
	}

	@Override
	protected void stopOperation() {
		super.notifyOperationCancelled();
	}
	
	public String toString() {
		return "NOP";
	}
}