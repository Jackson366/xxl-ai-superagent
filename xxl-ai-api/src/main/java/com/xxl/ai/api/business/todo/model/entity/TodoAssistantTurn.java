package com.xxl.ai.api.business.todo.model.entity;

/**
 * 超级助理入口消息及执行建议
 *
 * @author xxl-ai 2026-11-12
 */
public record TodoAssistantTurn(long id, String content, String reply, String state,
                                Long referenceId, String referenceTitle, Long revisesId, Long targetTicketId,
                                String attentionId, String proposal, String errorCode, Long threadId) {
}
