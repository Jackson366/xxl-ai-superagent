package com.xxl.ai.api.business.todo.model.json;

import java.util.ArrayList;
import java.util.List;

/**
 * 线程进行中的 Eval（xxl_ai_todo_thread.evaluating 列的 JSON 值对象）
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoEvaluating {

    public String id;                                  /* EvalID */
    public List<Long> triggerIds = new ArrayList<>();  /* 本批触发ID */
    public long primary;                               /* 赋予本批强度的触发ID，接单后作为工单发起触发 */
    public String strength;                            /* 强度：explicit、inferred */
    public String policyVersion;                       /* 开始时的策略版本 */
    public long startedAt;                             /* 开始时间（毫秒） */
    public long delivered;                             /* 候选交给 Router 的时间（毫秒），0 表示尚在排队，排队期间不超时 */
    public long deadline;                              /* 超时时间（毫秒），从交给 Router 起计 */

}
