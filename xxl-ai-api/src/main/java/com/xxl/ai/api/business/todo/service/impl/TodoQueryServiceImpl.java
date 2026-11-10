package com.xxl.ai.api.business.todo.service.impl;

import com.xxl.ai.api.business.todo.constant.TodoConstant;
import com.xxl.ai.api.business.todo.mapper.TodoAssignmentMapper;
import com.xxl.ai.api.business.todo.mapper.TodoDecisionMapper;
import com.xxl.ai.api.business.todo.mapper.TodoInputMapper;
import com.xxl.ai.api.business.todo.mapper.TodoMessageMapper;
import com.xxl.ai.api.business.todo.mapper.TodoThreadMapper;
import com.xxl.ai.api.business.todo.mapper.TodoTicketMapper;
import com.xxl.ai.api.business.todo.mapper.TodoTriggerMapper;
import com.xxl.ai.api.business.todo.model.dto.TodoAssignmentDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoDecisionDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoMessageDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoOverviewDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDetailDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoTicketDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoTriggerDTO;
import com.xxl.ai.api.business.todo.model.entity.TodoAssignment;
import com.xxl.ai.api.business.todo.model.entity.TodoDecision;
import com.xxl.ai.api.business.todo.model.entity.TodoInput;
import com.xxl.ai.api.business.todo.model.entity.TodoMessage;
import com.xxl.ai.api.business.todo.model.entity.TodoThread;
import com.xxl.ai.api.business.todo.model.entity.TodoTicket;
import com.xxl.ai.api.business.todo.model.entity.TodoTrigger;
import com.xxl.ai.api.business.todo.model.json.TodoAttention;
import com.xxl.ai.api.business.todo.model.json.TodoDisposition;
import com.xxl.ai.api.business.todo.model.json.TodoMandate;
import com.xxl.ai.api.business.todo.model.json.TodoReason;
import com.xxl.ai.api.business.todo.service.TodoEffectService;
import com.xxl.ai.api.business.todo.service.TodoQueryService;
import com.xxl.ai.api.business.todo.service.TodoSettingsService;
import com.xxl.ai.api.business.todo.util.TodoJsonTool;
import com.xxl.ai.api.business.todo.util.TodoStageTool;
import com.xxl.tool.core.DateTool;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 超级助理 查询 Service 实现
 *
 * 阶段只在这里由数据推导（TodoStageTool），面板标签直接取阶段编码，不另存状态字段。
 *
 * @author xxl-ai 2026-11-10
 */
@Service
public class TodoQueryServiceImpl implements TodoQueryService {

    /** 概览扫描的最近待办数 */
    private static final int OVERVIEW_SCAN = 200;

    @Resource
    private TodoThreadMapper threadMapper;
    @Resource
    private TodoTriggerMapper triggerMapper;
    @Resource
    private TodoDecisionMapper decisionMapper;
    @Resource
    private TodoTicketMapper ticketMapper;
    @Resource
    private TodoAssignmentMapper assignmentMapper;
    @Resource
    private TodoInputMapper inputMapper;
    @Resource
    private TodoMessageMapper messageMapper;
    @Resource
    private TodoSettingsService settingsService;
    @Resource
    private TodoEffectService effectService;
    @Resource
    private com.xxl.ai.api.business.todo.mapper.TodoEventMapper eventMapper;

    @Override
    public PageModel<TodoThreadDTO> pageList(int userId, int offset, int pagesize) {
        List<TodoThreadDTO> list = new ArrayList<>();
        for (TodoThread thread : threadMapper.pageList(userId, offset, pagesize)) {
            list.add(threadDto(thread));
        }
        PageModel<TodoThreadDTO> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(threadMapper.pageListCount(userId));
        return pageModel;
    }

    @Override
    public Response<TodoThreadDetailDTO> load(int userId, long threadId) {
        TodoThread thread = threadMapper.load(threadId);
        if (thread == null || thread.getUserId() != userId) {
            return Response.ofFail("待办不存在");
        }
        TodoThreadDetailDTO detail = new TodoThreadDetailDTO();
        detail.setThread(threadDto(thread));

        List<TodoTriggerDTO> triggers = new ArrayList<>();
        for (TodoTrigger trigger : triggerMapper.listByThread(threadId)) {
            triggers.add(triggerDto(trigger));
        }
        detail.setTriggers(triggers);

        List<TodoDecisionDTO> decisions = new ArrayList<>();
        for (TodoDecision decision : decisionMapper.listByThread(threadId)) {
            decisions.add(decisionDto(decision));
        }
        detail.setDecisions(decisions);

        List<TodoTicketDTO> tickets = new ArrayList<>();
        List<TodoMessageDTO> messages = new ArrayList<>();
        for (TodoTicket ticket : ticketMapper.listByThread(threadId)) {
            tickets.add(ticketDto(ticket));
            for (TodoMessage message : messageMapper.listByTicket(ticket.getId())) {
                // 只展示已送达的消息
                if (TodoConstant.MESSAGE_SENT.equals(message.getStatus())) {
                    messages.add(messageDto(message, ticket));
                }
            }
        }
        detail.setTickets(tickets);
        detail.setMessages(messages);
        return Response.ofSuccess(detail);
    }

    @Override
    public TodoOverviewDTO overview(int userId) {
        TodoOverviewDTO overview = new TodoOverviewDTO();
        List<TodoThreadDTO> needMe = new ArrayList<>();
        for (TodoThread thread : threadMapper.pageList(userId, 0, OVERVIEW_SCAN)) {
            TodoThreadDTO dto = threadDto(thread);
            if (TodoStageTool.needPerson(dto.getStage())) {
                needMe.add(dto);
            }
        }
        overview.setNeedMe(needMe);
        overview.setWorkers(eventMapper.workers(userId));
        overview.setProblems(effectService.problems());
        overview.setReadyCode(settingsService.readyCode());
        overview.setPaused(settingsService.worker().paused);
        overview.setBusySlots(effectService.busySlots());
        overview.setMaxWorkers(settingsService.worker().maxWorkers);
        overview.setUnread(messageMapper.pageListCount(userId, 0));
        return overview;
    }

    @Override
    public PageModel<TodoMessageDTO> messagePageList(int userId, int readStatus, int offset, int pagesize) {
        Map<Long, TodoTicket> ticketCache = new HashMap<>();
        List<TodoMessageDTO> list = new ArrayList<>();
        for (TodoMessage message : messageMapper.pageList(userId, readStatus, offset, pagesize)) {
            TodoTicket ticket = message.getTicketId() == null ? null
                    : ticketCache.computeIfAbsent(message.getTicketId(), id -> ticketMapper.load(id));
            list.add(messageDto(message, ticket));
        }
        PageModel<TodoMessageDTO> pageModel = new PageModel<>();
        pageModel.setData(list);
        pageModel.setTotal(messageMapper.pageListCount(userId, readStatus));
        return pageModel;
    }

    @Override
    public Response<String> markRead(int userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Response.ofFail("请选择消息");
        }
        messageMapper.markRead(userId, ids);
        return Response.ofSuccess();
    }

    // ---------------------- 推导 ----------------------

    /**
     * 待办行：有进行中工单时取其阶段（优先需要人处理的）；否则按研判状态与最新决定推导
     */
    private TodoThreadDTO threadDto(TodoThread thread) {
        TodoThreadDTO dto = new TodoThreadDTO();
        dto.setId(thread.getId());
        dto.setTitle(thread.getTitle());
        dto.setLastTime(format(thread.getLastTime()));
        dto.setAddTime(format(thread.getAddTime()));

        // 1、进行中的工单
        List<TodoTicket> tickets = ticketMapper.listByThread(thread.getId());
        TodoTicket current = null;
        String currentStage = null;
        for (TodoTicket ticket : tickets) {
            if (ticket.getEndedTime() != null) {
                continue;
            }
            String stage = TodoStageTool.ticketStage(ticket, assignmentMapper.listByTicket(ticket.getId()),
                    inputMapper.listByTicket(ticket.getId()));
            if (current == null || (TodoStageTool.needPerson(stage) && !TodoStageTool.needPerson(currentStage))) {
                current = ticket;
                currentStage = stage;
            }
        }
        if (current != null) {
            fillTicket(dto, current, currentStage);
            return dto;
        }

        // 2、研判中或排队研判
        if (thread.getEvaluating() != null || thread.getPending() != null) {
            dto.setStage(TodoConstant.STAGE_EVALUATING);
            return dto;
        }

        // 3、最新决定：未完成且未忽略时等人处理；否则看最近的工单
        List<TodoDecision> decisions = decisionMapper.listByThread(thread.getId());
        TodoDecision latest = decisions.isEmpty() ? null : decisions.get(decisions.size() - 1);
        if (latest != null) {
            TodoReason reason = firstReason(latest);
            dto.setReasonCode(reason == null ? null : reason.code);
            dto.setReasonText(reason == null ? null : reason.text);
        }
        if (latest != null && TodoConstant.DECISION_UNAVAILABLE.equals(latest.getKind()) && latest.getDismissed() == 0) {
            dto.setStage(TodoConstant.STAGE_UNAVAILABLE);
            return dto;
        }
        if (!tickets.isEmpty()) {
            TodoTicket last = tickets.get(tickets.size() - 1);
            fillTicket(dto, last, TodoConstant.STAGE_ENDED);
            return dto;
        }
        dto.setStage(latest == null ? TodoConstant.STAGE_EVALUATING : TodoConstant.STAGE_IGNORED);
        return dto;
    }

    /**
     * 待办行填充工单信息
     */
    private void fillTicket(TodoThreadDTO dto, TodoTicket ticket, String stage) {
        dto.setStage(stage);
        dto.setTicketId(ticket.getId());
        TodoAttention attention = TodoJsonTool.fromJson(ticket.getAttention(), TodoAttention.class);
        if (attention != null) {
            dto.setQuestion(attention.question);
            dto.setAttentionId(attention.id);
        }
        TodoAssignment latest = TodoStageTool.latest(assignmentMapper.listByTicket(ticket.getId()));
        dto.setSummary(latest == null ? null : latest.getReportSummary());
    }

    // ---------------------- DTO 转换 ----------------------

    private TodoTriggerDTO triggerDto(TodoTrigger trigger) {
        TodoDisposition disposition = TodoJsonTool.fromJson(trigger.getDisposition(), TodoDisposition.class);
        if (disposition == null) {
            disposition = new TodoDisposition();
        }
        TodoTriggerDTO dto = new TodoTriggerDTO();
        dto.setId(trigger.getId());
        dto.setKind(trigger.getKind());
        dto.setContent(trigger.getContent());
        dto.setEvalId(disposition.evalId);
        dto.setDecisionId(disposition.decisionId);
        dto.setFanout(disposition.fanout);
        dto.setAcked(disposition.acked);
        List<String> handbacks = new ArrayList<>();
        for (TodoDisposition.TodoHandback handback : disposition.handbacks) {
            handbacks.add("#" + handback.ticketId + " " + handback.reason);
        }
        dto.setHandbacks(handbacks);
        dto.setRequeued(disposition.requeued);
        dto.setEventTime(format(trigger.getEventTime()));
        return dto;
    }

    private TodoDecisionDTO decisionDto(TodoDecision decision) {
        TodoDecisionDTO dto = new TodoDecisionDTO();
        dto.setId(decision.getId());
        dto.setEvalId(decision.getEvalId());
        dto.setKind(decision.getKind());
        dto.setStrength(decision.getStrength());
        dto.setTriggerIds(TodoJsonTool.longList(decision.getTriggerIds()));
        TodoReason reason = firstReason(decision);
        dto.setReasonCode(reason == null ? null : reason.code);
        dto.setReasonText(reason == null ? null : reason.text);
        dto.setTicketId(decision.getTicketId());
        TodoMandate mandate = TodoJsonTool.fromJson(decision.getMandate(), TodoMandate.class);
        dto.setScope(mandate == null ? null : mandate.scope);
        Map<?, ?> audit = TodoJsonTool.fromJson(decision.getAudit(), Map.class);
        dto.setRouterDecision(audit == null ? null : (String) audit.get("routerDecision"));
        dto.setRouterScope(audit == null ? null : (String) audit.get("scope"));
        dto.setWouldAccept(audit != null && Boolean.TRUE.equals(audit.get("wouldAccept")));
        dto.setDismissed(decision.getDismissed());
        dto.setPolicyVersion(decision.getPolicyVersion());
        dto.setEventTime(format(decision.getEventTime()));
        return dto;
    }

    private TodoTicketDTO ticketDto(TodoTicket ticket) {
        List<TodoAssignment> assignments = assignmentMapper.listByTicket(ticket.getId());
        List<TodoInput> inputs = inputMapper.listByTicket(ticket.getId());
        TodoTicketDTO dto = new TodoTicketDTO();
        dto.setId(ticket.getId());
        dto.setTitle(ticket.getTitle());
        dto.setStage(TodoStageTool.ticketStage(ticket, assignments, inputs));
        TodoMandate mandate = TodoJsonTool.fromJson(ticket.getMandate(), TodoMandate.class);
        if (mandate != null) {
            dto.setScope(mandate.scope);
            dto.setStrength(mandate.strength);
            dto.setPolicyVersion(mandate.policyVersion);
        }
        TodoAttention attention = TodoJsonTool.fromJson(ticket.getAttention(), TodoAttention.class);
        if (attention != null) {
            dto.setQuestion(attention.question);
            dto.setAttentionId(attention.id);
        }
        dto.setEndedBy(ticket.getEndedBy());
        dto.setEndedOutcome(ticket.getEndedOutcome());
        dto.setEndedReason(ticket.getEndedReason());
        dto.setEndedTime(format(ticket.getEndedTime()));
        dto.setCanRetry(TodoStageTool.canRetry(ticket, assignments, inputs));
        dto.setAddTime(format(ticket.getAddTime()));
        List<TodoAssignmentDTO> list = new ArrayList<>();
        for (TodoAssignment assignment : assignments) {
            TodoAssignmentDTO item = new TodoAssignmentDTO();
            item.setId(assignment.getId());
            item.setState(assignment.getState());
            item.setReportState(assignment.getReportState());
            item.setReportSummary(assignment.getReportSummary());
            item.setReportTime(format(assignment.getReportTime()));
            item.setStartedTime(format(assignment.getStartedTime()));
            item.setEndedTime(format(assignment.getEndedTime()));
            item.setEndedReason(assignment.getEndedReason());
            list.add(item);
        }
        dto.setAssignments(list);
        return dto;
    }

    private TodoMessageDTO messageDto(TodoMessage message, TodoTicket ticket) {
        TodoMessageDTO dto = new TodoMessageDTO();
        dto.setId(message.getId());
        dto.setTicketId(message.getTicketId());
        if (ticket != null) {
            dto.setThreadId(ticket.getThreadId());
            dto.setTicketTitle(ticket.getTitle());
        }
        dto.setPurpose(message.getPurpose());
        dto.setContent(message.getContent());
        dto.setReadStatus(message.getReadStatus());
        dto.setAddTime(format(message.getAddTime()));
        return dto;
    }

    // ---------------------- 工具 ----------------------

    private TodoReason firstReason(TodoDecision decision) {
        TodoReason[] reasons = TodoJsonTool.fromJson(decision.getReason(), TodoReason[].class);
        return reasons == null || reasons.length == 0 ? null : reasons[0];
    }

    private String format(Date date) {
        return date == null ? null : DateTool.formatDateTime(date);
    }

}
