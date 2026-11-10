package com.xxl.ai.api.business.todo.service;

import com.xxl.ai.api.business.todo.mapper.TodoEventMapper;
import com.xxl.ai.api.business.todo.mapper.TodoThreadMapper;
import com.xxl.ai.api.business.todo.model.entity.TodoEvent;
import com.xxl.ai.api.business.todo.model.entity.TodoThread;
import com.xxl.ai.api.business.todo.util.TodoEventSanitizer;
import com.xxl.ai.api.business.harness.superagent.SuperagentTraceEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.List;

/**
 * 超级助理事件存储与归属校验；同线程串行追加，确保续传游标不越过未提交事件
 *
 * @author Codex 2026-11-11
 */
@Service
public class TodoObservationService {
    @Resource
    private TodoEventMapper eventMapper;
    @Resource
    private TodoThreadMapper threadMapper;

    /* 业务事务中加入同一事务；运行时独立调用时开启短事务 */
    @Transactional(rollbackFor = Exception.class)
    public void append(long threadId, String evalId, Long ticketId, Long assignmentId, String roundId,
                       SuperagentTraceEvent trace) {
        if (threadMapper.loadForUpdate(threadId) == null) {
            return;
        }
        TodoEvent event = new TodoEvent();
        event.setThreadId(threadId);
        event.setEvalId(evalId);
        event.setTicketId(ticketId);
        event.setAssignmentId(assignmentId);
        event.setRoundId(roundId);
        event.setCallId(trace.callId());
        event.setKind(trace.kind());
        event.setStatus(trace.status());
        event.setSummary(TodoEventSanitizer.clean(trace.summary(), 900));
        event.setDetail(TodoEventSanitizer.clean(trace.detail(), 8000));
        event.setDurationMs(trace.durationMs());
        event.setEventTime(new Date());
        eventMapper.insert(event);
    }

    /* 每次读取都重新核对归属，SSE 重连和长连接均不能跨用户读取 */
    public List<TodoEvent> events(int userId, long threadId, long afterId, int limit) {
        TodoThread thread = threadMapper.load(threadId);
        if (thread == null || thread.getUserId() != userId) {
            throw new IllegalArgumentException("待办不存在");
        }
        return eventMapper.listAfter(threadId, Math.max(0, afterId), Math.max(1, Math.min(limit, 200)));
    }
}
