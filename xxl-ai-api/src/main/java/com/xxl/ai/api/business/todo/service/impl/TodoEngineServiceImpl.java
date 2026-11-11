package com.xxl.ai.api.business.todo.service.impl;

import com.xxl.ai.api.business.todo.constant.TodoConstant;
import com.xxl.ai.api.business.todo.mapper.TodoAssignmentMapper;
import com.xxl.ai.api.business.todo.mapper.TodoDecisionMapper;
import com.xxl.ai.api.business.todo.mapper.TodoEffectMapper;
import com.xxl.ai.api.business.todo.mapper.TodoInputMapper;
import com.xxl.ai.api.business.todo.mapper.TodoMessageMapper;
import com.xxl.ai.api.business.todo.mapper.TodoThreadMapper;
import com.xxl.ai.api.business.todo.mapper.TodoTicketMapper;
import com.xxl.ai.api.business.todo.mapper.TodoTriggerMapper;
import com.xxl.ai.api.business.todo.mapper.TodoWorkerMsgMapper;
import com.xxl.ai.api.business.todo.model.dto.TodoCandidate;
import com.xxl.ai.api.business.todo.model.dto.TodoRound;
import com.xxl.ai.api.business.todo.model.dto.TodoSubmitDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoWakeEvent;
import com.xxl.ai.api.business.todo.model.entity.TodoAssignment;
import com.xxl.ai.api.business.todo.model.entity.TodoDecision;
import com.xxl.ai.api.business.todo.model.entity.TodoEffect;
import com.xxl.ai.api.business.todo.model.entity.TodoInput;
import com.xxl.ai.api.business.todo.model.entity.TodoMessage;
import com.xxl.ai.api.business.todo.model.entity.TodoThread;
import com.xxl.ai.api.business.todo.model.entity.TodoTicket;
import com.xxl.ai.api.business.todo.model.entity.TodoTrigger;
import com.xxl.ai.api.business.todo.model.entity.TodoWorkerMsg;
import com.xxl.ai.api.business.todo.model.json.TodoAttention;
import com.xxl.ai.api.business.todo.model.json.TodoDisposition;
import com.xxl.ai.api.business.todo.model.json.TodoEvaluating;
import com.xxl.ai.api.business.todo.model.json.TodoMandate;
import com.xxl.ai.api.business.todo.model.json.TodoReason;
import com.xxl.ai.api.business.todo.model.json.TodoSettingsContent;
import com.xxl.ai.api.business.todo.model.json.TodoToolsSnapshot;
import com.xxl.ai.api.business.todo.model.json.TodoWorkerModel;
import com.xxl.ai.api.business.todo.service.TodoEngineService;
import com.xxl.ai.api.business.todo.service.TodoSettingsService;
import com.xxl.ai.api.business.todo.service.TodoObservationService;
import com.xxl.ai.api.business.harness.superagent.SuperagentTraceEvent;
import com.xxl.ai.api.business.todo.util.TodoJsonTool;
import com.xxl.ai.api.business.todo.util.TodoStageTool;
import com.xxl.ai.api.framework.constant.enums.XxlRoleEnum;
import com.xxl.ai.api.framework.mapper.UserMapper;
import com.xxl.ai.api.framework.model.entity.User;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 超级助理 引擎实现
 *
 * 锁顺序固定为“先线程、后工单”（lockTicket 先按工单查线程再加锁），所有命令共用，避免死锁。
 * 副作用只入队、不执行；事务提交后由 TodoWakeEvent 唤醒调度器。
 *
 * @author xxl-ai 2026-11-10
 */
@Service
public class TodoEngineServiceImpl implements TodoEngineService {

    private static final Logger logger = LoggerFactory.getLogger(TodoEngineServiceImpl.class);

    /** 提交内容最大长度 */
    private static final int CONTENT_MAX = 4000;
    /** 已结束工单的工具调用统一拒绝说明 */
    private static final String ENDED_REFUSAL = "工单已结束，新的工作需要新的工单";
    /** 重启恢复说明：提醒 Worker 先核对已做的事，避免重复对外动作 */
    private static final String RECOVERY_NOTE = "服务重启打断了上一轮执行。重复任何对外动作之前，先用 todo_read 核对本工单已经做过的事。";
    /** 合法的报告状态 */
    private static final Set<String> REPORT_STATES = Set.of(TodoConstant.REPORT_PROGRESS, TodoConstant.REPORT_READY,
            TodoConstant.REPORT_BLOCKED, TodoConstant.REPORT_FAILED, TodoConstant.REPORT_DROPPED);

    @Resource
    private TodoTriggerMapper triggerMapper;
    @Resource
    private TodoThreadMapper threadMapper;
    @Resource
    private TodoDecisionMapper decisionMapper;
    @Resource
    private TodoTicketMapper ticketMapper;
    @Resource
    private TodoAssignmentMapper assignmentMapper;
    @Resource
    private TodoInputMapper inputMapper;
    @Resource
    private TodoWorkerMsgMapper workerMsgMapper;
    @Resource
    private TodoEffectMapper effectMapper;
    @Resource
    private TodoMessageMapper messageMapper;
    @Resource
    private TodoSettingsService settingsService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private TodoObservationService observation;

    // ---------------------- 提交 ----------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> submit(int userId, TodoSubmitDTO dto) {
        return submit(userId, dto, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> submitFromAssistant(int userId, TodoSubmitDTO dto, Long targetTicketId) {
        return submit(userId, dto, targetTicketId, true);
    }

    private Response<Long> submit(int userId, TodoSubmitDTO dto, Long targetTicketId, boolean assistantConfirmed) {
        if (dto == null || StringTool.isBlank(dto.getRequestId()) || dto.getRequestId().length() > 64) {
            return Response.ofFail("请求标识不合法");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            return Response.ofFail("内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            return Response.ofFail("内容不能超过 " + CONTENT_MAX + " 字");
        }

        // 幂等：同一 requestId 只受理一次
        Long submitted = findSubmitted(userId, dto.getRequestId());
        if (submitted != null) {
            return Response.ofSuccess(submitted);
        }
        if (triggerMapper.loadByKey(dto.getRequestId()) != null) {
            return Response.ofFail("请求标识冲突");
        }

        Date now = new Date();
        String kind = dto.getKind();
        TodoThread thread;
        if (targetTicketId != null && TodoConstant.TRIGGER_CREATE.equals(kind)) {
            return Response.ofFail("新建待办不能指定原工单");
        }
        if (TodoConstant.TRIGGER_CREATE.equals(kind)) {
            thread = new TodoThread();
            thread.setUserId(userId);
            thread.setTitle(TodoJsonTool.abbreviate(firstLine(content), 100));
            thread.setLastTime(now);
            threadMapper.insert(thread);
        } else if (TodoConstant.TRIGGER_APPEND.equals(kind) || TodoConstant.TRIGGER_ANSWER.equals(kind)) {
            thread = dto.getThreadId() == null ? null : threadMapper.loadForUpdate(dto.getThreadId());
            if (thread == null || thread.getUserId() != userId) {
                return Response.ofFail("待办不存在");
            }
        } else {
            return Response.ofFail("提交类型不合法");
        }

        // 回答：必须对应本待办里仍在等待的提问
        Long answeredTicketId = null;
        if (TodoConstant.TRIGGER_ANSWER.equals(kind)) {
            answeredTicketId = findAttentionTicket(thread.getId(), dto.getAttentionId());
            if (answeredTicketId == null) {
                return Response.ofFail("该问题已回答或已失效");
            }
        }

        if (targetTicketId != null) {
            TodoTicket target = ticketMapper.loadForUpdate(targetTicketId);
            if (target == null || target.getThreadId() != thread.getId() || target.getUserId() != userId
                    || !TodoJsonTool.longList(thread.getSubscriptions()).contains(targetTicketId)
                    || TodoConstant.ENDED_BY_HUMAN.equals(target.getEndedBy())
                    || (target.getEndedTime() != null && !revalidate(target))
                    || (answeredTicketId != null && !targetTicketId.equals(answeredTicketId))) {
                return Response.ofFail("原工单或提问状态已变化，请重新提交");
            }
        }

        TodoTrigger trigger = new TodoTrigger();
        trigger.setTriggerKey(dto.getRequestId());
        trigger.setThreadId(thread.getId());
        trigger.setUserId(userId);
        trigger.setKind(kind);
        trigger.setAttentionId(TodoConstant.TRIGGER_ANSWER.equals(kind) ? dto.getAttentionId() : null);
        trigger.setContent(content);
        trigger.setEventTime(now);
        // 并发重复提交时唯一索引冲突，整个事务回滚，由调用方回读已受理结果
        triggerMapper.insert(trigger);

        observation.append(thread.getId(), null, null, null, null,
                new SuperagentTraceEvent("trigger.received", "success", kind,
                        TodoJsonTool.toJson(Map.of("triggerId", trigger.getId(), "content", content)), null, null));
        thread.setLastTime(now);
        route(thread, trigger, answeredTicketId, targetTicketId, assistantConfirmed);
        threadMapper.update(thread);
        wake(null);
        return Response.ofSuccess(thread.getId());
    }

    @Override
    public Long findSubmitted(int userId, String requestId) {
        TodoTrigger exist = StringTool.isBlank(requestId) ? null : triggerMapper.loadByKey(requestId);
        return exist != null && exist.getUserId() == userId ? exist.getThreadId() : null;
    }

    /**
     * 路由一条触发：送达订阅本线程的工单（已结束的先重新校验授权再恢复）；没有工单接手时进入研判队列
     */
    private void route(TodoThread thread, TodoTrigger trigger, Long answeredTicketId, Long targetTicketId, boolean assistantConfirmed) {
        TodoDisposition disposition = new TodoDisposition();
        disposition.assistantConfirmed = assistantConfirmed;
        disposition.targetTicketId = targetTicketId;
        for (Long ticketId : TodoJsonTool.longList(thread.getSubscriptions())) {
            if (targetTicketId != null && !targetTicketId.equals(ticketId)) {
                continue;
            }
            TodoTicket ticket = ticketMapper.loadForUpdate(ticketId);
            // 人工停止的工单不再接收任何消息
            if (ticket == null || TodoConstant.ENDED_BY_HUMAN.equals(ticket.getEndedBy())) {
                continue;
            }
            boolean resumed = false;
            if (ticket.getEndedTime() != null) {
                // 授权不再成立时不恢复，消息按未送达处理，进入研判
                if (targetTicketId == null && !revalidate(ticket)) {
                    continue;
                }
                reopen(ticket, trigger.getId());
                resumed = true;
            }
            if (answeredTicketId != null && answeredTicketId == ticket.getId()) {
                ticket.setAttention(null);
            }
            addInput(ticket, TodoConstant.INPUT_TRIGGER, trigger.getId(), true, null);
            ticketMapper.update(ticket);
            // 恢复的工单由启动派工带上输入；进行中的工单投递给当前派工
            if (!resumed) {
                enqueue(TodoConstant.EFFECT_DELIVERY, String.valueOf(ticket.getId()), Map.of("ticketId", ticket.getId()));
            }
            disposition.fanout.add(ticket.getId());
            observation.append(thread.getId(), null, ticket.getId(), null, null,
                    new SuperagentTraceEvent("input.delivered", "success", String.valueOf(trigger.getId()), null, null, null));
        }
        if (disposition.fanout.isEmpty()) {
            List<Long> pending = TodoJsonTool.longList(thread.getPending());
            pending.add(trigger.getId());
            thread.setPending(TodoJsonTool.longListJson(pending));
        }
        triggerMapper.updateDisposition(trigger.getId(), TodoJsonTool.toJson(disposition));
    }

    /**
     * 查找本线程中提问ID匹配且仍在等待回答的工单
     */
    private Long findAttentionTicket(long threadId, String attentionId) {
        if (StringTool.isBlank(attentionId)) {
            return null;
        }
        for (TodoTicket ticket : ticketMapper.listByThread(threadId)) {
            TodoAttention attention = TodoJsonTool.fromJson(ticket.getAttention(), TodoAttention.class);
            if (ticket.getEndedTime() == null && attention != null && attentionId.equals(attention.id)) {
                return ticket.getId();
            }
        }
        return null;
    }

    // ---------------------- 研判 ----------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startEval(long threadId) {
        TodoThread thread = threadMapper.loadForUpdate(threadId);
        if (thread == null || thread.getEvaluating() != null) {
            return;
        }
        List<Long> pending = TodoJsonTool.longList(thread.getPending());
        if (pending.isEmpty()) {
            return;
        }
        thread.setPending(null);

        // 1、筛选：已归工单决定的追问离开队列，不产生决定；已发起过工单的触发记忽略
        List<TodoTrigger> batch = new ArrayList<>();
        List<Long> started = new ArrayList<>();
        for (TodoTrigger trigger : triggerMapper.listByIds(pending)) {
            TodoDisposition disposition = disposition(trigger);
            if (!disposition.requeued && ownedByLive(disposition)) {
                continue;
            }
            if (ticketMapper.loadByOriginTrigger(trigger.getId()) != null) {
                started.add(trigger.getId());
                continue;
            }
            batch.add(trigger);
        }
        if (!started.isEmpty()) {
            record(thread, newEvalId(threadId), started, TodoConstant.DECISION_IGNORE, TodoConstant.STRENGTH_EXPLICIT,
                    reason(TodoConstant.REASON_STARTED_TICKET, null), null, null);
        }
        if (batch.isEmpty()) {
            threadMapper.update(thread);
            return;
        }

        // 2、系统未就绪：整批记未完成，配置好后可在面板重新研判
        String readyCode = settingsService.readyCode();
        if (readyCode != null) {
            record(thread, newEvalId(threadId), ids(batch), TodoConstant.DECISION_UNAVAILABLE, TodoConstant.STRENGTH_EXPLICIT,
                    reason(readyCode, null), null, null);
            threadMapper.update(thread);
            return;
        }

        // 3、准入（确定性）：提交人仍是有效管理员；Web 表单直接提交即明确请求
        List<TodoTrigger> admitted = new ArrayList<>();
        List<Long> refused = new ArrayList<>();
        String refusedCode = null;
        for (TodoTrigger trigger : batch) {
            String code = gate(trigger.getUserId());
            if (code == null) {
                admitted.add(trigger);
            } else {
                refused.add(trigger.getId());
                refusedCode = code;
            }
        }
        if (!refused.isEmpty()) {
            record(thread, newEvalId(threadId), refused, TodoConstant.DECISION_IGNORE, TodoConstant.STRENGTH_EXPLICIT,
                    reason(refusedCode, null), null, null);
        }
        if (admitted.isEmpty()) {
            threadMapper.update(thread);
            return;
        }

        // 4、开始 Eval：候选交给 Router 之前不计超时
        long now = System.currentTimeMillis();
        TodoEvaluating evaluating = new TodoEvaluating();
        evaluating.id = newEvalId(threadId);
        evaluating.triggerIds = ids(admitted);
        // 助理入口明确确认过的首条内容是该工单授权，后续提交不能覆盖它
        evaluating.primary = admitted.stream()
                .filter(trigger -> TodoConstant.TRIGGER_CREATE.equals(trigger.getKind()) && disposition(trigger).assistantConfirmed)
                .findFirst().orElse(admitted.get(admitted.size() - 1)).getId();
        evaluating.strength = TodoConstant.STRENGTH_EXPLICIT;
        evaluating.policyVersion = settingsService.policyVersion();
        evaluating.startedAt = now;
        evaluating.deadline = now + TodoConstant.EVAL_TIMEOUT_SECONDS * 1000L;
        thread.setEvaluating(TodoJsonTool.toJson(evaluating));
        threadMapper.update(thread);
        for (Long triggerId : evaluating.triggerIds) {
            updateDisposition(triggerId, item -> item.evalId = evaluating.id);
        }
        observation.append(threadId, evaluating.id, null, null, null,
                new SuperagentTraceEvent("router.queued", "queued", null,
                        TodoJsonTool.toJson(Map.of("triggerIds", evaluating.triggerIds, "policyVersion", evaluating.policyVersion)), null, null));
        enqueue(TodoConstant.EFFECT_ASSESSMENT, evaluating.id, Map.of("threadId", threadId));
        wake(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoCandidate candidate(long threadId, String evalId) {
        TodoThread thread = threadMapper.loadForUpdate(threadId);
        TodoEvaluating evaluating = thread == null ? null : evaluating(thread, evalId);
        if (evaluating == null) {
            return null;
        }
        // 超时从候选交给 Router 起计
        long now = System.currentTimeMillis();
        evaluating.delivered = now;
        evaluating.deadline = now + TodoConstant.EVAL_TIMEOUT_SECONDS * 1000L;
        thread.setEvaluating(TodoJsonTool.toJson(evaluating));
        threadMapper.update(thread);

        TodoSettingsContent.Model model = settingsService.model();
        return new TodoCandidate(evaluating.policyVersion, settingsService.prompts().router, model.spaceId,
                model.routerSupplierId, model.routerModelId, candidateCard(thread, evaluating));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decide(long threadId, String evalId, Map<String, String> assessment) {
        TodoThread thread = threadMapper.loadForUpdate(threadId);
        TodoEvaluating evaluating = thread == null ? null : evaluating(thread, evalId);
        if (evaluating == null) {
            return;
        }
        thread.setEvaluating(null);

        // 1、研判期间策略变更：记未完成，并按新策略立即重排
        if (!evaluating.policyVersion.equals(settingsService.policyVersion())) {
            record(thread, evalId, evaluating.triggerIds, TodoConstant.DECISION_UNAVAILABLE, evaluating.strength,
                    reason(TodoConstant.REASON_POLICY_CHANGED, null), null, null);
            requeueFront(thread, evaluating.triggerIds);
            threadMapper.update(thread);
            wake(null);
            return;
        }

        String reasonText = TodoJsonTool.abbreviate(assessment.get("reason"), 500);
        String title = assessment.get("title");
        String scope = assessment.get("scope");
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("routerDecision", assessment.get("decision"));
        audit.put("title", title);
        audit.put("scope", scope);
        boolean accept = TodoConstant.DECISION_ACCEPT.equals(assessment.get("decision"));

        // 2、推断请求只在 auto 模式接单；observe 模式记“会接单”
        if (accept && TodoConstant.STRENGTH_INFERRED.equals(evaluating.strength)
                && !TodoConstant.MODE_AUTO.equals(settingsService.policy().mode)) {
            audit.put("wouldAccept", true);
            record(thread, evalId, evaluating.triggerIds, TodoConstant.DECISION_IGNORE, evaluating.strength,
                    reason(TodoConstant.REASON_OBSERVED, reasonText), null, audit);
            threadMapper.update(thread);
            return;
        }
        if (!accept) {
            record(thread, evalId, evaluating.triggerIds, TodoConstant.DECISION_IGNORE, evaluating.strength,
                    reason(TodoConstant.REASON_SAID, reasonText), null, audit);
            threadMapper.update(thread);
            return;
        }

        // 3、接单：决定 → 工单 → 输入 → 订阅 → 派工
        TodoTrigger primary = triggerMapper.load(evaluating.primary);
        TodoMandate mandate = new TodoMandate();
        mandate.strength = evaluating.strength;
        mandate.policyVersion = evaluating.policyVersion;
        mandate.basis = TodoConstant.BASIS_ADDRESSED;
        mandate.actorUserId = primary.getUserId();
        if (disposition(primary).assistantConfirmed) {
            mandate.scope = primary.getContent();
        } else {
            mandate.scope = StringTool.isNotBlank(scope) ? TodoJsonTool.abbreviate(scope, 1000)
                    : TodoJsonTool.abbreviate(primary.getContent(), 1000);
        }
        TodoDecision decision = record(thread, evalId, evaluating.triggerIds, TodoConstant.DECISION_ACCEPT,
                evaluating.strength, reason(TodoConstant.REASON_SAID, reasonText), mandate, audit);

        TodoTicket ticket = new TodoTicket();
        ticket.setUserId(thread.getUserId());
        ticket.setThreadId(threadId);
        ticket.setDecisionId(decision.getId());
        ticket.setOriginTriggerId(primary.getId());
        ticket.setTitle(StringTool.isNotBlank(title) ? TodoJsonTool.abbreviate(title, 100) : thread.getTitle());
        ticket.setMandate(TodoJsonTool.toJson(mandate));
        ticketMapper.insert(ticket);
        decisionMapper.updateTicketId(decision.getId(), ticket.getId());
        observation.append(threadId, evalId, ticket.getId(), null, null,
                new SuperagentTraceEvent("ticket.created", "success", ticket.getTitle(), mandate.scope, null, null));

        for (Long triggerId : evaluating.triggerIds) {
            addInput(ticket, TodoConstant.INPUT_TRIGGER, triggerId, true, null);
            updateDisposition(triggerId, item -> item.fanout.add(ticket.getId()));
        }
        ticketMapper.update(ticket);

        List<Long> subscriptions = TodoJsonTool.longList(thread.getSubscriptions());
        subscriptions.add(ticket.getId());
        thread.setSubscriptions(TodoJsonTool.longListJson(subscriptions));
        threadMapper.update(thread);

        newAssignment(ticket);
        wake(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unavailable(long threadId, String evalId, String reasonCode, String text) {
        TodoThread thread = threadMapper.loadForUpdate(threadId);
        TodoEvaluating evaluating = thread == null ? null : evaluating(thread, evalId);
        if (evaluating == null) {
            return;
        }
        thread.setEvaluating(null);
        record(thread, evalId, evaluating.triggerIds, TodoConstant.DECISION_UNAVAILABLE, evaluating.strength,
                reason(reasonCode, TodoJsonTool.abbreviate(text, 500)), null, null);
        threadMapper.update(thread);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireEval(long threadId) {
        TodoThread thread = threadMapper.loadForUpdate(threadId);
        TodoEvaluating evaluating = thread == null ? null : TodoJsonTool.fromJson(thread.getEvaluating(), TodoEvaluating.class);
        // 未交给 Router 的候选不超时
        if (evaluating == null || evaluating.delivered == 0 || evaluating.deadline > System.currentTimeMillis()) {
            return;
        }
        thread.setEvaluating(null);
        effectMapper.deleteByKey(TodoConstant.EFFECT_ASSESSMENT, evaluating.id);
        int expired = thread.getExpired() + 1;
        if (expired > TodoConstant.EVAL_REQUEUES) {
            record(thread, evaluating.id, evaluating.triggerIds, TodoConstant.DECISION_UNAVAILABLE, evaluating.strength,
                    reason(TodoConstant.REASON_TIMEOUT, null), null, null);
        } else {
            // Router 忙时不丢请求：重新排到队首
            thread.setExpired(expired);
            requeueFront(thread, evaluating.triggerIds);
        }
        threadMapper.update(thread);
        wake(null);
    }

    // ---------------------- Worker 轮次 ----------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodoRound startRound(long ticketId, Long assignmentId, boolean launch) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getEndedTime() != null) {
            return null;
        }
        TodoTicket ticket = locked.ticket();
        TodoAssignment latest = TodoStageTool.latest(assignmentMapper.listByTicket(ticketId));
        if (latest == null) {
            return null;
        }
        Date now = new Date();
        if (launch) {
            // 只启动最新且待启动的派工；已被重试或停止替换的派工不再启动
            if (assignmentId == null || latest.getId() != assignmentId || !TodoConstant.ASSIGNMENT_PENDING.equals(latest.getState())) {
                return null;
            }
            latest.setState(TodoConstant.ASSIGNMENT_RUNNING);
            latest.setStartedTime(now);
        } else if (!TodoConstant.ASSIGNMENT_RUNNING.equals(latest.getState()) || TodoStageTool.busy(latest)) {
            // 投递只交给空闲的派工；正在执行的一轮结束时会再次入队投递
            return null;
        }
        List<Long> inputIds = TodoJsonTool.longList(ticket.getUndelivered());
        if (!launch && inputIds.isEmpty()) {
            return null;
        }

        // 1、Worker 模型：工单首次执行时快照，之后不变
        TodoWorkerModel workerModel = TodoJsonTool.fromJson(ticket.getWorkerModel(), TodoWorkerModel.class);
        if (workerModel == null) {
            TodoSettingsContent.Model model = settingsService.model();
            workerModel = new TodoWorkerModel();
            workerModel.spaceId = model.spaceId;
            workerModel.supplierId = model.workerSupplierId;
            workerModel.modelId = model.workerModelId;
            ticket.setWorkerModel(TodoJsonTool.toJson(workerModel));
        }

        // 2、本轮输入与会话历史（一工单一会话，跨派工保留）
        List<TodoInput> inputs = inputIds.isEmpty() ? new ArrayList<>() : inputMapper.listByIds(inputIds);
        String content = roundContent(inputs);
        List<String> roleList = new ArrayList<>();
        List<String> contentList = new ArrayList<>();
        for (TodoWorkerMsg msg : workerMsgMapper.listByTicket(ticketId)) {
            roleList.add(msg.getRole());
            contentList.add(msg.getContent());
        }

        // 3、标记送达：输入交给本派工，读取时间在本轮结束时记录
        for (TodoInput input : inputs) {
            input.setDeliveredTo(latest.getId());
            inputMapper.update(input);
        }
        latest.setDeliveredTime(now);
        assignmentMapper.update(latest);
        ticket.setUndelivered(null);
        ticket.setDeliveryError(null);
        ticketMapper.update(ticket);
        saveWorkerMsg(ticketId, latest.getId(), "user", content, inputIds);

        TodoToolsSnapshot tools = TodoJsonTool.fromJson(latest.getTools(), TodoToolsSnapshot.class);
        if (tools == null) {
            tools = new TodoToolsSnapshot();
        }
        String roundId = UUID.randomUUID().toString();
        observation.append(ticket.getThreadId(), null, ticketId, latest.getId(), roundId,
                new SuperagentTraceEvent("round.started", "running", String.valueOf(workerModel.modelId),
                        TodoJsonTool.toJson(Map.of("inputIds", inputIds, "modelId", workerModel.modelId,
                                "supplierId", workerModel.supplierId)), null, null));
        return new TodoRound(roundId, ticketId, latest.getId(), launch, workerModel.spaceId, workerModel.supplierId,
                workerModel.modelId, join(tools.mcpIds), join(tools.skillIds), workerPrompt(ticket), roleList,
                contentList, content, inputIds, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endRound(TodoRound round, String reply, String error, boolean timeout) {
        LockedTicket locked = lockTicket(round.ticketId());
        if (locked == null) {
            return;
        }
        TodoTicket ticket = locked.ticket();
        Date now = new Date();
        TodoAssignment current = assignmentMapper.load(round.assignmentId());
        String roundStatus = current != null && "stopped".equals(current.getEndedReason()) ? "cancelled"
                : timeout ? "timeout" : error == null ? "success" : "failed";
        observation.append(ticket.getThreadId(), null, ticket.getId(), round.assignmentId(), round.roundId(),
                new SuperagentTraceEvent("round.ended", roundStatus, null, error,
                        null, Math.max(0, now.getTime() - round.startedAt().getTime())));
        // 已取消轮次的迟到内容不能进入同一工单后续派工的模型上下文
        if ("cancelled".equals(roundStatus)) {
            return;
        }
        if (StringTool.isNotBlank(reply)) {
            saveWorkerMsg(ticket.getId(), round.assignmentId(), "assistant", reply, null);
        }
        // 本轮带上的输入视为已读
        if (!round.inputIds().isEmpty()) {
            for (TodoInput input : inputMapper.listByIds(round.inputIds())) {
                input.setReadTime(now);
                inputMapper.update(input);
            }
        }

        TodoAssignment assignment = assignmentMapper.load(round.assignmentId());
        // 已被停止、重试或已结束的派工不再补报告
        if (assignment == null || !TodoConstant.ASSIGNMENT_RUNNING.equals(assignment.getState())) {
            return;
        }
        assignment.setIdleTime(now);
        boolean reported = assignment.getReportTime() != null && !assignment.getReportTime().before(round.startedAt());
        if (error != null) {
            setReport(assignment, TodoConstant.REPORT_FAILED, TodoJsonTool.abbreviate(error, 500), now);
            sendMessage(ticket, TodoConstant.PURPOSE_NOTICE, "report-" + ticket.getId() + "-" + assignment.getId() + "-" + now.getTime(),
                    "执行失败：" + TodoJsonTool.abbreviate(error, 500), null);
        } else if (timeout) {
            setReport(assignment, TodoConstant.REPORT_WAITING, "本轮执行超过 " + TodoConstant.BACKGROUND_LIMIT_MINUTES + " 分钟，已结束本轮", now);
        } else if (!reported || TodoConstant.REPORT_PROGRESS.equals(assignment.getReportState())) {
            // 本轮结束且没有交接给人：视为等待新消息
            String summary = StringTool.isNotBlank(reply) ? TodoJsonTool.abbreviate(reply, 500) : assignment.getReportSummary();
            setReport(assignment, TodoConstant.REPORT_WAITING, summary, now);
        }
        assignmentMapper.update(assignment);

        // 本轮期间又有新输入：继续投递
        if (!TodoJsonTool.longList(ticket.getUndelivered()).isEmpty()) {
            enqueue(TodoConstant.EFFECT_DELIVERY, String.valueOf(ticket.getId()), Map.of("ticketId", ticket.getId()));
            wake(null);
        }
    }

    // ---------------------- Worker 上报 ----------------------

    @Override
    @Transactional(readOnly = true)
    public String read(long ticketId, Map<String, Object> args) {
        TodoTicket ticket = ticketMapper.load(ticketId);
        if (ticket == null || ticket.getEndedTime() != null) {
            return refusal(ENDED_REFUSAL);
        }
        String resource = TodoJsonTool.text(args, "resource");
        int offset = Math.max(0, TodoJsonTool.intValue(args, "offset", 0));
        int limit = Math.min(50, Math.max(1, TodoJsonTool.intValue(args, "limit", 20)));
        List<Map<String, Object>> items = new ArrayList<>();
        if ("triggers".equals(resource)) {
            for (TodoTrigger trigger : triggerMapper.listByThread(ticket.getThreadId())) {
                if (reached(ticket, trigger)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("trigger_id", trigger.getId());
                    item.put("kind", trigger.getKind());
                    item.put("content", trigger.getContent());
                    item.put("time", DateTool.formatDateTime(trigger.getEventTime()));
                    items.add(item);
                }
            }
        } else if ("inputs".equals(resource)) {
            for (TodoInput input : inputMapper.listByTicket(ticketId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("kind", input.getKind());
                item.put("trigger_id", input.getTriggerId());
                item.put("content", input.getContent());
                item.put("read", input.getReadTime() != null);
                item.put("time", DateTool.formatDateTime(input.getEventTime()));
                items.add(item);
            }
        } else if ("reports".equals(resource)) {
            for (TodoAssignment assignment : assignmentMapper.listByTicket(ticketId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("assignment_id", assignment.getId());
                item.put("state", assignment.getState());
                item.put("report", assignment.getReportState());
                item.put("summary", assignment.getReportSummary());
                item.put("ended_reason", assignment.getEndedReason());
                items.add(item);
            }
        } else {
            return refusal("resource 只能是 triggers、inputs、reports");
        }
        Map<String, Object> page = new LinkedHashMap<>();
        int end = Math.min(items.size(), offset + limit);
        page.put("items", offset >= items.size() ? new ArrayList<>() : items.subList(offset, end));
        page.put("next_offset", end < items.size() ? end : null);
        return TodoJsonTool.toJson(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String ack(long ticketId, Map<String, Object> args) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getEndedTime() != null) {
            return refusal(ENDED_REFUSAL);
        }
        TodoTicket ticket = locked.ticket();
        Long triggerId = TodoJsonTool.id(args, "trigger_id");
        String text = TodoJsonTool.text(args, "text");
        if (triggerId == null || text == null) {
            return refusal("trigger_id 与 text 不能为空");
        }
        TodoTrigger trigger = triggerMapper.load(triggerId);
        if (trigger == null || !reached(ticket, trigger)) {
            return refusal("该触发不属于本工单");
        }
        TodoDisposition disposition = disposition(trigger);
        if (disposition.acked.contains(ticketId)) {
            return TodoJsonTool.toJson(Map.of("acknowledged", true, "reused", true));
        }
        observation.append(ticket.getThreadId(), null, ticketId, null, null,
                new SuperagentTraceEvent("input.acknowledged", "success", String.valueOf(triggerId), text, null, null));
        disposition.acked.add(ticketId);
        triggerMapper.updateDisposition(triggerId, TodoJsonTool.toJson(disposition));
        sendMessage(ticket, TodoConstant.PURPOSE_ACKNOWLEDGEMENT, "ack-" + ticketId + "-" + triggerId,
                TodoJsonTool.abbreviate(text, 500), triggerId);
        wake(null);
        return TodoJsonTool.toJson(Map.of("acknowledged", true, "reused", false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String report(long ticketId, long assignmentId, Map<String, Object> args) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getEndedTime() != null) {
            return refusal(ENDED_REFUSAL);
        }
        TodoTicket ticket = locked.ticket();
        String state = TodoJsonTool.text(args, "state");
        String summary = TodoJsonTool.abbreviate(TodoJsonTool.text(args, "summary"), 4000);
        if (state == null || !REPORT_STATES.contains(state) || summary == null) {
            return refusal("state 只能是 progress、ready、blocked、failed、dropped，summary 不能为空");
        }
        TodoAssignment assignment = assignmentMapper.load(assignmentId);
        if (assignment == null || assignment.getTicketId() != ticketId || !TodoConstant.ASSIGNMENT_RUNNING.equals(assignment.getState())) {
            return refusal("本次派工已结束");
        }
        Date now = new Date();
        setReport(assignment, state, summary, now);

        // 1、提问：省略保持原问题，显式传 null 清除
        if (args.containsKey("human_action")) {
            String question = TodoJsonTool.text(args, "human_action");
            if (question == null) {
                ticket.setAttention(null);
            } else {
                TodoAttention attention = new TodoAttention();
                attention.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                attention.question = TodoJsonTool.abbreviate(question, 1000);
                attention.triggerId = latestHumanTrigger(ticketId);
                ticket.setAttention(TodoJsonTool.toJson(attention));
                sendMessage(ticket, TodoConstant.PURPOSE_ATTENTION, "attention-" + ticketId + "-" + attention.id,
                        attention.question, attention.triggerId);
            }
        }

        // 2、回答人的明确提问
        Long replyTo = TodoJsonTool.id(args, "reply_to_trigger_id");
        TodoTrigger replyTrigger = replyTo == null ? null : triggerMapper.load(replyTo);
        if (replyTrigger != null && reached(ticket, replyTrigger)) {
            sendMessage(ticket, TodoConstant.PURPOSE_ANSWER, "answer-" + ticketId + "-" + replyTo, summary, replyTo);
            updateDisposition(replyTo, item -> {
                if (!item.acked.contains(ticketId)) {
                    item.acked.add(ticketId);
                }
            });
        }

        // 3、按报告状态推进工单
        if (TodoConstant.REPORT_READY.equals(state)) {
            sendMessage(ticket, TodoConstant.PURPOSE_RESULT, "result-" + ticketId + "-" + assignmentId, summary, null);
            endTicket(locked, TodoConstant.ENDED_BY_EXECUTOR, TodoConstant.OUTCOME_COMPLETED, null, assignment);
        } else if (TodoConstant.REPORT_DROPPED.equals(state)) {
            endTicket(locked, TodoConstant.ENDED_BY_EXECUTOR, TodoConstant.OUTCOME_DROPPED, null, assignment);
        } else if (TodoConstant.REPORT_BLOCKED.equals(state) || TodoConstant.REPORT_FAILED.equals(state)) {
            sendMessage(ticket, TodoConstant.PURPOSE_NOTICE, "report-" + ticketId + "-" + assignmentId + "-" + now.getTime(), summary, null);
        }
        assignmentMapper.update(assignment);
        ticketMapper.update(ticket);
        threadMapper.update(locked.thread());
        wake(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reported", true);
        result.put("ended", ticket.getEndedTime() != null);
        return TodoJsonTool.toJson(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handback(long ticketId, Map<String, Object> args) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getEndedTime() != null) {
            return refusal(ENDED_REFUSAL);
        }
        Long triggerId = TodoJsonTool.id(args, "trigger_id");
        String reasonText = TodoJsonTool.text(args, "reason");
        if (triggerId == null || reasonText == null) {
            return refusal("trigger_id 与 reason 不能为空");
        }
        TodoTrigger trigger = triggerMapper.load(triggerId);
        TodoDisposition disposition = trigger == null ? null : disposition(trigger);
        // 不归本工单决定的消息（或已退回过）不重复排队
        if (disposition == null || !disposition.ownedBy(ticketId)) {
            return TodoJsonTool.toJson(Map.of("queued", false));
        }
        TodoDisposition.TodoHandback handback = new TodoDisposition.TodoHandback();
        handback.ticketId = ticketId;
        handback.reason = TodoJsonTool.abbreviate(reasonText, 300);
        handback.at = System.currentTimeMillis();
        disposition.handbacks.add(handback);
        observation.append(locked.thread().getId(), null, ticketId, null, null,
                new SuperagentTraceEvent("input.handback", "queued", String.valueOf(triggerId), reasonText, null, null));
        triggerMapper.updateDisposition(triggerId, TodoJsonTool.toJson(disposition));

        // 没有其他工单接手时进入研判
        boolean queued = disposition.targetTicketId == null && !ownedByLive(disposition);
        if (queued) {
            TodoThread thread = locked.thread();
            List<Long> pending = TodoJsonTool.longList(thread.getPending());
            if (!pending.contains(triggerId)) {
                pending.add(triggerId);
            }
            thread.setPending(TodoJsonTool.longListJson(pending));
            threadMapper.update(thread);
            wake(null);
        }
        return TodoJsonTool.toJson(Map.of("queued", queued));
    }

    // ---------------------- 人工操作 ----------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> stop(int userId, long ticketId) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getUserId() != userId) {
            return Response.ofFail("工单不存在");
        }
        if (locked.ticket().getEndedTime() != null) {
            return Response.ofFail("工单已结束");
        }
        endTicket(locked, TodoConstant.ENDED_BY_HUMAN, TodoConstant.OUTCOME_STOPPED, null, null);
        ticketMapper.update(locked.ticket());
        // 撤回未发送消息与未执行的启动、投递
        messageMapper.dropPendingByTicket(ticketId);
        effectMapper.deleteByKey(TodoConstant.EFFECT_DELIVERY, String.valueOf(ticketId));
        for (TodoAssignment assignment : assignmentMapper.listByTicket(ticketId)) {
            effectMapper.deleteByKey(TodoConstant.EFFECT_LAUNCH, String.valueOf(assignment.getId()));
        }
        // 提交后中断正在执行的一轮
        wake(ticketId);
        return Response.ofSuccess();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> restart(int userId, long ticketId) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getUserId() != userId) {
            return Response.ofFail("工单不存在");
        }
        TodoTicket ticket = locked.ticket();
        if (!TodoConstant.OUTCOME_STOPPED.equals(ticket.getEndedOutcome()) || !revalidate(ticket)) {
            return Response.ofFail("当前工单不可重新开始");
        }
        addInput(ticket, TodoConstant.INPUT_TEXT, null, true,
                "用户要求按原范围重新开始。先核实上次已经执行的操作，避免重复产生外部副作用。");
        reopen(ticket, null);
        ticketMapper.update(ticket);
        wake(null);
        return Response.ofSuccess();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> retry(int userId, long ticketId) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getUserId() != userId) {
            return Response.ofFail("工单不存在");
        }
        TodoTicket ticket = locked.ticket();
        List<TodoAssignment> assignments = assignmentMapper.listByTicket(ticketId);
        if (!TodoStageTool.canRetry(ticket, assignments, inputMapper.listByTicket(ticketId))) {
            return Response.ofFail("当前状态不可重试");
        }
        TodoAssignment latest = TodoStageTool.latest(assignments);
        if (latest != null && !TodoConstant.ASSIGNMENT_ENDED.equals(latest.getState())) {
            endAssignment(latest, "retried");
            assignmentMapper.update(latest);
            requeueUnread(ticket, latest.getId());
        }
        ticket.setDeliveryError(null);
        ticketMapper.update(ticket);
        newAssignment(ticket);
        wake(null);
        return Response.ofSuccess();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean autoRetry(long ticketId, String reason, String note) {
        LockedTicket locked = lockTicket(ticketId);
        if (locked == null || locked.ticket().getEndedTime() != null) {
            return false;
        }
        TodoTicket ticket = locked.ticket();
        List<TodoAssignment> assignments = assignmentMapper.listByTicket(ticketId);
        TodoAssignment latest = TodoStageTool.latest(assignments);
        // 只处理已经停下的一轮：仍在执行或执行中的投递不介入
        if (latest == null || !TodoConstant.ASSIGNMENT_RUNNING.equals(latest.getState()) || TodoStageTool.busy(latest)) {
            return false;
        }
        // 单工单自动重试次数上限（按派工结束原因计数）
        long retried = assignments.stream()
                .filter(item -> TodoConstant.ENDED_REASON_WATCHDOG.equals(item.getEndedReason()))
                .count();
        if (retried >= TodoConstant.WATCHDOG_MAX_RETRIES) {
            return false;
        }
        String report = latest.getReportState();
        if (TodoConstant.REPORT_FAILED.equals(report) || TodoConstant.REPORT_BLOCKED.equals(report)) {
            // 人工已追加/回答的不再自动重试，交给人继续推进
            if (!TodoStageTool.canRetry(ticket, assignments, inputMapper.listByTicket(ticketId))) {
                return false;
            }
        } else if (!TodoConstant.REPORT_WAITING.equals(report)) {
            return false;
        }
        endAssignment(latest, TodoConstant.ENDED_REASON_WATCHDOG);
        assignmentMapper.update(latest);
        requeueUnread(ticket, latest.getId());
        addInput(ticket, TodoConstant.INPUT_TEXT, null, false, note);
        ticket.setDeliveryError(null);
        ticketMapper.update(ticket);
        newAssignment(ticket);
        sendMessage(ticket, TodoConstant.PURPOSE_NOTICE,
                "watchdog-" + ticketId + "-" + latest.getId(),
                "执行中断（" + reason + "），已自动重试（第 " + (retried + 1) + " 次）", null);
        wake(null);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> requeue(int userId, long triggerId) {
        TodoTrigger trigger = triggerMapper.load(triggerId);
        if (trigger == null || trigger.getUserId() != userId) {
            return Response.ofFail("提交不存在");
        }
        TodoThread thread = threadMapper.loadForUpdate(trigger.getThreadId());
        TodoTrigger lockedTrigger = triggerMapper.load(triggerId);
        if (disposition(lockedTrigger).targetTicketId != null) {
            return Response.ofFail("指定工单的提交不能重新分派");
        }
        TodoEvaluating evaluating = TodoJsonTool.fromJson(thread.getEvaluating(), TodoEvaluating.class);
        List<Long> pending = TodoJsonTool.longList(thread.getPending());
        if ((evaluating != null && evaluating.triggerIds.contains(triggerId)) || pending.contains(triggerId)) {
            return Response.ofFail("该提交正在研判");
        }
        // 标记人工重新研判：即使仍有工单接手，也交给研判
        updateDisposition(triggerId, item -> item.requeued = true);
        requeueFront(thread, List.of(triggerId));
        threadMapper.update(thread);
        wake(null);
        return Response.ofSuccess();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<String> dismiss(int userId, long decisionId) {
        TodoDecision decision = decisionMapper.load(decisionId);
        TodoThread thread = decision == null ? null : threadMapper.load(decision.getThreadId());
        if (thread == null || thread.getUserId() != userId) {
            return Response.ofFail("决定不存在");
        }
        if (!TodoConstant.DECISION_UNAVAILABLE.equals(decision.getKind())) {
            return Response.ofFail("只能忽略未完成的研判");
        }
        decisionMapper.updateDismissed(decisionId);
        return Response.ofSuccess();
    }

    // ---------------------- 渠道与恢复 ----------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverMessage(String messageKey) {
        // Web 渠道：写入收件箱即送达；已送达或已丢弃的不再变化
        TodoMessage message = messageMapper.loadByKey(messageKey);
        if (message != null && TodoConstant.MESSAGE_PENDING.equals(message.getStatus())) {
            messageMapper.updateStatus(message.getId(), TodoConstant.MESSAGE_SENT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recover() {
        // 1、执行中被打断的一轮：结束派工，新建派工并附恢复说明；空闲等待的派工不受影响
        for (TodoAssignment running : assignmentMapper.listByState(TodoConstant.ASSIGNMENT_RUNNING)) {
            if (!TodoStageTool.busy(running)) {
                continue;
            }
            LockedTicket locked = lockTicket(running.getTicketId());
            TodoAssignment assignment = assignmentMapper.load(running.getId());
            if (locked == null || assignment == null) {
                continue;
            }
            endAssignment(assignment, "hostRestarted");
            assignmentMapper.update(assignment);
            TodoTicket ticket = locked.ticket();
            if (ticket.getEndedTime() != null) {
                continue;
            }
            requeueUnread(ticket, assignment.getId());
            addInput(ticket, TodoConstant.INPUT_TEXT, null, false, RECOVERY_NOTE);
            ticketMapper.update(ticket);
            newAssignment(ticket);
            logger.info("超级助理重启恢复派工, ticketId={}, assignmentId={}", ticket.getId(), assignment.getId());
        }
        // 2、补齐队列：待启动的派工与待投递的输入（同类型同对象合并，不会重复）
        for (TodoAssignment pending : assignmentMapper.listByState(TodoConstant.ASSIGNMENT_PENDING)) {
            enqueue(TodoConstant.EFFECT_LAUNCH, String.valueOf(pending.getId()),
                    Map.of("ticketId", pending.getTicketId(), "assignmentId", pending.getId()));
        }
        for (TodoTicket ticket : ticketMapper.listLive()) {
            if (!TodoJsonTool.longList(ticket.getUndelivered()).isEmpty()) {
                enqueue(TodoConstant.EFFECT_DELIVERY, String.valueOf(ticket.getId()), Map.of("ticketId", ticket.getId()));
            }
        }
        wake(null);
    }

    // ---------------------- 内部：工单生命周期 ----------------------

    /**
     * 线程与工单（已按“先线程、后工单”加锁）
     */
    private record LockedTicket(TodoThread thread, TodoTicket ticket) {
    }

    /**
     * 按“先线程、后工单”加锁；工单不存在返回 null
     */
    private LockedTicket lockTicket(long ticketId) {
        TodoTicket plain = ticketMapper.load(ticketId);
        if (plain == null) {
            return null;
        }
        TodoThread thread = threadMapper.loadForUpdate(plain.getThreadId());
        TodoTicket ticket = ticketMapper.loadForUpdate(ticketId);
        return thread == null || ticket == null ? null : new LockedTicket(thread, ticket);
    }

    /**
     * 结束工单：结束未结束的派工、清除提问；执行器结束时释放未读追问回研判（人工停止不释放）
     */
    private void endTicket(LockedTicket locked, String endedBy, String outcome, String reasonText, TodoAssignment current) {
        TodoTicket ticket = locked.ticket();
        ticket.setEndedTime(new Date());
        ticket.setEndedBy(endedBy);
        ticket.setEndedOutcome(outcome);
        ticket.setEndedReason(reasonText);
        ticket.setAttention(null);
        String endedReason = TodoConstant.ENDED_BY_HUMAN.equals(endedBy) ? "stopped" : "reported";
        for (TodoAssignment assignment : assignmentMapper.listByTicket(ticket.getId())) {
            if (TodoConstant.ASSIGNMENT_ENDED.equals(assignment.getState())) {
                continue;
            }
            // 当前派工由调用方统一更新
            if (current != null && current.getId() == assignment.getId()) {
                endAssignment(current, endedReason);
            } else {
                endAssignment(assignment, endedReason);
                assignmentMapper.update(assignment);
            }
        }
        if (TodoConstant.ENDED_BY_EXECUTOR.equals(endedBy)) {
            releaseUnread(locked);
        }
        ticket.setUndelivered(null);
    }

    /**
     * 释放未读追问：Worker 没读到、也没确认或退回的人工消息，在没有其他工单接手时回到研判
     */
    private void releaseUnread(LockedTicket locked) {
        TodoTicket ticket = locked.ticket();
        TodoThread thread = locked.thread();
        List<Long> pending = TodoJsonTool.longList(thread.getPending());
        for (TodoInput input : inputMapper.listByTicket(ticket.getId())) {
            if (!TodoConstant.INPUT_TRIGGER.equals(input.getKind()) || input.getHuman() != 1
                    || input.getReadTime() != null || input.getTriggerId() == null) {
                continue;
            }
            TodoTrigger trigger = triggerMapper.load(input.getTriggerId());
            TodoDisposition disposition = trigger == null ? null : disposition(trigger);
            if (disposition == null || disposition.acked.contains(ticket.getId()) || !disposition.ownedBy(ticket.getId())) {
                continue;
            }
            // 结束的工单不再算接手方
            disposition.fanout.remove(ticket.getId());
            triggerMapper.updateDisposition(trigger.getId(), TodoJsonTool.toJson(disposition));
            if (disposition.targetTicketId == null && !ownedByLive(disposition) && !pending.contains(trigger.getId())) {
                pending.add(trigger.getId());
            }
        }
        thread.setPending(TodoJsonTool.longListJson(pending));
    }

    /**
     * 恢复已结束的工单：沿用原授权，新建派工
     */
    private void reopen(TodoTicket ticket, Long triggerId) {
        ticket.setEndedTime(null);
        ticket.setEndedBy(null);
        ticket.setEndedOutcome(null);
        ticket.setEndedReason(null);
        addInput(ticket, TodoConstant.INPUT_RESUMED, triggerId, false, null);
        newAssignment(ticket);
    }

    /**
     * 恢复前重新校验授权：提交人仍是有效管理员；推断授权还需 auto 模式
     */
    private boolean revalidate(TodoTicket ticket) {
        TodoMandate mandate = TodoJsonTool.fromJson(ticket.getMandate(), TodoMandate.class);
        if (mandate == null || gate(mandate.actorUserId) != null) {
            return false;
        }
        return !TodoConstant.STRENGTH_INFERRED.equals(mandate.strength)
                || TodoConstant.MODE_AUTO.equals(settingsService.policy().mode);
    }

    /**
     * 准入：返回拒绝原因编码，通过返回 null
     */
    private String gate(int userId) {
        User user = userMapper.load(userId);
        if (user == null || user.getStatus() != 0) {
            return TodoConstant.REASON_SENDER_INACTIVE;
        }
        if (!XxlRoleEnum.ADMIN.getCode().equals(user.getRole())) {
            return TodoConstant.REASON_SENDER_NOT_ADMIN;
        }
        return null;
    }

    /**
     * 新建待启动派工（工具快照取当前执行配置），并入队启动
     */
    private void newAssignment(TodoTicket ticket) {
        TodoSettingsContent.Worker worker = settingsService.worker();
        TodoToolsSnapshot tools = new TodoToolsSnapshot();
        tools.mcpIds = worker.mcpIds;
        tools.skillIds = worker.skillIds;
        TodoAssignment assignment = new TodoAssignment();
        assignment.setTicketId(ticket.getId());
        assignment.setState(TodoConstant.ASSIGNMENT_PENDING);
        assignment.setTools(TodoJsonTool.toJson(tools));
        assignmentMapper.insert(assignment);
        observation.append(ticket.getThreadId(), null, ticket.getId(), assignment.getId(), null,
                new SuperagentTraceEvent("assignment.queued", "queued", null, assignment.getTools(), null, null));
        enqueue(TodoConstant.EFFECT_LAUNCH, String.valueOf(assignment.getId()),
                Map.of("ticketId", ticket.getId(), "assignmentId", assignment.getId()));
    }

    /**
     * 结束派工
     */
    private void endAssignment(TodoAssignment assignment, String endedReason) {
        assignment.setState(TodoConstant.ASSIGNMENT_ENDED);
        assignment.setEndedTime(new Date());
        assignment.setEndedReason(endedReason);
        TodoTicket ticket = ticketMapper.load(assignment.getTicketId());
        observation.append(ticket.getThreadId(), null, ticket.getId(), assignment.getId(), null,
                new SuperagentTraceEvent("assignment.ended", "ended", endedReason, null, null, null));
    }

    /**
     * 已送达旧派工但未读的输入，重新交给下一个派工
     */
    private void requeueUnread(TodoTicket ticket, long assignmentId) {
        List<Long> undelivered = TodoJsonTool.longList(ticket.getUndelivered());
        for (TodoInput input : inputMapper.listByTicket(ticket.getId())) {
            boolean unread = input.getDeliveredTo() != null && input.getDeliveredTo() == assignmentId && input.getReadTime() == null;
            if (unread && !undelivered.contains(input.getId())) {
                undelivered.add(input.getId());
            }
        }
        ticket.setUndelivered(TodoJsonTool.longListJson(undelivered));
    }

    /**
     * 新增工单输入，并加入待投递（调用方负责更新工单）
     */
    private void addInput(TodoTicket ticket, String kind, Long triggerId, boolean human, String content) {
        TodoInput input = new TodoInput();
        input.setTicketId(ticket.getId());
        input.setKind(kind);
        input.setTriggerId(triggerId);
        input.setHuman(human ? 1 : 0);
        input.setContent(content);
        input.setEventTime(new Date());
        inputMapper.insert(input);
        List<Long> undelivered = TodoJsonTool.longList(ticket.getUndelivered());
        undelivered.add(input.getId());
        ticket.setUndelivered(TodoJsonTool.longListJson(undelivered));
    }

    /**
     * 写入报告字段
     */
    private void setReport(TodoAssignment assignment, String state, String summary, Date now) {
        assignment.setReportState(state);
        assignment.setReportSummary(summary);
        assignment.setReportTime(now);
        TodoTicket ticket = ticketMapper.load(assignment.getTicketId());
        observation.append(ticket.getThreadId(), null, ticket.getId(), assignment.getId(), null,
                new SuperagentTraceEvent("worker.reported", state, summary, null, null, null));
    }

    // ---------------------- 内部：决定与去向 ----------------------

    /**
     * 记录一条决定，并回写各触发的去向；产生决定后清零连续超时次数
     */
    private TodoDecision record(TodoThread thread, String evalId, List<Long> triggerIds, String kind, String strength,
                                List<TodoReason> reasons, TodoMandate mandate, Map<String, Object> audit) {
        TodoDecision decision = new TodoDecision();
        decision.setEvalId(evalId);
        decision.setThreadId(thread.getId());
        decision.setTriggerIds(TodoJsonTool.toJson(triggerIds));
        decision.setPolicyVersion(settingsService.policyVersion());
        decision.setKind(kind);
        decision.setStrength(strength);
        decision.setReason(TodoJsonTool.toJson(reasons));
        decision.setMandate(TodoJsonTool.toJson(mandate));
        decision.setAudit(TodoJsonTool.toJson(audit));
        decision.setEventTime(new Date());
        decisionMapper.insert(decision);
        Map<String, Object> finalDecision = new LinkedHashMap<>();
        finalDecision.put("decisionId", decision.getId());
        finalDecision.put("kind", kind);
        finalDecision.put("reason", reasons);
        finalDecision.put("policyVersion", decision.getPolicyVersion());
        observation.append(thread.getId(), evalId, null, null, null,
                new SuperagentTraceEvent("decision.recorded", kind, kind, TodoJsonTool.toJson(finalDecision), null, null));
        for (Long triggerId : triggerIds) {
            updateDisposition(triggerId, item -> item.decisionId = decision.getId());
        }
        thread.setExpired(0);
        return decision;
    }

    /**
     * 重新读取并更新一条触发的去向
     */
    private void updateDisposition(long triggerId, Consumer<TodoDisposition> change) {
        TodoTrigger trigger = triggerMapper.load(triggerId);
        if (trigger == null) {
            return;
        }
        TodoDisposition disposition = disposition(trigger);
        change.accept(disposition);
        triggerMapper.updateDisposition(triggerId, TodoJsonTool.toJson(disposition));
    }

    /**
     * 触发去向，未记录时返回空去向
     */
    private TodoDisposition disposition(TodoTrigger trigger) {
        TodoDisposition disposition = TodoJsonTool.fromJson(trigger.getDisposition(), TodoDisposition.class);
        return disposition == null ? new TodoDisposition() : disposition;
    }

    /**
     * 是否仍有进行中的工单接手该触发
     */
    private boolean ownedByLive(TodoDisposition disposition) {
        for (Long ticketId : disposition.fanout) {
            if (disposition.ownedBy(ticketId)) {
                TodoTicket ticket = ticketMapper.load(ticketId);
                if (ticket != null && ticket.getEndedTime() == null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 触发是否送达过该工单（发起或经订阅送达）
     */
    private boolean reached(TodoTicket ticket, TodoTrigger trigger) {
        return trigger.getThreadId() == ticket.getThreadId()
                && (trigger.getId() == ticket.getOriginTriggerId() || disposition(trigger).fanout.contains(ticket.getId()));
    }

    /**
     * 当前进行中的 Eval，ID 不匹配（已结束或已被替换）返回 null
     */
    private TodoEvaluating evaluating(TodoThread thread, String evalId) {
        TodoEvaluating evaluating = TodoJsonTool.fromJson(thread.getEvaluating(), TodoEvaluating.class);
        return evaluating != null && evaluating.id.equals(evalId) ? evaluating : null;
    }

    /**
     * 触发重新排到队首（去重）
     */
    private void requeueFront(TodoThread thread, List<Long> triggerIds) {
        List<Long> pending = new ArrayList<>(triggerIds);
        for (Long id : TodoJsonTool.longList(thread.getPending())) {
            if (!pending.contains(id)) {
                pending.add(id);
            }
        }
        thread.setPending(TodoJsonTool.longListJson(pending));
    }

    /**
     * 本工单最近一条人工触发ID（提问回到这里）
     */
    private Long latestHumanTrigger(long ticketId) {
        Long latest = null;
        for (TodoInput input : inputMapper.listByTicket(ticketId)) {
            if (TodoConstant.INPUT_TRIGGER.equals(input.getKind()) && input.getHuman() == 1) {
                latest = input.getTriggerId();
            }
        }
        return latest;
    }

    // ---------------------- 内部：文本组装 ----------------------

    /**
     * 研判候选卡片：本批提交、退回原因、本待办已有工单（提交内容是外部数据）
     */
    private String candidateCard(TodoThread thread, TodoEvaluating evaluating) {
        StringBuilder card = new StringBuilder();
        card.append("## 待办\n标题：").append(thread.getTitle()).append("\n\n## 本批提交\n");
        for (TodoTrigger trigger : triggerMapper.listByIds(evaluating.triggerIds)) {
            card.append("[触发 #").append(trigger.getId()).append(" · ").append(trigger.getKind()).append("]\n")
                    .append(trigger.getContent()).append("\n");
            for (TodoDisposition.TodoHandback handback : disposition(trigger).handbacks) {
                card.append("（工单 #").append(handback.ticketId).append(" 退回：").append(handback.reason).append("）\n");
            }
        }
        card.append("\n## 这条待办已有的工单\n");
        List<TodoTicket> tickets = ticketMapper.listByThread(thread.getId());
        if (tickets.isEmpty()) {
            card.append("（无）\n");
        }
        for (TodoTicket ticket : tickets) {
            TodoMandate mandate = TodoJsonTool.fromJson(ticket.getMandate(), TodoMandate.class);
            String stage = TodoStageTool.ticketStage(ticket, assignmentMapper.listByTicket(ticket.getId()),
                    inputMapper.listByTicket(ticket.getId()));
            card.append("#").append(ticket.getId()).append(" ").append(ticket.getTitle()).append(" · ").append(stage)
                    .append(" · 范围：").append(mandate == null ? "" : mandate.scope).append("\n");
        }
        return card.toString();
    }

    /**
     * 本轮输入文本
     */
    private String roundContent(List<TodoInput> inputs) {
        if (inputs.isEmpty()) {
            return "开始执行本工单。";
        }
        Map<Long, TodoTrigger> triggers = new HashMap<>();
        List<Long> triggerIds = new ArrayList<>();
        for (TodoInput input : inputs) {
            if (input.getTriggerId() != null) {
                triggerIds.add(input.getTriggerId());
            }
        }
        if (!triggerIds.isEmpty()) {
            for (TodoTrigger trigger : triggerMapper.listByIds(triggerIds)) {
                triggers.put(trigger.getId(), trigger);
            }
        }
        StringBuilder text = new StringBuilder();
        for (TodoInput input : inputs) {
            TodoTrigger trigger = input.getTriggerId() == null ? null : triggers.get(input.getTriggerId());
            if (TodoConstant.INPUT_TRIGGER.equals(input.getKind()) && trigger != null) {
                String label = TodoConstant.TRIGGER_ANSWER.equals(trigger.getKind()) ? "回答你的提问" : "提交";
                text.append("[触发 #").append(trigger.getId()).append(" · ").append(label).append("]\n")
                        .append(trigger.getContent()).append("\n\n");
            } else if (TodoConstant.INPUT_RESUMED.equals(input.getKind())) {
                text.append("工单已结束后收到新消息，现已恢复，授权范围不变。\n\n");
            } else if (StringTool.isNotBlank(input.getContent())) {
                text.append(input.getContent()).append("\n\n");
            }
        }
        return text.toString().trim();
    }

    /**
     * Worker 系统提示词：团队指令 + 本工单授权（代码固定，不可覆盖）
     */
    private String workerPrompt(TodoTicket ticket) {
        TodoMandate mandate = TodoJsonTool.fromJson(ticket.getMandate(), TodoMandate.class);
        return settingsService.prompts().worker
                + "\n\n## 当前工单\n编号：#" + ticket.getId()
                + "\n标题：" + ticket.getTitle()
                + "\n授权范围：" + (mandate == null ? "" : mandate.scope)
                + "\n只能在授权范围内工作；超出范围的请求用 todo_handback 退回。"
                + "\n人的消息以“[触发 #编号]”开头送达，工具里的 trigger_id 填这个编号。";
    }

    /**
     * 保存一条 Worker 会话消息
     */
    private void saveWorkerMsg(long ticketId, long assignmentId, String role, String content, List<Long> inputIds) {
        TodoWorkerMsg msg = new TodoWorkerMsg();
        msg.setTicketId(ticketId);
        msg.setAssignmentId(assignmentId);
        msg.setRole(role);
        msg.setInputIds(TodoJsonTool.longListJson(inputIds));
        msg.setContent(content);
        workerMsgMapper.insert(msg);
    }

    // ---------------------- 内部：副作用 ----------------------

    /**
     * 发送消息：先写回执（幂等键去重），再入队投递
     */
    private void sendMessage(TodoTicket ticket, String purpose, String messageKey, String content, Long triggerId) {
        TodoMessage message = new TodoMessage();
        message.setMessageKey(messageKey);
        message.setUserId(ticket.getUserId());
        message.setTicketId(ticket.getId());
        message.setTriggerId(triggerId);
        message.setPurpose(purpose);
        message.setContent(content);
        message.setStatus(TodoConstant.MESSAGE_PENDING);
        // 幂等：同一幂等键只写一次（先查；并发写入由唯一索引兜底，冲突只回滚该语句，不影响本事务）
        if (messageMapper.loadByKey(messageKey) != null) {
            return;
        }
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            return;
        }
        enqueue(TodoConstant.EFFECT_REPLY, messageKey, Map.of());
    }

    /**
     * 入队副作用（与命令同一事务）
     */
    private void enqueue(String kind, String key, Map<String, Object> payload) {
        TodoEffect effect = new TodoEffect();
        effect.setKind(kind);
        effect.setEffectKey(key);
        effect.setPayload(TodoJsonTool.toJson(payload));
        effect.setDueTime(new Date());
        // 合并优先：同类型同对象已存在时原地更新（保留队列位置）；不存在再新增，并发新增冲突时回到合并
        if (effectMapper.updateByKey(effect) > 0) {
            return;
        }
        try {
            effectMapper.insert(effect);
        } catch (DuplicateKeyException e) {
            effectMapper.updateByKey(effect);
        }
    }

    /**
     * 发布唤醒事件：事务提交后调度器处理
     */
    private void wake(Long stopTicketId) {
        eventPublisher.publishEvent(new TodoWakeEvent(stopTicketId));
    }

    // ---------------------- 内部：工具 ----------------------

    private String newEvalId(long threadId) {
        return "eval-" + threadId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private List<TodoReason> reason(String code, String text) {
        List<TodoReason> list = new ArrayList<>();
        list.add(new TodoReason(code, text));
        return list;
    }

    private List<Long> ids(List<TodoTrigger> triggers) {
        List<Long> list = new ArrayList<>();
        for (TodoTrigger trigger : triggers) {
            list.add(trigger.getId());
        }
        return list;
    }

    private String join(List<Long> ids) {
        StringBuilder text = new StringBuilder();
        for (Long id : ids) {
            if (text.length() > 0) {
                text.append(",");
            }
            text.append(id);
        }
        return text.toString();
    }

    private String firstLine(String content) {
        int index = content.indexOf('\n');
        return index > 0 ? content.substring(0, index).trim() : content;
    }

    private String refusal(String error) {
        return TodoJsonTool.toJson(Map.of("error", error));
    }

}
