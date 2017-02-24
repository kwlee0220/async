package async.support;

import org.junit.Assert;
import org.junit.Test;

import async.ServiceState;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SetFailureDependencyTest {
	@Test
	public void test0() throws Exception {
		AbstractService dependee = new LeadServiceImpl();
		AbstractService dependent = new DepServiceImpl();
		AsyncUtils.setFailureDependency(dependee, dependent);
		
		dependee.start();
		dependent.start();
		Assert.assertEquals(ServiceState.RUNNING, dependee.getState());
		Assert.assertEquals(ServiceState.RUNNING, dependent.getState());
		
		dependee.notifyServiceFailed(new IllegalStateException());
		Assert.assertEquals(ServiceState.FAILED, dependee.getState());
		Thread.sleep(300);
		Assert.assertEquals(ServiceState.FAILED, dependent.getState());
	}
	
	class LeadServiceImpl extends AbstractService {
		@Override
		protected void startService() throws Exception {
			
		}

		@Override
		protected void stopService() throws Exception {
		}
		
	}
	
	class DepServiceImpl extends AbstractService {
		@Override
		protected void startService() throws Exception {
			
		}

		@Override
		protected void stopService() throws Exception {
		}
		
	}
}
