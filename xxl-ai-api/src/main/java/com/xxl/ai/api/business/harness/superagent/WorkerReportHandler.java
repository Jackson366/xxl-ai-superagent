package com.xxl.ai.api.business.harness.superagent;

import java.util.Map;

/**
 * 超级助理 Worker 上报回调（harness 定义、business 实现，harness 不直接写 business 表）
 *
 * 每个方法对应一个 Worker 内置工具；入参为模型给出的工具参数（外部数据，须由实现方校验），
 * 返回值作为工具结果交还模型。工单已结束时，实现方返回拒绝说明，不做任何写入。
 *
 * @author xxl-ai 2026-11-10
 */
public interface WorkerReportHandler {

    /**
     * todo_read：读取本工单的触发、输入、历史报告
     */
    String read(long ticketId, Map<String, Object> args);

    /**
     * todo_ack：开始实质工作前确认一条人工请求
     */
    String ack(long ticketId, Map<String, Object> args);

    /**
     * todo_report：上报进展或结果（progress/ready/blocked/failed/dropped），可附带一个向人提问
     */
    String report(long ticketId, long assignmentId, Map<String, Object> args);

    /**
     * todo_handback：把超出本工单范围的追问退回研判，每条只能一次
     */
    String handback(long ticketId, Map<String, Object> args);

}
