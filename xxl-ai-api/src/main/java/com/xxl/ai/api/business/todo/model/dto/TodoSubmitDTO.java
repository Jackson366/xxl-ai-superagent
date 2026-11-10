package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理 提交入参（新建、追加、回答）
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoSubmitDTO {

    private String requestId;      /* 幂等键：前端生成，失败重试复用 */
    private Long threadId;         /* 待办ID（追加/回答必填） */
    private String kind;           /* 类型：create、append、answer */
    private String content;        /* 提交内容 */
    private String attentionId;    /* 回答的提问ID（kind=answer） */

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttentionId() {
        return attentionId;
    }

    public void setAttentionId(String attentionId) {
        this.attentionId = attentionId;
    }

}
