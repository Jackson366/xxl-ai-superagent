package com.xxl.ai.api.business.todo.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xxl.tool.core.StringTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 超级助理 JSON 列读写工具（聚合内部结构以 JSON 文本存储，只按主键读写）
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoJsonTool {

    private static final Gson GSON = new Gson();

    private TodoJsonTool() {
    }

    /**
     * 对象转 JSON，空对象返回 null
     */
    public static String toJson(Object value) {
        return value == null ? null : GSON.toJson(value);
    }

    /**
     * JSON 转对象，空文本返回 null
     */
    public static <T> T fromJson(String json, Class<T> type) {
        return StringTool.isBlank(json) ? null : GSON.fromJson(json, type);
    }

    /**
     * JSON 数组转 ID 列表（可修改），空文本返回空列表
     */
    public static List<Long> longList(String json) {
        if (StringTool.isBlank(json)) {
            return new ArrayList<>();
        }
        List<Long> list = GSON.fromJson(json, TypeToken.getParameterized(List.class, Long.class).getType());
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /**
     * ID 列表转 JSON 数组；空列表存 NULL，便于按 IS NOT NULL 扫描
     */
    public static String longListJson(List<Long> list) {
        return list == null || list.isEmpty() ? null : GSON.toJson(list);
    }

    /**
     * 读取工具参数文本（模型给出的参数是外部数据，统一转字符串后再校验）
     */
    public static String text(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 读取工具参数 ID：兼容字符串与数字（Gson 将数字解析为 Double），非法返回 null
     */
    public static Long id(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = text(args, key);
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text.startsWith("#") ? text.substring(1) : text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取工具参数整数，缺省或非法返回默认值
     */
    public static int intValue(Map<String, Object> args, String key, int defaultValue) {
        Long value = id(args, key);
        return value == null ? defaultValue : value.intValue();
    }

    /**
     * 截断文本
     */
    public static String abbreviate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

}
