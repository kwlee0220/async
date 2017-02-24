package async.support;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import async.Service;
import async.ServiceState;
import utils.Utilities;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ConcurrentServiceTest {
	@Test
	public void test0() throws Exception {
		Service[] comps = new Service[]{new ServiceImpl(), new ServiceImpl(), new ServiceImpl()};
		Service svc = AsyncUtils.concurrent(comps);
		
		svc.start();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isRunning));
		Assert.assertEquals(ServiceState.RUNNING, svc.getState());
		
		svc.stop();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isStopped));
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());
	}
	@Test
	public void test1() throws Exception {
		Service[] comps = new Service[]{new ServiceImpl(), new ServiceImpl2()};
		Service svc = AsyncUtils.concurrent(comps);
		
		svc.start();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isRunning));
		Assert.assertEquals(ServiceState.RUNNING, svc.getState());
		
		Thread.sleep(150);
		Assert.assertEquals(ServiceState.RUNNING, svc.getState());
		Assert.assertEquals(ServiceState.FAILED, comps[1].getState());
		
		svc.stop();
		Assert.assertTrue(Arrays.stream(comps).allMatch(Service::isFinished));
		Assert.assertEquals(ServiceState.STOPPED, svc.getState());
	}
	
	class ServiceImpl extends AbstractService {
		@Override protected void startService() throws Exception { }
		@Override protected void stopService() throws Exception { }
	}
	
	class ServiceImpl2 extends AbstractService {
		@Override protected void startService() throws Exception {
			Utilities.runCheckedAsync(()->{
				Thread.sleep(100);
				notifyServiceFailed(new IllegalArgumentException("error"));
			});
		}
		@Override protected void stopService() throws Exception { }
	}
}
