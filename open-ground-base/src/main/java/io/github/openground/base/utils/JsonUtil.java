package io.github.openground.base.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;

/**
 * JSON 工具类（基于 Fastjson）
 *
 * @author open-ground
 * @version 1.0
 */
public class JsonUtil {

    /**
     * 对象转 json 字符串并格式化
     *
     * @param obj 对象
     * @return 格式化后的 json 字符串
     */
    public static String toFormatJson(Object obj) {
        if (obj instanceof Array || obj instanceof List) {
            return jsonFormat(toJson(obj), "Array");
        } else {
            return jsonFormat(toJson(obj), "Object");
        }
    }

    /**
     * 对象转 json 字符串
     *
     * @param obj 对象
     * @return json 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            obj = new HashMap<String, String>();
        }
        return JSON.toJSONString(obj,
                SerializerFeature.WriteDateUseDateFormat,
                SerializerFeature.DisableCircularReferenceDetect);
    }

    /**
     * json 格式化 json 字符串
     *
     * @param jsonString json 字符串
     * @param type       Array 或 Object
     * @return 格式化后的字符串
     */
    public static String jsonFormat(String jsonString, String type) {
        String formatJson;
        if ("Array".equals(type)) {
            JSONArray array = JSONArray.parseArray(jsonString);
            formatJson = JSON.toJSONString(array,
                    SerializerFeature.PrettyFormat,
                    SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteDateUseDateFormat);
        } else {
            JSONObject object = JSONObject.parseObject(jsonString);
            formatJson = JSON.toJSONString(object,
                    SerializerFeature.PrettyFormat,
                    SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteDateUseDateFormat);
        }
        return formatJson;
    }
}
