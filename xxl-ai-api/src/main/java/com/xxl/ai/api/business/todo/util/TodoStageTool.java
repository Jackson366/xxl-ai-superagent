package com.xxl.ai.api.business.todo.util;

import com.xxl.ai.api.business.todo.constant.TodoConstant;
import com.xxl.ai.api.business.todo.model.entity.TodoAssignment;
import com.xxl.ai.api.business.todo.model.entity.TodoInput;
import com.xxl.ai.api.business.todo.model.entity.TodoTicket;

import java.util.Date;
import java.util.List;

/**
 * 超级助理 工单阶段判定（纯函数）：自上而下首条命中，阶段互斥且完备
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoStageTool {

    private TodoStageTool() {
    }

    /**
     * 工单阶段
     *
     * @param ticket      工单
     * @param assignments 工单全部派工（按创建顺序）
     * @param inputs      工单全部输入
     */
    public static String ticketStage(TodoTicket ticket, List<TodoAssignment> assignments, List<TodoInput> inputs) {
        if (ticket.getEndedTime() != null) {
            return TodoConstant.STAGE_ENDED;
        }
        if (ticket.getDeliveryError() != null) {
            return TodoConstant.STAGE_DELIVERY;
        }
        if (ticket.getAttention() != null) {
            return TodoConstant.STAGE_ANSWER;
        }
        TodoAssignment latest = latest(assignments);
        String report = latest == null ? null : latest.getReportState();
        // 受阻/失败：人的新消息到达前一直等人
        if ((TodoConstant.REPORT_BLOCKED.equals(report) || TodoConstant.REPORT_FAILED.equals(report))
                && waitsForPerson(latest, inputs)) {
            return TodoConstant.REPORT_BLOCKED.equals(report) ? TodoConstant.STAGE_BLOCKED : TodoConstant.STAGE_FAILED;
        }
        if (latest == null || TodoConstant.ASSIGNMENT_PENDING.equals(latest.getState())) {
            return TodoConstant.STAGE_QUEUED;
        }
        // 输入送达晚于本轮结束：Worker 正在处理
        if (TodoConstant.ASSIGNMENT_RUNNING.equals(latest.getState()) && busy(latest)) {
            return TodoConstant.STAGE_WORKING;
        }
        // 有输入等待投递：排队等槽位
        if (!TodoJsonTool.longList(ticket.getUndelivered()).isEmpty()) {
            return TodoConstant.STAGE_QUEUED;
        }
        if (TodoConstant.REPORT_WAITING.equals(report) || TodoConstant.ASSIGNMENT_ENDED.equals(latest.getState())) {
            return TodoConstant.STAGE_IDLE;
        }
        return TodoConstant.STAGE_WORKING;
    }

    /**
     * 是否可重试：投递失败，或受阻/失败后没有人的新消息
     */
    public static boolean canRetry(TodoTicket ticket, List<TodoAssignment> assignments, List<TodoInput> inputs) {
        String stage = ticketStage(ticket, assignments, inputs);
        return TodoConstant.STAGE_DELIVERY.equals(stage)
                || TodoConstant.STAGE_BLOCKED.equals(stage)
                || TodoConstant.STAGE_FAILED.equals(stage);
    }

    /**
     * 是否需要人处理（面板“要我吗”）
     */
    public static boolean needPerson(String stage) {
        return TodoConstant.STAGE_DELIVERY.equals(stage)
                || TodoConstant.STAGE_ANSWER.equals(stage)
                || TodoConstant.STAGE_BLOCKED.equals(stage)
                || TodoConstant.STAGE_FAILED.equals(stage)
                || TodoConstant.STAGE_UNAVAILABLE.equals(stage);
    }

    /**
     * 最新派工
     */
    public static TodoAssignment latest(List<TodoAssignment> assignments) {
        return assignments == null || assignments.isEmpty() ? null : assignments.get(assignments.size() - 1);
    }

    /**
     * 派工是否在执行一轮：最近送达晚于最近一轮结束
     */
    public static boolean busy(TodoAssignment assignment) {
        Date delivered = assignment.getDeliveredTime();
        Date idle = assignment.getIdleTime();
        return delivered != null && (idle == null || delivered.after(idle));
    }

    /**
     * 受阻/失败报告之后是否还没有人的新消息
     */
    private static boolean waitsForPerson(TodoAssignment assignment, List<TodoInput> inputs) {
        Date reportTime = assignment.getReportTime();
        if (reportTime == null || inputs == null) {
            return true;
        }
        for (TodoInput input : inputs) {
            if (TodoConstant.INPUT_TRIGGER.equals(input.getKind()) && input.getHuman() == 1
                    && input.getEventTime() != null && input.getEventTime().after(reportTime)) {
                return false;
            }
        }
        return true;
    }

}
