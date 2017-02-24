package async.support;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import async.ServiceState;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ChainTest {
	private AbstractService m_dependee;
	private AbstractService m_dependent;
	private Object m_link;
	
	@Before
	public void setUp() {
		m_dependee = new LeadServiceImpl();
		m_dependent = new DepServiceImpl();
		m_link = AsyncUtils.chain(m_dependee, m_dependent);
	}
	
	@Test
	public void test0() throws Exception {
		m_dependee.start();
		Assert.assertEquals(ServiceState.RUNNING, m_dependee.getState());
		Thread.sleep(100);
		Assert.assertEquals(ServiceState.RUNNING, m_dependent.getState());
		
		m_dependee.stop();
		Assert.assertEquals(ServiceState.STOPPED, m_dependee.getState());
		Thread.sleep(100);
		Assert.assertEquals(ServiceState.STOPPED, m_dependent.getState());
		
		m_dependee.start();
		m_dependee.notifyServiceFailed(new IllegalStateException());
		Assert.assertEquals(ServiceState.FAILED, m_dependee.getState());
		Thread.sleep(300);
		Assert.assertEquals(ServiceState.FAILED, m_dependent.getState());
		
		m_dependee.start();
		Thread.sleep(100);
		Assert.assertEquals(ServiceState.RUNNING, m_dependent.getState());
		
		AsyncUtils.unchain(m_link);
		m_dependee.stop();
		Assert.assertEquals(ServiceState.STOPPED, m_dependee.getState());
		Thread.sleep(100);
		Assert.assertEquals(ServiceState.RUNNING, m_dependent.getState());
		
		m_dependent.stop();
		Assert.assertEquals(ServiceState.STOPPED, m_dependent.getState());
		
		m_dependee.start();
		Thread.sleep(100);
		Assert.assertEquals(ServiceState.STOPPED, m_dependent.getState());
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
