package com.xxl.ai.api.business.todo.service;

import com.xxl.ai.api.business.harness.superagent.WorkerReportHandler;
import com.xxl.ai.api.business.todo.model.dto.TodoCandidate;
import com.xxl.ai.api.business.todo.model.dto.TodoRound;
import com.xxl.ai.api.business.todo.model.dto.TodoSubmitDTO;
import com.xxl.tool.response.Response;

import java.util.Map;

/**
 * 超级助理 引擎：触发、线程、决定、工单、派工的唯一写入入口
 *
 * 每个命令一个事务；锁顺序固定为“先线程、后工单”，避免死锁。副作用（研判、启动、投递、发消息）在事务内写入队列，
 * 提交后由调度器执行。同时实现 Worker 上报回调，Worker 的上报同样经引擎落库。
 *
 * @author xxl-ai 2026-11-10
 */
public interface TodoEngineService extends WorkerReportHandler {

    /**
     * 提交（新建、追加、回答）：按 requestId 幂等，返回待办ID
     */
    Response<Long> submit(int userId, TodoSubmitDTO dto);

    /**
     * 按 requestId 查询已提交的待办ID（并发重复提交时回读），不属于该用户时返回 null
     */
    Long findSubmitted(int userId, String requestId);

    /**
     * 开始线程的一次研判：取一批待研判触发 → 准入 → 写入进行中 Eval → 入队研判
     */
    void startEval(long threadId);

    /**
     * 取研判候选，并从此刻开始计算超时；Eval 已结束时返回 null
     */
    TodoCandidate candidate(long threadId, String evalId);

    /**
     * 记录研判结果：接单时建工单与派工，并入队启动
     */
    void decide(long threadId, String evalId, Map<String, String> assessment);

    /**
     * 记录研判未完成（模型不可用、调用失败）
     */
    void unavailable(long threadId, String evalId, String reasonCode, String text);

    /**
     * Eval 超时：重新排队，连续超时超过上限记 unavailable
     */
    void expireEval(long threadId);

    /**
     * 开始 Worker 一轮执行：标记派工执行中、把未投递输入交给本轮；无事可做时返回 null
     */
    TodoRound startRound(long ticketId, Long assignmentId, boolean launch);

    /**
     * 结束 Worker 一轮执行：保存回复、标记输入已读、补齐报告
     */
    void endRound(TodoRound round, String reply, String error, boolean timeout);

    /**
     * 人工停止工单
     */
    Response<String> stop(int userId, long ticketId);

    /**
     * 人工重试：结束当前派工并新建派工
     */
    Response<String> retry(int userId, long ticketId);

    /**
     * 自动重试（watchdog）：仅对可恢复中断生效，单工单次数受限；人工已推进的工单不介入
     *
     * @param ticketId 工单ID
     * @param reason   重试原因（用于通知文案）
     * @param note     追加到下一轮的纠正提示
     * @return 是否已自动重试
     */
    boolean autoRetry(long ticketId, String reason, String note);

    /**
     * 人工重新研判一条触发
     */
    Response<String> requeue(int userId, long triggerId);

    /**
     * 忽略一条未完成决定
     */
    Response<String> dismiss(int userId, long decisionId);

    /**
     * 投递一条 Web 渠道消息（写入收件箱）
     */
    void deliverMessage(String messageKey);

    /**
     * 启动恢复：执行中被打断的派工以 hostRestarted 结束，新建派工并附恢复说明
     */
    void recover();

}
