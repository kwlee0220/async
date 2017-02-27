package async.support;

import org.junit.Assert;
import org.junit.Test;

import async.AsyncOperation;
import async.AsyncOperationState;
import utils.Errors;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractAsyncOperationTest {
	private boolean m_startCalled;
	private boolean m_cancelCalled = false;
	
	@Test(timeout=800)
	public void testBasic() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl();
		
		m_startCalled = false;
		
		long ts0 = System.currentTimeMillis();
		Assert.assertEquals(AsyncOperationState.NOT_STARTED, aop.getState());
		
		aop.start();
		Assert.assertEquals(AsyncOperationState.NOT_STARTED, aop.getState());
		Assert.assertEquals(false, m_startCalled);
		aop.waitForStarted();
		long ts1 = System.currentTimeMillis();
		Assert.assertEquals(AsyncOperationState.RUNNING, aop.getState());
		Assert.assertEquals(true, m_startCalled);
		Assert.assertTrue(ts1 -ts0 >= 200);
		
		aop.waitForFinished();
		long ts2 = System.currentTimeMillis();
		Assert.assertEquals(AsyncOperationState.COMPLETED, aop.getState());
		Assert.assertTrue(ts2 -ts0 >= 500);
	}
	
	@Test(expected = IllegalStateException.class)
	public void testRestart() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl();
		
		aop.start();
		aop.waitForFinished();
		
		aop.start();
		aop.waitForStarted();
	}
	
	@Test
	public void testFailBeforeStart() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl2();
		
		aop.start();
		aop.waitForStarted();
		Assert.assertEquals(AsyncOperationState.FAILED, aop.getState());
	}
	
	@Test
	public void testFailBeforeStart2() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl3();
		
		aop.start();
		aop.waitForStarted();
		Assert.assertEquals(AsyncOperationState.FAILED, aop.getState());
	}
	
	@Test
	public void testFailAfterStart() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl4();
		
		aop.start();
		aop.waitForStarted();
		Assert.assertEquals(AsyncOperationState.RUNNING, aop.getState());
		
		aop.waitForFinished();
		Assert.assertEquals(AsyncOperationState.FAILED, aop.getState());
		Assert.assertTrue(aop.getFailureCause() instanceof AssertionError);
	}
	
	@Test
	public void testCancel() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl();
		
		m_cancelCalled = false;
		
		aop.start();
		Thread.sleep(400);
		aop.cancel();
		aop.waitForFinished();
		Assert.assertEquals(AsyncOperationState.CANCELLED, aop.getState());
		Assert.assertEquals(true, m_cancelCalled);
	}
	
	class AsyncOpImpl extends AbstractAsyncOperation<Void> {
		@Override
		protected void startOperation() throws Throwable {
			Utilities.runAsync(Errors.toRunnableIE(()-> {
				Thread.sleep(200);
				m_startCalled = true;
				notifyOperationStarted();
				
				Thread.sleep(300);
				notifyOperationCompleted(null);
			}));
		}
		@Override protected void cancelOperation() {
			m_cancelCalled = true;
		}
	}
	
	static class AsyncOpImpl2 extends AbstractAsyncOperation<Void> {
		@Override
		protected void startOperation() throws Throwable {
			throw new AssertionError();
		}
		@Override protected void cancelOperation() { }
	}
	
	static class AsyncOpImpl3 extends AbstractAsyncOperation<Void> {
		@Override
		protected void startOperation() throws Throwable {
			Utilities.runAsync(Errors.toRunnableIE(()-> {
				Thread.sleep(100);
				notifyOperationFailed(new AssertionError());
			}));
		}
		@Override protected void cancelOperation() { }
	}
	
	static class AsyncOpImpl4 extends AbstractAsyncOperation<Void> {
		@Override
		protected void startOperation() throws Throwable {
			Utilities.runAsync(Errors.toRunnableIE(()-> {
				notifyOperationStarted();
				Thread.sleep(200);
				notifyOperationFailed(new AssertionError());
			}));
		}
		@Override protected void cancelOperation() { }
	}
}
