package cv;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@XmlRootElement
@XmlType(propOrder={"value", "modified"})
public final class ValueInfo<T> {
	/** Context 값 */
	public T value;
	/** Context 값 설정 시각 (in milli-seconds) */
	public long modified;
	
	public ValueInfo() { }
	public ValueInfo(T value, long modified) {
		this.value = value;
		this.modified = modified;
	}
	
	public ValueInfo(T value) {
		this.value = value;
		this.modified = System.currentTimeMillis();
	}
	
	public ValueInfo<T> duplicate() {
		return new ValueInfo<>(value, modified);
	}
	
	@Override
	public String toString() {
		return "" + this.value + "(modified=" + modified + ")";
	}
}
