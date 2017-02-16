package async.support;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.jws.Oneway;

import org.apache.log4j.Logger;

import com.google.common.collect.Lists;

import async.AsyncOperation;
import async.Startable;
import async.StartableListener;
import async.StartableState;
import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee
 */
public class AsyncUtils {
	private AsyncUtils() {
		throw new AssertionError("should not be called: class=" + getClass());
	}
	
	public static <T> AsyncCompletableFuture<T> wrap(CompletableFuture<T> future) {
		return AsyncCompletableFuture.get(future);
	}
	
	public static AsyncRunnable runAsync(Runnable task, Runnable canceler) {
		return new AsyncRunnable(task, canceler);
	}
	
	public static AsyncRunnable runAsync(Runnable task) {
		return new AsyncRunnable(task, null);
	}
	
	public static <T> AsyncSupplier<T> runAsync(Supplier<T> task, Runnable canceler) {
		return new AsyncSupplier<T>(task, canceler);
	}
	
	public static <T> AsyncSupplier<T> runAsync(Supplier<T> task) {
		return new AsyncSupplier<T>(task, null);
	}
	
	public static <T> AsyncCallable<T> from(Callable<T> task, Runnable canceler) {
		return new AsyncCallable<T>(task, canceler);
	}
	
	public static <T> AsyncCallable<T> from(Callable<T> task) {
		return new AsyncCallable<T>(task, null);
	}

	public static <T> boolean stopQuietly(AsyncOperation<T> aop) {
		if ( aop != null ) {
			try {
				aop.cancel();
				return true;
			}
			catch ( Throwable ignored ) { }
		}
		return false;
	}

	public static <T> boolean stopQuietlyAndSynchronously(AsyncOperation<T> aop) {
		if ( aop != null ) {
			try {
				aop.cancel();
				aop.waitForFinished();
			}
			catch ( Throwable ignored ) { }
		}
		return false;
	}
	
	public static boolean startQuietly(Startable startable) {
		if ( startable != null ) {
			try {
				startable.start();
				return true;
			}
			catch ( Exception ignored ) { }
		}
		return false;
	}

	public static boolean stopQuietly(Startable startable) {
		if ( startable != null ) {
			try {
				startable.stop();
				
				return true;
			}
			catch ( Throwable ignored ) {
				return false;
			}
		}
		else {
			return false;
		}
	}

	public static void stopQuietly(Executor executor, Startable... tasks) throws InterruptedException {
		List<StartableCondition> conditions = Lists.newArrayList();
		for ( Startable task: tasks ) {
			if ( task != null ) {
				conditions.add(StartableCondition.whenStopped(task));
				stopAsynchronously(task, executor);
			}
		}
		
		for ( StartableCondition cond: conditions ) {
			cond.await();
		}
	}

	public static void stopAsynchronously(final Startable job, Executor executor) {
		Utilities.executeAsynchronously(executor, new Runnable() {
			public void run() {
				try {
					job.stop();
				}
				catch ( Exception ignored ) {
					System.err.println("async stop failed: " + ignored);
				}
			}
		});
	}
	
	public static final void setChain(Startable leader, Startable follower) {
		leader.addStartableListener(new Propagator(leader, follower));
	}
	
	static class Propagator implements StartableListener {
		private static final Logger s_logger = Logger.getLogger("STARTABLE.CHAIN");
		
		private final Startable m_leader;
		private final Startable m_follower;
		
		Propagator(Startable leader, Startable follower) {
			m_leader = leader;
			m_follower = follower;
		}
		
		@Override @Oneway
		public void onStateChanged(Startable target, StartableState fromState, StartableState toState) {
			if ( fromState == StartableState.STOPPED && toState == StartableState.RUNNING ) {
				try {
					m_follower.start();
				}
				catch ( Exception e ) {
					s_logger.error("fails to start the follower in chain: comp=" + m_follower + ", cause=" + e);
					
					AsyncUtils.stopQuietly(m_leader);
				}
			}
			else if ( fromState == StartableState.RUNNING && toState == StartableState.STOPPED ) {
				try {
					m_follower.stop();
				}
				catch ( Throwable e ) {
					s_logger.error("fails to stop the follower in chain: comp=" + m_follower + ", cause=" + e);
				}
			}
		}
	}
}
