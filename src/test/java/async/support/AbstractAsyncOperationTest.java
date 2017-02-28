package async.support;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import async.AsyncOperation;
import async.AsyncOperationState;
import edu.emory.mathcs.backport.java.util.concurrent.atomic.AtomicInteger;
import utils.Errors;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AbstractAsyncOperationTest {
	private static final int NOT_STARTED = 0;
	private static final int BEFORE_NOTI_START = 1;
	private static final int AFTER_NOTI_START = 2;
	private static final int AFTER_NOTI_FINISH = 3;
	private static final int END = 4;
	private final AtomicInteger m_providerState = new AtomicInteger();
	private boolean m_cancelCalled;
	
	@Before
	public void setUp() {
		m_providerState.set(NOT_STARTED);
		m_cancelCalled = false;
	}
	
	@Test(timeout=800)
	public void testBasic() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl();
		
		long ts0 = System.currentTimeMillis();
		Assert.assertEquals(AsyncOperationState.NOT_STARTED, aop.getState());
		
		aop.start();
		Assert.assertEquals(AsyncOperationState.NOT_STARTED, aop.getState());
		Assert.assertEquals(BEFORE_NOTI_START, m_providerState.get());
		aop.waitForStarted();
		
		long ts1 = System.currentTimeMillis();
		Assert.assertEquals(AsyncOperationState.RUNNING, aop.getState());
		Assert.assertEquals(AFTER_NOTI_START, m_providerState.get());
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
	
	@Test
	public void testCancelAfterFinish() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl();
		
		aop.start();
		aop.waitForFinished();
		aop.cancel();
		
		Assert.assertTrue(aop.isFinished());
	}
	
	@Test
	public void testCancelBeforeStart() throws Exception {
		AsyncOperation<Void> aop = new AsyncOpImpl();

		long ts0 = System.currentTimeMillis();
		aop.start();
		Assert.assertEquals(BEFORE_NOTI_START, m_providerState.get());
		
		aop.cancel();
		Assert.assertEquals(AsyncOperationState.CANCELLED, aop.getState());
		long ts1 = System.currentTimeMillis();
		
		Assert.assertTrue(ts1-ts0 < 100);
		Assert.assertEquals(false, m_cancelCalled);
		
		Thread.sleep(500);
		Assert.assertEquals(END, m_providerState.get());
	}
	
	class AsyncOpImpl extends AbstractAsyncOperation<Void> {
		@Override
		protected void startOperation() throws Throwable {
			m_providerState.set(BEFORE_NOTI_START);
			
			Utilities.runAsync(Errors.toRunnableIE(()-> {
				Thread.sleep(200);
				AsyncOperationState state = notifyOperationStarted();
				m_providerState.set(AFTER_NOTI_START);
				
				if ( state == AsyncOperationState.RUNNING ) {
					Thread.sleep(300);
					notifyOperationCompleted(null);
					m_providerState.set(AFTER_NOTI_FINISH);
				}
				m_providerState.set(END);
			}));
		}
		@Override protected void stopOperation() {
			m_cancelCalled = true;
		}
	}
	
	static class AsyncOpImpl2 extends AbstractAsyncOperation<Void> {
		@Override
		protected void startOperation() throws Throwable {
			throw new AssertionError();
		}
		@Override protected void stopOperation() { }
	}
	
	static class AsyncOpImpl3 extends AbstractAsyncOperation<Void> {
		@Override
		protected void startOperation() throws Throwable {
			Utilities.runAsync(Errors.toRunnableIE(()-> {
				Thread.sleep(100);
				notifyOperationFailed(new AssertionError());
			}));
		}
		@Override protected void stopOperation() { }
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
		@Override protected void stopOperation() { }
	}
}
