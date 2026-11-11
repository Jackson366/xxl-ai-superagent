package com.xxl.ai.api.business.todo;

import com.xxl.ai.api.business.harness.superagent.SuperagentRouterTool;
import com.xxl.ai.api.business.supplier.model.SupplierRuntime;
import com.xxl.ai.api.business.todo.constant.TodoConstant;
import com.xxl.ai.api.business.todo.mapper.TodoAssistantTurnMapper;
import com.xxl.ai.api.business.todo.mapper.TodoAssignmentMapper;
import com.xxl.ai.api.business.todo.mapper.TodoWorkerMsgMapper;
import com.xxl.ai.api.business.todo.mapper.TodoEffectMapper;
import com.xxl.ai.api.business.todo.mapper.TodoThreadMapper;
import com.xxl.ai.api.business.todo.mapper.TodoTicketMapper;
import com.xxl.ai.api.business.todo.mapper.TodoTriggerMapper;
import com.xxl.ai.api.business.todo.model.dto.TodoRound;
import com.xxl.ai.api.business.todo.model.dto.TodoSubmitDTO;
import com.xxl.ai.api.business.todo.model.entity.TodoAssistantTurn;
import com.xxl.ai.api.business.todo.model.entity.TodoAssignment;
import com.xxl.ai.api.business.todo.model.entity.TodoThread;
import com.xxl.ai.api.business.todo.model.entity.TodoTicket;
import com.xxl.ai.api.business.todo.model.entity.TodoTrigger;
import com.xxl.ai.api.business.todo.model.json.TodoDisposition;
import com.xxl.ai.api.business.todo.model.json.TodoMandate;
import com.xxl.ai.api.business.todo.model.json.TodoSettingsContent;
import com.xxl.ai.api.business.todo.service.TodoAssistantService;
import com.xxl.ai.api.business.todo.service.TodoEngineService;
import com.xxl.ai.api.business.todo.service.TodoSettingsService;
import com.xxl.ai.api.business.todo.service.impl.TodoEffectServiceImpl;
import com.xxl.ai.api.business.todo.util.TodoJsonTool;
import com.xxl.tool.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * H2 验证助理受理、建议确认、幂等与指定工单的归属隔离
 *
 * @author xxl-ai 2026-11-12
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:todo_assistant;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
class TodoAssistantIntegrationTest {

    @MockitoBean
    TodoEffectServiceImpl dispatcher;
    @MockitoBean
    TodoSettingsService settings;
    @MockitoBean
    SuperagentRouterTool router;
    @Autowired
    TodoAssistantService assistant;
    @Autowired
    TodoEngineService engine;
    @Autowired
    TodoAssistantTurnMapper turns;
    @Autowired
    TodoEffectMapper effects;
    @Autowired
    TodoThreadMapper threads;
    @Autowired
    TodoTicketMapper tickets;
    @Autowired
    TodoTriggerMapper triggers;
    @Autowired
    TodoAssignmentMapper assignments;
    @Autowired
    TodoWorkerMsgMapper workerMessages;

    @BeforeEach
    void model() {
        TodoSettingsContent.Model model = new TodoSettingsContent.Model();
        model.spaceId = 1;
        model.routerSupplierId = 2;
        model.routerModelId = 3;
        when(settings.model()).thenReturn(model);
        when(settings.worker()).thenReturn(new TodoSettingsContent.Worker());
        when(settings.runtime(1, 2, 3)).thenReturn(Response.ofSuccess(new SupplierRuntime()));
    }

    @Test
    void readOnlyAnswerDoesNotCreateTodoAndSendIsIdempotent() {
        TodoAssistantService.Send request = new TodoAssistantService.Send(UUID.randomUUID().toString(), "汇总待办", null, null);
        TodoAssistantTurn first = assistant.send(1, request).getData();
        assertEquals("pending", first.state());
        assertEquals(first.id(), assistant.send(1, request).getData().id());
        assertNotNull(effects.loadByKey(TodoConstant.EFFECT_ASSISTANT, String.valueOf(first.id())));

        modelReplies("answer", "这是待办摘要。", null);
        assistant.respond(1, first.id());
        assertEquals("answered", turns.load(1, first.id(), false).state());
        assertFalse(assistant.confirm(1, first.id()).isSuccess());
        assertNull(turns.load(2, first.id(), false));
    }

    @Test
    void twoConfirmationsCreateOneTodo() throws Exception {
        modelReplies("create", "请确认范围。", "仅汇总本周已完成事项");
        TodoAssistantTurn turn = send("整理本周周报", null, null);
        assertNull(turns.load(1, turn.id(), false).threadId());
        assertFalse(assistant.confirm(2, turn.id()).isSuccess());

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> assistant.confirm(1, turn.id()));
            var second = pool.submit(() -> assistant.confirm(1, turn.id()));
            long threadId = first.get(5, TimeUnit.SECONDS).getData();
            assertEquals(threadId, second.get(5, TimeUnit.SECONDS).getData());
            assertEquals(1, triggers.listByThread(threadId).size());
            assertEquals("仅汇总本周已完成事项", triggers.listByThread(threadId).getFirst().getContent());
            assertEquals("submitted", turns.load(1, turn.id(), false).state());
        }
    }

    @Test
    void revisedProposalCannotBeConfirmedAndPriorScopeGoesToModel() {
        modelReplies("create", "请确认范围。", "整理本周所有事项");
        TodoAssistantTurn original = send("整理周报", null, null);
        when(router.reply(any(), anyList(), anyList(), contains("用户要修改的原执行建议：\n整理本周所有事项"), anyString()))
                .thenReturn(new SuperagentRouterTool.AssistantReply("create", "范围已更新。", "仅整理本周已完成事项"));
        TodoAssistantTurn revision = send("只保留已完成的事项", null, original.id());
        assertEquals("replaced", turns.load(1, original.id(), false).state());
        assertFalse(assistant.confirm(1, original.id()).isSuccess());
        assertEquals("仅整理本周已完成事项", revision.proposal());
    }

    @Test
    void referencedTodoCanOnlyDeliverToItsSelectedTicket() {
        TodoThread thread = new TodoThread();
        thread.setUserId(1);
        thread.setTitle("需要续办的任务");
        threads.insert(thread);
        TodoTicket primary = ticket(thread.getId());
        TodoTicket other = ticket(thread.getId());
        thread.setSubscriptions(TodoJsonTool.longListJson(List.of(primary.getId(), other.getId())));
        threads.update(thread);
        assertFalse(assistant.send(2, new TodoAssistantService.Send(UUID.randomUUID().toString(), "续办", thread.getId(), null)).isSuccess());

        TodoSubmitDTO dto = new TodoSubmitDTO();
        dto.setRequestId(UUID.randomUUID().toString());
        dto.setThreadId(thread.getId());
        dto.setContent("补充范围");
        dto.setKind(TodoConstant.TRIGGER_APPEND);
        assertTrue(engine.submitFromAssistant(1, dto, primary.getId()).isSuccess());
        var trigger = triggers.loadByKey(dto.getRequestId());
        var disposition = TodoJsonTool.fromJson(trigger.getDisposition(), TodoDisposition.class);
        assertEquals(List.of(primary.getId()), disposition.fanout);
        assertEquals(primary.getId(), disposition.targetTicketId);
        assertFalse(engine.requeue(1, trigger.getId()).isSuccess());
    }

    @Test
    void restartingKeepsTicketAndRejectsLateOutputFromStoppedAssignment() {
        TodoThread thread = new TodoThread();
        thread.setUserId(1);
        thread.setTitle("停止后继续");
        threads.insert(thread);
        TodoTicket ticket = ticket(thread.getId());
        TodoMandate mandate = new TodoMandate();
        mandate.actorUserId = 1;
        mandate.strength = TodoConstant.STRENGTH_EXPLICIT;
        mandate.scope = "只整理已完成事项";
        ticket.setMandate(TodoJsonTool.toJson(mandate));
        tickets.update(ticket);
        TodoAssignment old = new TodoAssignment();
        old.setTicketId(ticket.getId());
        old.setState(TodoConstant.ASSIGNMENT_RUNNING);
        assignments.insert(old);

        assertTrue(engine.stop(1, ticket.getId()).isSuccess());
        assertFalse(engine.restart(2, ticket.getId()).isSuccess());
        assertTrue(engine.restart(1, ticket.getId()).isSuccess());
        assertFalse(engine.restart(1, ticket.getId()).isSuccess());
        TodoRound late = new TodoRound("late", ticket.getId(), old.getId(), true, 1, 2, 3,
                "", "", "", List.of(), List.of(), "", List.of(), new Date());
        engine.endRound(late, "旧派工迟到内容", null, false);

        assertNull(tickets.load(ticket.getId()).getEndedTime());
        assertEquals(ticket.getMandate(), tickets.load(ticket.getId()).getMandate());
        assertEquals(2, assignments.listByTicket(ticket.getId()).size());
        assertTrue(workerMessages.listByTicket(ticket.getId()).isEmpty());
    }

    private TodoAssistantTurn send(String content, Long referenceId, Long revisesId) {
        var turn = assistant.send(1, new TodoAssistantService.Send(UUID.randomUUID().toString(), content, referenceId, revisesId)).getData();
        assistant.respond(1, turn.id());
        return turns.load(1, turn.id(), false);
    }

    private void modelReplies(String kind, String reply, String task) {
        when(router.reply(any(), anyList(), anyList(), anyString(), anyString()))
                .thenReturn(new SuperagentRouterTool.AssistantReply(kind, reply, task));
    }

    private TodoTicket ticket(long threadId) {
        TodoTrigger origin = new TodoTrigger();
        origin.setTriggerKey(UUID.randomUUID().toString());
        origin.setThreadId(threadId);
        origin.setUserId(1);
        origin.setKind(TodoConstant.TRIGGER_CREATE);
        origin.setContent("工单来源");
        origin.setEventTime(new Date());
        triggers.insert(origin);
        TodoTicket ticket = new TodoTicket();
        ticket.setOriginTriggerId(origin.getId());
        ticket.setThreadId(threadId);
        ticket.setUserId(1);
        ticket.setTitle("工单");
        ticket.setMandate("{}");
        tickets.insert(ticket);
        return ticket;
    }
}
