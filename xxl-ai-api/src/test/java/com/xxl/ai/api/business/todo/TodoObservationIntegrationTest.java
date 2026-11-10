package com.xxl.ai.api.business.todo;

import com.xxl.ai.api.business.harness.superagent.SuperagentTraceEvent;
import com.xxl.ai.api.business.todo.mapper.TodoThreadMapper;
import com.xxl.ai.api.business.todo.mapper.TodoEventMapper;
import com.xxl.ai.api.business.todo.model.entity.TodoThread;
import com.xxl.ai.api.business.todo.model.entity.TodoEvent;
import com.xxl.ai.api.business.todo.service.TodoObservationService;
import com.xxl.ai.api.business.todo.service.impl.TodoEffectServiceImpl;
import com.xxl.ai.api.business.todo.util.TodoEventSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 H2 / MyBatis 验证归属隔离、并发提交顺序、断点补齐和脱敏
 *
 * @author Codex 2026-11-11
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:observation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
class TodoObservationIntegrationTest {
    @MockitoBean
    TodoEffectServiceImpl dispatcher;
    @Autowired
    TodoThreadMapper threads;
    @Autowired
    TodoEventMapper events;
    @Autowired
    TodoObservationService observation;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    com.xxl.ai.api.business.todo.service.TodoEngineService engine;
    @Autowired
    com.xxl.ai.api.business.todo.mapper.TodoTicketMapper tickets;
    @Autowired
    com.xxl.ai.api.business.todo.mapper.TodoAssignmentMapper assignments;

    @Test
    void eventsResumeInCommitOrderAndStayPrivate() throws Exception {
        TodoThread thread = new TodoThread();
        thread.setUserId(1);
        thread.setTitle("观测验收");
        threads.insert(thread);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                observation.append(thread.getId(), "eval-1", null, null, null,
                        new SuperagentTraceEvent("router.started", "running", null,
                                "{\"nested\":{\"api_key\":\"private-value\"}}", null, null));
                written.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                return null;
            }));
            assertTrue(written.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> observation.append(thread.getId(), "eval-1", null, null, null,
                    new SuperagentTraceEvent("router.suggested", "success", "accept", null, null, 10L)));
            assertTrue(observation.events(1, thread.getId(), 0, 200).isEmpty());
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        List<TodoEvent> firstPage = observation.events(1, thread.getId(), 0, 1);
        assertEquals("router.started", firstPage.get(0).getKind());
        assertFalse(firstPage.get(0).getDetail().contains("private-value"));
        List<TodoEvent> resumed = observation.events(1, thread.getId(), firstPage.get(0).getId(), 200);
        assertEquals(1, resumed.size());
        assertEquals("router.suggested", resumed.get(0).getKind());
        assertThrows(IllegalArgumentException.class, () -> observation.events(2, thread.getId(), 0, 200));
        assertTrue(events.workers(2).isEmpty());
    }

    @Test
    void localUpgradeIsIdempotentAndPreservesExistingData() throws Exception {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:observation_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("CREATE TABLE xxl_ai_user(id INT PRIMARY KEY)");
        jdbc.execute("INSERT INTO xxl_ai_user VALUES (123)");
        var initializer = new com.xxl.ai.api.framework.config.LocalDatabaseConfig().localDatabaseInitializer(source);
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        assertEquals(123, jdbc.queryForObject("SELECT id FROM xxl_ai_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM xxl_ai_todo_event", Integer.class));
    }

    @Test
    void stoppedRoundCannotBecomeCompletedWhenModelReturnsLate() {
        TodoThread thread = new TodoThread();
        thread.setUserId(1);
        thread.setTitle("停止验收");
        threads.insert(thread);
        var ticket = new com.xxl.ai.api.business.todo.model.entity.TodoTicket();
        ticket.setThreadId(thread.getId());
        ticket.setUserId(1);
        ticket.setTitle("停止中的工单");
        ticket.setMandate("{}");
        tickets.insert(ticket);
        var assignment = new com.xxl.ai.api.business.todo.model.entity.TodoAssignment();
        assignment.setTicketId(ticket.getId());
        assignment.setState("running");
        assignments.insert(assignment);
        var round = new com.xxl.ai.api.business.todo.model.dto.TodoRound("late-round", ticket.getId(), assignment.getId(),
                true, 1, 3, 6, "", "", "", List.of(), List.of(), "", List.of(), new java.util.Date());
        assertTrue(engine.stop(1, ticket.getId()).isSuccess());
        engine.endRound(round, "迟到的模型输出", null, false);
        assertEquals("stopped", tickets.load(ticket.getId()).getEndedOutcome());
        var recorded = observation.events(1, thread.getId(), 0, 200);
        assertTrue(recorded.stream().anyMatch(event -> event.getKind().equals("assignment.ended") && "stopped".equals(event.getSummary())));
        assertEquals("cancelled", recorded.get(recorded.size() - 1).getStatus());
        assertTrue(events.workers(1).stream().noneMatch(worker -> worker.getTicketId() == ticket.getId()));
    }

    @Test
    void commonCredentialsAreRedactedAndPayloadBounded() {
        String clean = TodoEventSanitizer.clean("Authorization: Bearer very-secret", 100);
        assertFalse(clean.contains("very-secret"));
        assertEquals(51, TodoEventSanitizer.clean("x".repeat(1000), 50).length());
        assertFalse(TodoEventSanitizer.clean("{\"headers\":{\"Authorization\":\"Bearer hidden\"},\"token\":\"private\"}", 1000).contains("hidden"));
    }
}
