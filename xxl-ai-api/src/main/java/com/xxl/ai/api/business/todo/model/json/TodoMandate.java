package com.xxl.ai.api.business.todo.model.json;

/**
 * 授权（xxl_ai_todo_ticket.mandate / xxl_ai_todo_decision.mandate 列的 JSON 值对象），创建后不可修改
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoMandate {

    public String strength;        /* 强度：explicit、inferred */
    public String policyVersion;   /* 接单时的策略版本 */
    public String basis;           /* 准入依据：addressed-管理员在 Web 表单直接提交 */
    public int actorUserId;        /* 提交人用户ID */
    public String scope;           /* 允许做的事 */

}
