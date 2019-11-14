package async.support;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;

import async.AsyncOperation;
import async.Service;
import async.ServiceState;
import async.ServiceStateChangeEvent;
import async.ServiceStateChangeListener;
import async.optor.ConcurrentService;
import utils.Utilities;
import utils.func.CheckedRunnable;
import utils.func.Try;
import utils.func.Unchecked;


/**
 * 
 * @author Kang-Woo Lee
 */
public class AsyncUtils {
	private AsyncUtils() {
		throw new AssertionError("should not be called: class=" + getClass());
	}
	
	public static CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
		return CompletableFuture.runAsync(task, executor);
	}
	public static CompletableFuture<Void> runAsync(Runnable task) {
		return CompletableFuture.runAsync(task);
	}
	
	public static CompletableFuture<Try<Void>> tryToRunAsync(CheckedRunnable task, Executor executor) {
		return CompletableFuture.supplyAsync(Try.lift(task), executor);
	}
	public static CompletableFuture<Try<Void>> tryToRunAsync(CheckedRunnable task) {
		return CompletableFuture.supplyAsync(Try.lift(task));
	}
	
	public static CompletableFuture<Void> runAsyncSneakily(CheckedRunnable task, Executor executor) {
		return CompletableFuture.runAsync(Unchecked.sneakyThrow(task), executor);
	}
	public static CompletableFuture<Void> runAsyncSneakily(CheckedRunnable task) {
		return CompletableFuture.runAsync(Unchecked.sneakyThrow(task));
	}
	
	public static <T> AsyncCompletableFuture<T> wrap(CompletableFuture<T> future) {
		return AsyncCompletableFuture.get(future);
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
	
	public static Try<Void> startQuietly(Service service) {
		return Try.run(service::start);
	}
	
	public static Try<Void> stopQuietly(Service service) {
		return Try.run(service::stop);
	}

	public static void stopQuietly(Service... tasks) {
		Arrays.stream(tasks).forEach(AsyncUtils::stopQuietly);
	}

	public static void stopAsynchronously(final Service svc, Executor executor) {
		Utilities.runAsync(executor, new Runnable() {
			public void run() {
				try {
					svc.stop();
				}
				catch ( Exception ignored ) {
					System.err.println("async stop failed: " + ignored);
				}
			}
		});
	}
	
	public static final Service concurrent(Service... services) {
		return new ConcurrentService(services);
	}
	
	public static final void setFailureDependency(Service dependee, AbstractService dependent) {
		dependee.addStateChangeListener(new ServiceStateChangeListener() {
			@Subscribe
			public void onServiceStateChange(ServiceStateChangeEvent event) {
				if ( event.getToState() == ServiceState.FAILED ) {
					dependent.notifyServiceFailed(event.getService().getFailureCause());
				}
			}
		});
	}
	
	public static final Object chain(Service dependee, Service dependent) {
		Propagator chain = new Propagator(dependee, dependent);
		dependee.addStateChangeListener(chain);
		
		return chain;
	}
	
	public static final void unchain(Object chain) {
		Preconditions.checkArgument(chain instanceof Propagator, "invalid chain: not "
																+ Propagator.class.getName());
		
		Propagator link = (Propagator)chain;
		link.m_dependee.removeStateChangeListener(link);
	}
	
	static class Propagator {
		private static final Logger s_logger = LoggerFactory.getLogger("STARTABLE.CHAIN");
		
		private final Service m_dependee;
		private final Service m_dependent;
		
		Propagator(Service dependee, Service dependent) {
			m_dependee = dependee;
			m_dependent = dependent;
		}
		
		public Service getDependee() {
			return m_dependee;
		}
		
		@Subscribe
		public void onStateChanged(ServiceStateChangeEvent event) {
			final Service target = event.getService();
			
			switch ( event.getToState() ) {
				case RUNNING:
					CompletableFuture.runAsync(() -> {
						try {
							m_dependent.start();
						}
						catch ( Throwable e ) {
							s_logger.error("fails to start the dependent in chain: comp={}, cause={}",
											m_dependent, e);
							stopQuietly(target);
						}
					});
					break;
				case STOPPED:
					CompletableFuture.runAsync(() -> m_dependent.stop());
					break;
				case FAILED:
					if ( m_dependent instanceof AbstractService ) {
						AbstractService asvc = (AbstractService)m_dependent;
						CompletableFuture.runAsync(() -> asvc.notifyServiceFailed(target.getFailureCause()));
					}
					else {
						CompletableFuture.runAsync(() -> m_dependent.stop());
					}
					break;
			}
		}
	}
}
