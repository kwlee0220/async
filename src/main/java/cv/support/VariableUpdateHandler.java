package cv.support;

import async.AsyncOperation;
import cv.VariableUpdateException;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface VariableUpdateHandler {
	public AsyncOperation<Void> update(Object value) throws VariableUpdateException;
}