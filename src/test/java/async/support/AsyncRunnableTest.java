package async.support;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import async.AsyncOperation;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncRunnableTest {
	private boolean m_runnableCalled;
	
	@Before
	public void setUp() {
		m_runnableCalled = false;
	}
	
	@Test(timeout=100)
	public void testBasic() throws Exception {
		AsyncOperation<Void> aop = AsyncUtils.runAsync(() -> {
			m_runnableCalled = true;
		});
		
		Assert.assertTrue(aop.isNotStarted());
		
		aop.start();
		aop.waitForFinished();
		Assert.assertTrue(aop.isCompleted());
		Assert.assertTrue(m_runnableCalled);
	}
//	
//	@Test(expected = IllegalStateException.class)
//	public void testRestart() throws Exception {
//		AsyncOperation<Void> aop = new AsyncOpImpl();
//		
//		aop.start();
//		aop.waitForFinished();
//		
//		aop.start();
//		aop.waitForStarted();
//	}
//	
//	@Test
//	public void testFailBeforeStart() throws Exception {
//		AsyncOperation<Void> aop = new AsyncOpImpl2();
//		
//		aop.start();
//		aop.waitForStarted();
//		Assert.assertEquals(AsyncOperationState.FAILED, aop.getState());
//	}
//	
//	@Test
//	public void testFailBeforeStart2() throws Exception {
//		AsyncOperation<Void> aop = new AsyncOpImpl3();
//		
//		aop.start();
//		aop.waitForStarted();
//		Assert.assertEquals(AsyncOperationState.FAILED, aop.getState());
//	}
//	
//	@Test
//	public void testFailAfterStart() throws Exception {
//		AsyncOperation<Void> aop = new AsyncOpImpl4();
//		
//		aop.start();
//		aop.waitForStarted();
//		Assert.assertEquals(AsyncOperationState.RUNNING, aop.getState());
//		
//		aop.waitForFinished();
//		Assert.assertEquals(AsyncOperationState.FAILED, aop.getState());
//		Assert.assertTrue(aop.getFailureCause() instanceof AssertionError);
//	}
//	
//	@Test
//	public void testCancel() throws Exception {
//		AsyncOperation<Void> aop = new AsyncOpImpl();
//		
//		m_cancelCalled = false;
//		
//		aop.start();
//		Thread.sleep(400);
//		aop.cancel();
//		aop.waitForFinished();
//		Assert.assertEquals(AsyncOperationState.CANCELLED, aop.getState());
//		Assert.assertEquals(true, m_cancelCalled);
//	}
//	
//	@Test
//	public void testCancelAfterFinish() throws Exception {
//		AsyncOperation<Void> aop = new AsyncOpImpl();
//		
//		aop.start();
//		aop.waitForFinished();
//		aop.cancel();
//		
//		Assert.assertTrue(aop.isFinished());
//	}
//	
//	@Test
//	public void testCancelBeforeStart() throws Exception {
//		AsyncOperation<Void> aop = new AsyncOpImpl();
//
//		long ts0 = System.currentTimeMillis();
//		aop.start();
//		Assert.assertEquals(BEFORE_NOTI_START, m_providerState.get());
//		
//		aop.cancel();
//		Assert.assertEquals(AsyncOperationState.CANCELLED, aop.getState());
//		long ts1 = System.currentTimeMillis();
//		
//		Assert.assertTrue(ts1-ts0 < 100);
//		Assert.assertEquals(false, m_cancelCalled);
//		
//		Thread.sleep(500);
//		Assert.assertEquals(END, m_providerState.get());
//	}
//	
//	class AsyncOpImpl extends AbstractAsyncOperation<Void> {
//		@Override
//		protected void startOperation() throws Throwable {
//			m_providerState.set(BEFORE_NOTI_START);
//			
//			Utilities.runAsync(Errors.toRunnableIE(()-> {
//				Thread.sleep(200);
//				AsyncOperationState state = notifyOperationStarted();
//				m_providerState.set(AFTER_NOTI_START);
//				
//				if ( state == AsyncOperationState.RUNNING ) {
//					Thread.sleep(300);
//					notifyOperationCompleted(null);
//					m_providerState.set(AFTER_NOTI_FINISH);
//				}
//				m_providerState.set(END);
//			}));
//		}
//		@Override protected void stopOperation() {
//			m_cancelCalled = true;
//		}
//	}
//	
//	static class AsyncOpImpl2 extends AbstractAsyncOperation<Void> {
//		@Override
//		protected void startOperation() throws Throwable {
//			throw new AssertionError();
//		}
//		@Override protected void stopOperation() { }
//	}
//	
//	static class AsyncOpImpl3 extends AbstractAsyncOperation<Void> {
//		@Override
//		protected void startOperation() throws Throwable {
//			Utilities.runAsync(Errors.toRunnableIE(()-> {
//				Thread.sleep(100);
//				notifyOperationFailed(new AssertionError());
//			}));
//		}
//		@Override protected void stopOperation() { }
//	}
//	
//	static class AsyncOpImpl4 extends AbstractAsyncOperation<Void> {
//		@Override
//		protected void startOperation() throws Throwable {
//			Utilities.runAsync(Errors.toRunnableIE(()-> {
//				notifyOperationStarted();
//				Thread.sleep(200);
//				notifyOperationFailed(new AssertionError());
//			}));
//		}
//		@Override protected void stopOperation() { }
//	}
}
