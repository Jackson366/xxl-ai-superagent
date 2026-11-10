package com.xxl.ai.api.business.todo.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 执行事件脱敏与长度限制（不保存模型隐藏推理）
 *
 * @author Codex 2026-11-11
 */
public final class TodoEventSanitizer {
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|passwd|secret|token|authorization|api[_-]?key|cookie|credential).*");
    private static final Pattern BEARER_TEXT = Pattern.compile("(?i)(bearer\\s+)[^\\s\"']+");
    private static final Pattern SECRET_TEXT = Pattern.compile("(?i)((?:password|passwd|secret|token|api[_-]?key|authorization|cookie)\\s*[=:]\\s*)[^\\s,;]+");

    private TodoEventSanitizer() {
    }

    /* 先按结构化键脱敏，再处理命令或普通文本中的常见凭据 */
    public static String clean(String text, int limit) {
        if (text == null) {
            return null;
        }
        String value = text;
        try {
            JsonElement json = JsonParser.parseString(text);
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                String nested = json.getAsString().trim();
                if (nested.startsWith("{") || nested.startsWith("[")) {
                    json = JsonParser.parseString(nested);
                }
            }
            if (json.isJsonObject() || json.isJsonArray()) {
                redact(json);
                value = json.toString();
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 输出保留纯文本
        }
        value = BEARER_TEXT.matcher(value).replaceAll("$1[REDACTED]");
        value = SECRET_TEXT.matcher(value).replaceAll("$1[REDACTED]");
        return value.length() > limit ? value.substring(0, limit) + "…" : value;
    }

    /* 递归删除敏感字段的值 */
    private static void redact(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (SECRET_KEY.matcher(entry.getKey()).matches()) {
                    entry.setValue(new com.google.gson.JsonPrimitive("[REDACTED]"));
                } else {
                    redact(entry.getValue());
                }
            }
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(TodoEventSanitizer::redact);
        }
    }
}
