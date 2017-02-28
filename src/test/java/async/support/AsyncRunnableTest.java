package async.support;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

import async.AsyncOperation;
import async.AsyncOperationState;
import async.AsyncOperationStateChangeEvent;
import async.OperationStoppedException;
import net.jcip.annotations.GuardedBy;
import utils.Errors;
import utils.Lambdas;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncRunnableTest {
	private boolean m_runnableCalled;
	
	private final Lock m_lock = new ReentrantLock();
	private final Condition m_cond = m_lock.newCondition();
	@GuardedBy("m_lock") private AsyncOperationState m_lastState;
	
	@Before
	public void setUp() {
		m_runnableCalled = false;
		
		m_lock.lock();
		try {
			m_lastState = AsyncOperationState.NOT_STARTED;
			m_cond.signalAll();
		}
		finally {
			m_lock.unlock();
		}
	}
	
	@Subscribe @AllowConcurrentEvents
	public void receive(AsyncOperationStateChangeEvent<Void> event) {
		Lambdas.guraded(m_lock, () -> {
			m_lastState = event.getToState();
			m_cond.signalAll();
		});
	}
	
	@Test(timeout=500)
	public void testBasic() throws Exception {
		AsyncOperation<Void> aop = AsyncUtils.runAsync(() -> {
			m_runnableCalled = true;
			Errors.runQuietly(() -> Thread.sleep(200));
		});
		aop.addStateChangeListener(this);
		
		Assert.assertTrue(aop.isNotStarted());
		
		aop.start();
		aop.waitForStarted();
		Assert.assertEquals(AsyncOperationState.RUNNING, waitWhile(AsyncOperationState.NOT_STARTED));
		
		aop.waitForFinished();
		Assert.assertTrue(aop.isCompleted());
		Assert.assertTrue(m_runnableCalled);
		Assert.assertEquals(AsyncOperationState.COMPLETED, waitWhile(AsyncOperationState.RUNNING));
	}
	
	@Test(expected = IllegalStateException.class)
	public void testFailure() throws Exception {
		AsyncOperation<Void> aop = AsyncUtils.runAsync(() -> {
			m_runnableCalled = true;
			throw new IllegalArgumentException();
		});
		
		aop.start();
		aop.waitForFinished();
		Assert.assertTrue(aop.isFailed());
		Assert.assertEquals(IllegalArgumentException.class, aop.getFailureCause().getClass());
		aop.getResult();
	}
	
	@Test
	public void testSelfCancel1() throws Exception {
		AsyncOperation<Void> aop = AsyncUtils.runAsync(() -> {
			Errors.runQuietly(()->Thread.sleep(200));
			Thread.currentThread().interrupt();
		});
		
		aop.start();
		aop.waitForFinished();
		Assert.assertEquals(AsyncOperationState.CANCELLED, aop.getState());
	}
	
	@Test
	public void testSelfCancel2() throws Exception {
		AsyncOperation<Void> aop = AsyncUtils.runAsync(() -> {
			Errors.runQuietly(()->Thread.sleep(200));
			throw new OperationStoppedException("self stopped");
		});
		
		aop.start();
		aop.waitForFinished();
		Assert.assertEquals(AsyncOperationState.CANCELLED, aop.getState());
	}

	@Test(timeout=200)
	public void testCancel1() throws Exception {
		AsyncOperation<Void> aop = AsyncUtils.runAsync(() -> {
			Errors.runRTE(()->Thread.sleep(30000));
		});
		
		aop.start();
		aop.waitForStarted();
		
		aop.cancel();
		aop.waitForFinished();
		Assert.assertEquals(AsyncOperationState.CANCELLED, aop.getState());
	}
	
	private AsyncOperationState waitWhile(final AsyncOperationState state)
		throws InterruptedException {
		m_lock.lock();
		try {
			while ( m_lastState == state ) {
				m_cond.await();
			}
			
			return m_lastState;
		}
		finally {
			m_lock.unlock();
		}
	}
}
