package com.xxl.ai.api.business.harness.superagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具观测不改变上下文、结果、异常与直接返回语义
 *
 * @author Codex 2026-11-11
 */
class SuperagentTraceToolTest {
    @Test
    void retainsContextMetadataAndPairsEvents() {
        List<SuperagentTraceEvent> events = new ArrayList<>();
        ToolContext context = new ToolContext(Map.of("test", "context"));
        ToolCallback delegate = new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("probe").description("test").inputSchema("{}").build();
            }
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().returnDirect(true).build();
            }
            public String call(String input) {
                throw new AssertionError("上下文不能被丢弃");
            }
            public String call(String input, ToolContext supplied) {
                assertSame(context, supplied);
                return "{\"error\":\"denied\"}";
            }
        };
        ToolCallback observed = (ToolCallback) SuperagentTraceTool.wrap(new Object[]{delegate}, events::add)[0];
        assertTrue(observed.getToolMetadata().returnDirect());
        assertEquals("{\"error\":\"denied\"}", observed.call("{}", context));
        assertEquals(List.of("tool.started", "tool.failed"), events.stream().map(SuperagentTraceEvent::kind).toList());
        assertEquals(events.get(0).callId(), events.get(1).callId());
        assertNotNull(events.get(1).durationMs());
    }

    @Test
    void convertsAnnotatedToolsAndIgnoresObservationFailures() {
        ToolCallback observed = (ToolCallback) SuperagentTraceTool.wrap(new Object[]{new EchoTool()}, event -> {
            throw new IllegalStateException("观测不可用");
        })[0];
        assertEquals(org.springframework.ai.support.ToolCallbacks.from(new EchoTool())[0].call("{}"), observed.call("{}"));
    }

    @Test
    void errorTextWrappedByResultConverterStillFails() {
        List<SuperagentTraceEvent> events = new ArrayList<>();
        ToolCallback callback = org.springframework.ai.tool.function.FunctionToolCallback
                .builder("safe_report", (java.util.function.Function<Map<String, Object>, String>) args -> "{\"error\":\"denied\"}")
                .description("test").inputType(Map.class).inputSchema("{}").build();
        ToolCallback observed = (ToolCallback) SuperagentTraceTool.wrap(new Object[]{callback}, events::add)[0];
        assertEquals(callback.call("{}"), observed.call("{}"));
        assertEquals("tool.failed", events.get(1).kind());
    }

    static class EchoTool {
        @Tool(description = "test")
        public String echo() {
            return "ok";
        }
    }
}
