package com.xxl.ai.api.business.todo.model.json;

/**
 * Worker 模型快照（xxl_ai_todo_ticket.worker_model 列的 JSON 值对象）：工单首次执行时记下，之后不变
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoWorkerModel {

    public long spaceId;       /* 超级助理空间ID */
    public long supplierId;    /* 供应商ID */
    public long modelId;       /* 模型ID */

}
