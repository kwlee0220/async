package async.support;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import async.Service;
import async.ServiceState;
import async.optor.CompositeService;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CompositeServiceTest {
	@Test
	public void test0() throws Exception {
		Service[] comps = new Service[]{new ServiceImpl(), new ServiceImpl(), new ServiceImpl()};
		Service svc = new CompositeService(comps);
		
		svc.start();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isRunning));
		Assert.assertEquals(ServiceState.RUNNING, svc.getState());
		
		svc.stop();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isStopped));
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());
	}
	
	@Test
	public void test1() throws Exception {
		Service[] comps = new Service[]{new ServiceImpl(), new StartFailService()};
		Service svc = new CompositeService(comps);
		
		try {
			svc.start();
			Assert.fail("Should have thrown an IllegalArgumentException");
		}
		catch ( IllegalArgumentException e ) { }
		Assert.assertTrue(comps[0].isStopped());
		Assert.assertTrue(comps[1].isFailed());
		Assert.assertTrue(svc.isFailed());
	}
	
	@Test
	public void test2() throws Exception {
		AbstractService[] comps = new AbstractService[]{new ServiceImpl(), new ServiceImpl()};
		Service svc = new CompositeService(comps);

		svc.start();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isRunning));
		Assert.assertTrue(svc.isRunning());
		
		comps[0].notifyServiceFailed(new IllegalArgumentException("error"));
		Assert.assertTrue(comps[0].isFailed());
		Thread.sleep(100);
		Assert.assertTrue(svc.isFailed());
		Assert.assertEquals(IllegalArgumentException.class, svc.getFailureCause().getClass());
	}
	
	@Test
	public void test3() throws Exception {
		AbstractService[] comps = new AbstractService[]{new ServiceImpl(), new ServiceImpl()};
		Service svc = new CompositeService(comps);

		svc.start();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isRunning));
		Assert.assertTrue(svc.isRunning());
		
		comps[0].notifyServiceInterrupted();
		Assert.assertTrue(comps[0].isStopped());
		Thread.sleep(100);
		Assert.assertTrue(svc.isStopped());
		Assert.assertTrue(comps[1].isStopped());
	}
	
	class ServiceImpl extends AbstractService {
		@Override protected void startService() throws Exception { }
		@Override protected void stopService() throws Exception { }
	}
	
	class StartFailService extends AbstractService {
		@Override protected void startService() throws Exception {
			throw new IllegalArgumentException("error");
		}
		@Override protected void stopService() throws Exception { }
	}
	
	class RuntimeFailService extends AbstractService {
		@Override protected void startService() throws Exception {
			Utilities.runCheckedAsync(()->{
				Thread.sleep(100);
				notifyServiceFailed(new IllegalArgumentException("error"));
			});
		}
		@Override protected void stopService() throws Exception { }
	}
}
