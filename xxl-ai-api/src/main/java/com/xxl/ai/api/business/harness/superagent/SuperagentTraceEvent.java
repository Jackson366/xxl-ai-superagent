package com.xxl.ai.api.business.harness.superagent;

/**
 * 超级助理运行时事件；持久化和业务归属由调用方负责
 *
 * @author Codex 2026-11-11
 */
public record SuperagentTraceEvent(String kind, String status, String summary, String detail,
                                   String callId, Long durationMs) {
}
