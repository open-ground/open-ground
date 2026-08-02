package io.github.openground.land.common.util;

import javax.servlet.http.HttpServletRequest;
import org.apache.ibatis.type.Alias;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 说明：参数封装Map
 * 创建人：  
 * 修改时间：2014年9月20日
 * @version
 */
@SuppressWarnings("rawtypes")
@Alias("pd")
public class PageData extends PlatformHashMap implements Map{
	
	private static final long serialVersionUID = 1L;
	
	private Map map = null;
	private HttpServletRequest request;
	@SuppressWarnings("rawtypes")
	public PageData(HttpServletRequest request){
		this.request = request;
		Map properties = request.getParameterMap();
		Map returnMap = new HashMap(); 
		Iterator entries = properties.entrySet().iterator(); 
		Entry entry;
		String name = "";
		StringBuffer value = new StringBuffer();
		while (entries.hasNext()) {
			entry = (Entry) entries.next();
			name = (String) entry.getKey(); 
			Object valueObj = entry.getValue(); 
//			if(null == valueObj){ 
			value = value.delete(0, value.length()); 
//			}else if(valueObj instanceof String[]){ 
			if(valueObj instanceof String[]){
				String[] values = (String[])valueObj;
				for(int i=0;i<values.length;i++){ 
					 value.append(values[i]);
					 value.append(",");
				}
				value = value.delete(value.length()-1, value.length()); 
			}else{
				value = (StringBuffer) valueObj; 
			}
			returnMap.put(name, value.toString()); 
		}
		map = returnMap;
	}
	
	public PageData() {
		map = new HashMap();
	}
	
	@Override
	public Object get(Object key) {
		Object obj = null;
		if(map.get(key) instanceof Object[]) {
			Object[] arr = (Object[])map.get(key);
			obj = request == null ? arr:(request.getParameter((String)key) == null ? arr:arr[0]);
		} else {
			obj = map.get(key);
		}
		return obj;
	}
	
	public String getString(Object key) {
		return (String)get(key);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Object put(Object key, Object value) {
		return map.put(key, value);
	}
	
	@Override
	public Object remove(Object key) {
		return map.remove(key);
	}

	public void clear() {
		map.clear();
	}

	public boolean containsKey(Object key) {
		//Auto-generated method stub
		return map.containsKey(key);
	}

	public boolean containsValue(Object value) {
		//Auto-generated method stub
		return map.containsValue(value);
	}

	public Set entrySet() {
		//Auto-generated method stub
		return map.entrySet();
	}

	public boolean isEmpty() {
		//Auto-generated method stub
		return map.isEmpty();
	}

	public Set keySet() {
		//Auto-generated method stub
		return map.keySet();
	}

	@SuppressWarnings("unchecked")
	public void putAll(Map t) {
		//Auto-generated method stub
		map.putAll(t);
	}

	public int size() {
		//Auto-generated method stub
		return map.size();
	}

	public Collection values() {
		//Auto-generated method stub
		return map.values();
	}
	
	private StringBuilder prefix = new StringBuilder("  ");

	public String getPrefix() {
		return prefix.toString();
	}

	public void setPrefix(String prefix) {
		this.prefix.append(prefix);
	}
//	@SuppressWarnings({ "rawtypes", "unchecked" })
//	@Override
//	public String toString() {
//		Iterator<Map.Entry<K, V>> i = entrySet().iterator();
//		if (!i.hasNext())
//			return "{}";
//
//		StringBuilder sb = new StringBuilder();
//		sb.append(this.getPrefix().substring(0, this.getPrefix().length() - 1));
//		sb.append('{').append('\n');
//		for (;;) {
//			Map.Entry<K, V> e = i.next();
//			K key = e.getKey();
//			V value = e.getValue();
//			if (value instanceof FormatHashMap) {
//				((FormatHashMap) value).setPrefix(this.getPrefix());
//			}
//			if (value instanceof List) {
//				List l = (List) value;
//				for (int m = 0; m < l.size(); m++) {
//					if (l.get(m) instanceof FormatHashMap) {
//						((FormatHashMap) l.get(m)).setPrefix(this.getPrefix());
//					}
//				}
//			}
//			sb.append(this.getPrefix());
//			sb.append(key == this ? "(this Map)" : key);
//			sb.append('=');
//			sb.append(value == this ? "(this Map)" : value);
//			if (!i.hasNext())
//				return sb.append('\n').append(this.getPrefix().substring(0, this.getPrefix().length() - 1)).append('}').toString();
//			sb.append(',').append('\n');
//		}
//	}
}
