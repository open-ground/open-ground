package io.github.openground.land.common.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * PlatformHashMap 重写toString方法使格式化
 *@version v_1.0 
 *@date 2017年3月28日 下午3:49:44
 * @param <K>
 * @param <V>
 */
@SuppressWarnings("serial")
public class PlatformHashMap<K, V> extends HashMap<K, V> {
	private StringBuilder prefix = new StringBuilder("  ");

	public String getPrefix() {
		return prefix.toString();
	}

	public void setPrefix(String prefix) {
		this.prefix.append(prefix);
	}

	public PlatformHashMap(Map<K, V> map) {
		super(map);
	}

	public PlatformHashMap() {
		super();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public V put(K key, V value) {
		if (key.toString().indexOf(".") > -1) {
			String k = key.toString();
			String[] kps = k.split("\\.");
			Map t = this;
			Map n = null;
			for (int i = 0; i < kps.length; i++) {
				if (t.get(kps[i]) == null) {
					if (i == kps.length - 1) {
						t.put(kps[i], value);
					} else {
						n = new PlatformHashMap();
						t.put(kps[i], n);
						t = n;
					}
				} else {
					if (i == kps.length - 1) {
						t.put(kps[i], value);
					} else {
						t = (Map) t.get(kps[i]);
					}

				}
			}
			return value;
		} else {
			return super.put(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		if (key.toString().indexOf(".") < 0) {
			return super.get(key);
		} else {
			String k = key.toString();
			String[] kps = k.split("\\.");
			Map<K, V> t = this;
			for (int i = 0; i < kps.length; i++) {
				if (t.get(kps[i]) == null) {
					return null;
				} else {
					if (i == kps.length - 1) {
						return t.get(kps[i]);
					} else {
						if (t.get(kps[i]) instanceof Map) {
							t = (Map<K, V>) t.get(kps[i]);
						} else {
							return null;
						}

					}
				}
			}
		}
		return null;

	}

	@SuppressWarnings("rawtypes")
	@Override
	public String toString() {
		Iterator<Entry<K, V>> i = entrySet().iterator();
		if (!i.hasNext())
			return "{}";

		StringBuilder sb = new StringBuilder();
		sb.append(this.getPrefix().substring(0, this.getPrefix().length() - 1));
		sb.append('{').append('\n');
		for (;;) {
			Entry<K, V> e = i.next();
			K key = e.getKey();
			V value = e.getValue();
			if (value instanceof PlatformHashMap) {
				((PlatformHashMap) value).setPrefix(this.getPrefix());
			}
			if (value instanceof List) {
				List l = (List) value;
				for (int m = 0; m < l.size(); m++) {
					if (l.get(m) instanceof PlatformHashMap) {
						((PlatformHashMap) l.get(m)).setPrefix(this.getPrefix());
					}
				}
			}
			sb.append(this.getPrefix());
			sb.append(key == this ? "(this Map)" : key);
			sb.append('=');
			sb.append(value == this ? "(this Map)" : value);
			if (!i.hasNext())
				return sb.append('\n').append(this.getPrefix().substring(0, this.getPrefix().length() - 1)).append('}').toString();
			sb.append(',').append('\n');
		}
	}

}
