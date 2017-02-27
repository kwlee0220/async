package async.support;

import org.junit.Assert;
import org.junit.Test;

import async.AsyncOperation;
import async.ServiceState;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ThreadedAsyncOperationTest {
	@Test
	public void test0() throws Exception {
		AsyncOperation<Void> aop = new ThreadedAsyncOperation<>((cb)-> {
			Thread.sleep(500);
			return null;
		});
		
		long ts0 = System.currentTimeMillis();
		Assert.assertEquals(ServiceState.STOPPED, aop.getState());
		
		aop.start();
		Assert.assertEquals(ServiceState.RUNNING, aop.getState());
		
		aop.waitForFinished();
		long ts1 = System.currentTimeMillis();
		Assert.assertEquals(ServiceState.STOPPED, aop.getState());
		Assert.assertTrue(ts1 -ts0 >= 500);
	}
}
