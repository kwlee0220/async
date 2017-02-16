package async;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FutureCondition {
	public boolean evaluateNow();

	public void await() throws InterruptedException;
	public void await(long time, TimeUnit unit) throws InterruptedException;
}
