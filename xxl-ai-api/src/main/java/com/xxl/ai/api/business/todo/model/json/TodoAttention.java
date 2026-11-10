package com.xxl.ai.api.business.todo.model.json;

/**
 * 待人工决定（xxl_ai_todo_ticket.attention 列的 JSON 值对象）
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoAttention {

    public String id;          /* 提问ID，回答时携带 */
    public String question;    /* 问题 */
    public Long triggerId;     /* 引出问题的人工触发ID */

}
