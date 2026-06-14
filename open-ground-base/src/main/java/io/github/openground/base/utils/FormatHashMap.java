package io.github.openground.base.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * FormatHashMap 重写 toString 方法使格式化输出，支持点号分隔的层级 key
 * <p>
 * 例如：map.put("a.b.c", "value") 会自动创建嵌套结构 {a={b={c=value}}}
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author open-ground
 * @version 1.0
 */
@SuppressWarnings("serial")
public class FormatHashMap<K, V> extends HashMap<K, V> {
    private StringBuilder prefix = new StringBuilder("    ");

    public String getPrefix() {
        return prefix.toString();
    }

    public void setPrefix(String prefix) {
        this.prefix.append(prefix);
    }

    public FormatHashMap(Map<K, V> map) {
        super(map);
    }

    public FormatHashMap() {
        super();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public V put(K key, V value) {
        if (key.toString().indexOf(".") > -1) {
            String k = key.toString();
            String[] kps = k.split("\\.");
            Map t = this;
            Map n;
            for (int i = 0; i < kps.length; i++) {
                if (t.get(kps[i]) == null) {
                    if (i == kps.length - 1) {
                        t.put(kps[i], value);
                    } else {
                        n = new FormatHashMap();
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
        if (!i.hasNext()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{').append('\n');
        for (; ; ) {
            Entry<K, V> e = i.next();
            K key = e.getKey();
            V value = e.getValue();
            if (value instanceof FormatHashMap) {
                ((FormatHashMap) value).setPrefix(this.getPrefix());
            }
            if (value instanceof List) {
                List l = (List) value;
                for (int m = 0; m < l.size(); m++) {
                    if (l.get(m) instanceof FormatHashMap) {
                        ((FormatHashMap) l.get(m)).setPrefix(this.getPrefix());
                    }
                }
            }
            sb.append(this.getPrefix());
            sb.append(key == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);
            if (!i.hasNext()) {
                return sb.append('\n').append(this.getPrefix().substring(0, this.getPrefix().length() - 4)).append('}').toString();
            }
            sb.append(',').append('\n');
        }
    }
}
