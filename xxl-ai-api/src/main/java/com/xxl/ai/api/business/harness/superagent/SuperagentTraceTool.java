package com.xxl.ai.api.business.harness.superagent;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 超级助理工具观测：覆盖注解工具及回调工具，保留原定义、上下文与返回行为
 *
 * @author Codex 2026-11-11
 */
public final class SuperagentTraceTool {
    private static final Logger logger = LoggerFactory.getLogger(SuperagentTraceTool.class);

    private SuperagentTraceTool() {
    }

    /* 观测失败不改变模型或工具的执行结果 */
    public static void emit(Consumer<SuperagentTraceEvent> observer, SuperagentTraceEvent event) {
        try {
            observer.accept(event);
        } catch (RuntimeException error) {
            logger.warn("超级助理事件记录失败, kind={}", event.kind(), error);
        }
    }

    /* 先将注解对象转成 ToolCallback，再包裹统一生命周期 */
    public static Object[] wrap(Object[] tools, Consumer<SuperagentTraceEvent> observer) {
        List<Object> traced = new ArrayList<>();
        for (Object tool : tools) {
            ToolCallback[] callbacks = tool instanceof ToolCallback callback
                    ? new ToolCallback[]{callback} : ToolCallbacks.from(tool);
            for (ToolCallback callback : callbacks) {
                traced.add(new ToolCallback() {
                    @Override
                    public ToolDefinition getToolDefinition() {
                        return callback.getToolDefinition();
                    }

                    @Override
                    public ToolMetadata getToolMetadata() {
                        return callback.getToolMetadata();
                    }

                    @Override
                    public String call(String input) {
                        return invoke(input, null);
                    }

                    @Override
                    public String call(String input, ToolContext context) {
                        return invoke(input, context);
                    }

                    /* 每次调用独立 ID；返回错误 JSON 也记录为失败 */
                    private String invoke(String input, ToolContext context) {
                        String callId = UUID.randomUUID().toString();
                        String name = getToolDefinition().name();
                        long start = System.nanoTime();
                        emit(observer, new SuperagentTraceEvent("tool.started", "running", name, input, callId, null));
                        try {
                            String result = context == null ? callback.call(input) : callback.call(input, context);
                            boolean failed = errorResult(result);
                            emit(observer, new SuperagentTraceEvent(failed ? "tool.failed" : "tool.finished",
                                    failed ? "failed" : "success", name, result, callId,
                                    (System.nanoTime() - start) / 1000000));
                            return result;
                        } catch (RuntimeException error) {
                            emit(observer, new SuperagentTraceEvent("tool.failed", "failed", name,
                                    error.getMessage(), callId, (System.nanoTime() - start) / 1000000));
                            throw error;
                        }
                    }
                });
            }
        }
        return traced.toArray();
    }

    /* 常见 MCP 和内置工具错误回执；普通文本不猜测失败 */
    private static boolean errorResult(String result) {
        try {
            JsonElement json = JsonParser.parseString(result);
            // 默认工具结果转换器可能把 JSON 文本再包一层字符串
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                json = JsonParser.parseString(json.getAsString());
            }
            return json.isJsonObject() && ((json.getAsJsonObject().has("error")
                    && !json.getAsJsonObject().get("error").isJsonNull())
                    || (json.getAsJsonObject().has("isError")
                    && json.getAsJsonObject().get("isError").getAsBoolean()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
