package async.optor;

import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

import async.AsyncOperation;
import async.support.AbstractAsyncOperation;




/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class NopAsyncOperation extends AbstractAsyncOperation implements AsyncOperation {
	static final Logger s_logger = Logger.getLogger("AOP.NOP");
	
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
	protected void cancelOperation() {
		super.notifyOperationCancelled();
	}
	
	public String toString() {
		return "NOP";
	}
}