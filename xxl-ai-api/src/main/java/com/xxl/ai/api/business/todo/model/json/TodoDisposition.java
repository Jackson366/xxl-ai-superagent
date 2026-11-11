package com.xxl.ai.api.business.todo.model.json;

import java.util.ArrayList;
import java.util.List;

/**
 * 触发去向（xxl_ai_todo_trigger.disposition 列的 JSON 值对象），不授予任何权限，只记录走向
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoDisposition {

    public String evalId;                                  /* 进入的EvalID */
    public boolean assistantConfirmed;                     /* 已在助理入口确认执行范围 */
    public Long targetTicketId;                            /* 仅投递给指定工单 */
    public Long decisionId;                                /* 产生的决定ID */
    public List<Long> fanout = new ArrayList<>();          /* 送达的工单ID */
    public List<TodoHandback> handbacks = new ArrayList<>(); /* 被退回的工单与原因 */
    public List<Long> acked = new ArrayList<>();           /* 已确认接手的工单ID */
    public boolean requeued;                               /* 人工重新研判过 */

    /**
     * Worker 退回记录
     */
    public static class TodoHandback {

        public long ticketId;   /* 退回的工单ID */
        public String reason;   /* 退回原因 */
        public long at;         /* 退回时间（毫秒） */

    }

    /**
     * 该触发是否仍归某工单决定：送达且未被其退回
     */
    public boolean ownedBy(long ticketId) {
        if (!fanout.contains(ticketId)) {
            return false;
        }
        for (TodoHandback handback : handbacks) {
            if (handback.ticketId == ticketId) {
                return false;
            }
        }
        return true;
    }

}
