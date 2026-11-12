package com.xxl.ai.api.business.todo.model.dto;

/**
 * 对话中需要用户处理的待办消息
 *
 * @author xxl-ai 2026-11-12
 */
public record TodoAssistantNoticeDTO(String id, long threadId, long ticketId, String title,
                                     String stage, String content, String attentionId, boolean canRetry) {
}
