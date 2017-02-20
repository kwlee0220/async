package cv;

import java.util.Collection;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ContextMemory {
	/**
	 * 주어진 식별자에 해당하는 상황 변수의 등록 여부를 반환한다.
	 * 
	 * @param id	확인 대상 상황 변수의 식별자.
	 * @return		등록된 경우는 <code>true</code>, 그렇지 않은 경우는 <code>false</code>.
	 */
	public boolean existsContextVariable(String id);

	/**
	 * 주어진 식별자에 해당하는 상황 변수를 얻는다.
	 * 
	 * @param id	획득 대상 상황 변수의 식별자.
	 * @return 		식별자에 해당하는 상황 변수.
	 * @throws VariableNotFoundException	식별자에 해당하는 상황 변수가 존재하지 않는 경우.
	 */
	public ContextVariable getContextVariable(String id) throws VariableNotFoundException;
	
	/**
	 * 등록된 모든 상황 변수들의 식별자를 반환한다.
	 * 
	 * @return	등록된 상황 변수들의 식별자 모음.
	 */
	public Collection<String> getContextVariableIdAll();
}
