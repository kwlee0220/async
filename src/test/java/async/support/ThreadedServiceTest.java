package async.support;

import org.junit.Assert;
import org.junit.Test;

import async.ServiceState;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ThreadedServiceTest {
	@Test
	public void test0() throws Exception {
		ThreadedService svc = ThreadedService.from(cb -> Thread.sleep(500));
		
		long ts0 = System.currentTimeMillis();
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());
		
		svc.start();
		Assert.assertEquals(ServiceState.RUNNING, svc.getState());
		
		svc.waitForFinished();
		long ts1 = System.currentTimeMillis();
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());
		Assert.assertTrue(ts1 -ts0 >= 500);
	}
	
	@Test
	public void testManualStartNotification() {
		ThreadedService svc = ThreadedService.from(cb -> {
			Thread.sleep(200);
			cb.notifyStarted();
			
			Thread.sleep(300);
		});
		svc.setManualStartNotification(true);
		
		long ts0 = System.currentTimeMillis();
		try {
			Assert.assertEquals(ServiceState.STOPPED, svc.getState());
			
			svc.start();
			long ts1 = System.currentTimeMillis();
			Assert.assertEquals(ServiceState.RUNNING, svc.getState());
			Assert.assertTrue(ts1 -ts0 >= 200);
			
			svc.waitForFinished();
			long ts2 = System.currentTimeMillis();
			Assert.assertEquals(ServiceState.STOPPED, svc.getState());
			Assert.assertTrue(ts2 -ts0 >= 500);
		}
		catch ( Exception e ) {
		}
	}
	
	@Test
	public void testFailBeforeStart() throws Exception {
		ThreadedService svc = ThreadedService.from(cb -> {
			throw new IllegalStateException("before start");
		});
		svc.setManualStartNotification(true);
		
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());
		
		try {
			svc.start();
			Assert.fail("should have thrown an exception");
		}
		catch ( Throwable e ) {
			Assert.assertEquals(IllegalStateException.class, e.getClass());
			Assert.assertEquals("before start", e.getMessage());
			Assert.assertEquals(ServiceState.FAILED, svc.getState());
		}
	}
	
	@Test
	public void testFailAfterStart() throws Exception {
		ThreadedService svc = ThreadedService.from(cb -> {
			Thread.sleep(200);
			throw new IllegalStateException("after start");
		});
		
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());

		svc.start();
		Assert.assertEquals(ServiceState.RUNNING, svc.getState());
		
		svc.waitForFinished();
		Assert.assertEquals(ServiceState.FAILED, svc.getState());
		Assert.assertEquals(IllegalStateException.class, svc.getFailureCause().getClass());
		Assert.assertEquals("after start", svc.getFailureCause().getMessage());
	}
	
	@Test
	public void testNotifyServiceFailed() throws Exception {
		ThreadedService svc = ThreadedService.from(cb -> {
			Thread.sleep(200);
			throw new IllegalStateException("after start");
		});
		
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());
		svc.notifyServiceFailed(new IllegalArgumentException());
		Assert.assertEquals(ServiceState.FAILED, svc.getState());

//		svc.start();
//		Assert.assertEquals(ServiceState.RUNNING, svc.getState());
//		
//		svc.waitForFinished();
//		Assert.assertEquals(ServiceState.FAILED, svc.getState());
//		Assert.assertEquals(IllegalStateException.class, svc.getFailureCause().getClass());
//		Assert.assertEquals("after start", svc.getFailureCause().getMessage());
	}
}
