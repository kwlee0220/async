package async;

import com.google.common.eventbus.Subscribe;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ServiceStateChangeListener {
	@Subscribe
	public void onServiceStateChange(ServiceStateChangeEvent event);
}
