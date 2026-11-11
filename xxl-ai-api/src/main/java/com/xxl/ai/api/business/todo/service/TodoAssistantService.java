package com.xxl.ai.api.business.todo.service;

import com.xxl.ai.api.business.harness.superagent.SuperagentRouterTool;
import com.xxl.ai.api.business.supplier.model.SupplierRuntime;
import com.xxl.ai.api.business.todo.constant.TodoConstant;
import com.xxl.ai.api.business.todo.mapper.TodoAssistantTurnMapper;
import com.xxl.ai.api.business.todo.mapper.TodoEffectMapper;
import com.xxl.ai.api.business.todo.model.dto.TodoMessageDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoSubmitDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDetailDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoTicketDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoWakeEvent;
import com.xxl.ai.api.business.todo.model.entity.TodoAssistantTurn;
import com.xxl.ai.api.business.todo.model.entity.TodoEffect;
import com.xxl.ai.api.business.todo.model.json.TodoSettingsContent;
import com.xxl.ai.api.business.todo.util.TodoJsonTool;
import com.xxl.ai.api.framework.constant.enums.XxlRoleEnum;
import com.xxl.ai.api.framework.mapper.UserMapper;
import com.xxl.ai.api.framework.model.entity.User;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统一助理入口：组织上下文、排队回复、确认后交给待办引擎
 *
 * @author xxl-ai 2026-11-12
 */
@Service
public class TodoAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(TodoAssistantService.class);

    @Resource
    private TodoAssistantTurnMapper mapper;
    @Resource
    private TodoEffectMapper effectMapper;
    @Resource
    private TodoQueryService query;
    @Resource
    private TodoSettingsService settings;
    @Resource
    private TodoEngineService engine;
    @Resource
    private SuperagentRouterTool router;
    @Resource
    private ApplicationEventPublisher publisher;
    @Resource
    private UserMapper userMapper;

    public record Send(String requestId, String content, Long referenceId, Long revisesId) {
    }

    public record TurnCommand(long id) {
    }

    /* 最近消息按时间正序展示，游标用于查看更早的记录 */
    public List<TodoAssistantTurn> recent(int userId, Long beforeId) {
        List<TodoAssistantTurn> turns = mapper.recent(userId, beforeId, 30, false);
        Collections.reverse(turns);
        return turns;
    }

    @Transactional
    public Response<TodoAssistantTurn> send(int userId, Send request) {
        if (request == null || request.requestId() == null || !request.requestId().matches("[a-zA-Z0-9_-]{1,64}")) {
            return Response.ofFail("请求标识不合法");
        }
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty() || content.length() > 4000) {
            return Response.ofFail("请输入 1 至 4000 字");
        }
        TodoAssistantTurn existing = mapper.loadByRequest(userId, request.requestId(), false);
        if (existing != null) {
            return Response.ofSuccess(existing);
        }

        TodoAssistantTurn revision = request.revisesId() == null ? null : mapper.load(userId, request.revisesId(), true);
        if (request.revisesId() != null && (revision == null || !"proposed".equals(revision.state()))) {
            TodoAssistantTurn submitted = mapper.loadByRequest(userId, request.requestId(), true);
            return submitted == null ? Response.ofFail("原执行建议已失效") : Response.ofSuccess(submitted);
        }
        Long referenceId = request.referenceId() == null && revision != null ? revision.referenceId() : request.referenceId();
        String referenceTitle = null;
        if (referenceId != null) {
            Response<TodoThreadDetailDTO> referenced = query.load(userId, referenceId);
            if (!referenced.isSuccess()) {
                return Response.ofFail("引用的待办不存在");
            }
            referenceTitle = referenced.getData().getThread().getTitle();
        }

        mapper.insert(userId, request.requestId(), content, referenceId, referenceTitle, request.revisesId());
        if (revision != null) {
            mapper.replaceProposal(userId, revision.id());
        }
        TodoAssistantTurn turn = mapper.loadByRequest(userId, request.requestId(), false);
        enqueue(userId, turn.id());
        return Response.ofSuccess(turn);
    }

    public TodoAssistantTurn findSent(int userId, String requestId) {
        return requestId == null ? null : mapper.loadByRequest(userId, requestId, false);
    }

    @Transactional
    public Response<TodoAssistantTurn> retry(int userId, long id) {
        TodoAssistantTurn turn = mapper.load(userId, id, true);
        if (turn == null || !"failed".equals(turn.state())) {
            return Response.ofFail("该消息当前不可重试");
        }
        mapper.retry(userId, id);
        enqueue(userId, id);
        return Response.ofSuccess(mapper.load(userId, id, false));
    }

    /* 持有消息行锁，确保确认和已有待办引擎提交在同一事务 */
    @Transactional
    public Response<Long> confirm(int userId, long id) {
        TodoAssistantTurn turn = mapper.load(userId, id, true);
        if (turn == null) {
            return Response.ofFail("执行建议不存在");
        }
        if (turn.threadId() != null) {
            return Response.ofSuccess(turn.threadId());
        }
        if (!"proposed".equals(turn.state())) {
            return Response.ofFail("该消息没有待确认的建议");
        }

        TodoSubmitDTO submit = new TodoSubmitDTO();
        submit.setRequestId(UUID.randomUUID().toString());
        submit.setContent(turn.proposal());
        if (turn.targetTicketId() == null) {
            submit.setKind(TodoConstant.TRIGGER_CREATE);
        } else {
            submit.setThreadId(turn.referenceId());
            submit.setKind(turn.attentionId() == null ? TodoConstant.TRIGGER_APPEND : TodoConstant.TRIGGER_ANSWER);
            submit.setAttentionId(turn.attentionId());
        }
        Response<Long> result = engine.submitFromAssistant(userId, submit, turn.targetTicketId());
        if (result.isSuccess()) {
            mapper.submit(userId, id, result.getData());
        }
        return result;
    }

    /* 队列执行时重新读取归属与配置，模型调用期间不持有数据库锁 */
    public void respond(int userId, long id) {
        TodoAssistantTurn turn = mapper.load(userId, id, false);
        if (turn == null || !"pending".equals(turn.state())) {
            return;
        }
        User user = userMapper.load(userId);
        if (user == null || user.getStatus() != 0 || !XxlRoleEnum.ADMIN.getCode().equals(user.getRole())) {
            fail(userId, id, TodoConstant.REASON_SENDER_INACTIVE);
            return;
        }
        TodoSettingsContent.Model model = settings.model();
        if (model.spaceId <= 0 || model.routerSupplierId <= 0 || model.routerModelId <= 0) {
            fail(userId, id, TodoConstant.REASON_MODEL_NOT_CONFIGURED);
            return;
        }
        Response<SupplierRuntime> runtime = settings.runtime(model.spaceId, model.routerSupplierId, model.routerModelId);
        if (!runtime.isSuccess()) {
            fail(userId, id, TodoConstant.REASON_MODEL_UNAVAILABLE);
            return;
        }

        TodoThreadDetailDTO reference = null;
        if (turn.referenceId() != null) {
            Response<TodoThreadDetailDTO> loaded = query.load(userId, turn.referenceId());
            if (!loaded.isSuccess()) {
                fail(userId, id, "referenceUnavailable");
                return;
            }
            reference = loaded.getData();
        }
        List<TodoAssistantTurn> recent = mapper.recent(userId, id, 8, true);
        Collections.reverse(recent);
        List<String> roles = new ArrayList<>();
        List<String> history = new ArrayList<>();
        for (TodoAssistantTurn prior : recent) {
            roles.add("user");
            history.add(TodoJsonTool.abbreviate(prior.content(), 1800));
            roles.add("assistant");
            String proposal = prior.proposal() == null ? "" : "\n当时的执行建议：" + TodoJsonTool.abbreviate(prior.proposal(), 800);
            history.add(TodoJsonTool.abbreviate(prior.reply(), 1800) + proposal);
        }
        TodoTicketDTO target = reference == null ? null : target(reference);
        TodoAssistantTurn revision = turn.revisesId() == null ? null : mapper.load(userId, turn.revisesId(), false);
        SuperagentRouterTool.AssistantReply output;
        try {
            output = router.reply(runtime.getData(), roles, history, context(userId, reference, target, revision, turn.content()),
                    "assistant-" + userId + "-" + id);
        } catch (RuntimeException error) {
            logger.warn("超级助理消息生成失败, turnId={}, type={}", id, error.getClass().getSimpleName());
            fail(userId, id, TodoConstant.REASON_ASSESSMENT_FAILED);
            return;
        }

        if ("continue".equals(output.kind()) && target == null) {
            fail(userId, id, "referenceUnavailable");
            return;
        }
        boolean propose = !"answer".equals(output.kind());
        Long ticketId = "continue".equals(output.kind()) ? target.getId() : null;
        String attentionId = ticketId == null ? null : target.getAttentionId();
        mapper.resolve(userId, id, propose ? "proposed" : "answered", output.reply(), output.task(), ticketId, attentionId, null);
    }

    private TodoTicketDTO target(TodoThreadDetailDTO reference) {
        Long ticketId = reference.getThread().getTicketId();
        return reference.getTickets().stream()
                .filter(ticket -> ticketId != null && ticket.getId() == ticketId)
                .filter(ticket -> !TodoConstant.ENDED_BY_HUMAN.equals(ticket.getEndedBy()))
                .findFirst().orElse(null);
    }

    private String context(int userId, TodoThreadDetailDTO reference, TodoTicketDTO target,
                           TodoAssistantTurn revision, String content) {
        List<TodoThreadDTO> recent = query.pageList(userId, 0, 10).getData();
        StringBuilder text = new StringBuilder("最近更新的待办（最多十项，仅供参考）：\n");
        for (TodoThreadDTO item : recent) {
            text.append(item.getTitle()).append(" | ").append(item.getStage()).append(" | ")
                    .append(item.getLastTime());
            if (item.getSummary() != null) {
                text.append(" | ").append(TodoJsonTool.abbreviate(item.getSummary(), 400));
            }
            text.append('\n');
        }
        if (reference != null) {
            TodoThreadDTO thread = reference.getThread();
            text.append("\n用户明确引用的待办：").append(thread.getTitle()).append("\n状态：").append(thread.getStage())
                    .append("\n摘要：").append(thread.getSummary() == null ? "" : TodoJsonTool.abbreviate(thread.getSummary(), 1600));
            if (target != null) {
                text.append("\n可继续原工单，范围：").append(TodoJsonTool.abbreviate(target.getScope(), 1600));
                if (target.getQuestion() != null) {
                    text.append("\n原工单待回答：").append(TodoJsonTool.abbreviate(target.getQuestion(), 1600));
                }
            }
            reference.getMessages().stream()
                    .filter(message -> TodoConstant.PURPOSE_RESULT.equals(message.getPurpose())
                            || TodoConstant.PURPOSE_ANSWER.equals(message.getPurpose()))
                    .sorted(Comparator.comparingLong(TodoMessageDTO::getId).reversed())
                    .limit(2)
                    .forEach(message -> text.append("\n已有回复：").append(TodoJsonTool.abbreviate(message.getContent(), 2000)));
        }
        if (revision != null) {
            text.append("\n用户要修改的原执行建议：\n").append(revision.proposal());
        }
        return text.append("\n\n当前用户消息：\n").append(content).toString();
    }

    private void fail(int userId, long id, String reason) {
        mapper.resolve(userId, id, "failed", null, null, null, null, reason);
    }

    private void enqueue(int userId, long id) {
        TodoEffect effect = new TodoEffect();
        effect.setKind(TodoConstant.EFFECT_ASSISTANT);
        effect.setEffectKey(String.valueOf(id));
        effect.setPayload(TodoJsonTool.toJson(Map.of("userId", userId)));
        effect.setDueTime(new Date());
        if (effectMapper.updateByKey(effect) == 0) {
            effectMapper.insert(effect);
        }
        publisher.publishEvent(new TodoWakeEvent(null));
    }
}
