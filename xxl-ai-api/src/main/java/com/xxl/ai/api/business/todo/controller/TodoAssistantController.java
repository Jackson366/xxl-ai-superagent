package com.xxl.ai.api.business.todo.controller;

import com.xxl.ai.api.business.todo.constant.TodoConstant;
import com.xxl.ai.api.business.todo.model.dto.TodoAssistantNoticeDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoSubmitDTO;
import com.xxl.ai.api.business.todo.model.entity.TodoAssistantTurn;
import com.xxl.ai.api.business.todo.service.TodoAssistantService;
import com.xxl.ai.api.business.todo.service.TodoEngineService;
import com.xxl.ai.api.business.todo.service.TodoQueryService;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 超级助理统一入口
 *
 * @author xxl-ai 2026-11-12
 */
@RestController
@RequestMapping("/todo/assistant")
public class TodoAssistantController {

    @Resource
    private TodoAssistantService assistant;
    @Resource
    private TodoEngineService engine;

    @Resource
    private TodoQueryService query;

    @GetMapping("/turns")
    @XxlSso(permission = "todo:default")
    public Response<List<TodoAssistantTurn>> turns(HttpServletRequest request, @RequestParam(required = false) Long beforeId) {
        return Response.ofSuccess(assistant.recent(userId(request), beforeId));
    }

    @GetMapping("/notices")
    @XxlSso(permission = "todo:default")
    public Response<List<TodoAssistantNoticeDTO>> notices(HttpServletRequest request) {
        return Response.ofSuccess(query.assistantNotices(userId(request)));
    }

    @PostMapping("/answer")
    @XxlSso(permission = "todo:default")
    public Response<Long> answer(HttpServletRequest request, @RequestBody Answer input) {
        int userId = userId(request);
        TodoSubmitDTO submit = new TodoSubmitDTO();
        submit.setKind(TodoConstant.TRIGGER_ANSWER);
        submit.setRequestId(input.requestId());
        submit.setThreadId(input.threadId());
        submit.setAttentionId(input.attentionId());
        submit.setContent(input.content());
        Response<Long> result;
        try {
            result = engine.submitFromAssistant(userId, submit, input.ticketId());
        } catch (DuplicateKeyException collision) {
            result = Response.ofFail("请求标识冲突");
        }
        if (result.isSuccess()) {
            return result;
        }
        // 并发重发可能在原请求提交后才拿到工单锁，回读已受理的请求。
        Long threadId = engine.findSubmitted(userId, input.requestId());
        return threadId == null ? result : Response.ofSuccess(threadId);
    }

    public record Answer(String requestId, long threadId, long ticketId, String attentionId, String content) {
    }

    @PostMapping("/send")
    @XxlSso(permission = "todo:default")
    public Response<TodoAssistantTurn> send(HttpServletRequest request, @RequestBody TodoAssistantService.Send input) {
        int userId = userId(request);
        try {
            return assistant.send(userId, input);
        } catch (DuplicateKeyException collision) {
            TodoAssistantTurn turn = assistant.findSent(userId, input.requestId());
            return turn == null ? Response.ofFail("请求标识冲突") : Response.ofSuccess(turn);
        }
    }

    @PostMapping("/confirm")
    @XxlSso(permission = "todo:default")
    public Response<Long> confirm(HttpServletRequest request, @RequestBody TodoAssistantService.TurnCommand input) {
        return assistant.confirm(userId(request), input.id());
    }

    @PostMapping("/retry")
    @XxlSso(permission = "todo:default")
    public Response<TodoAssistantTurn> retry(HttpServletRequest request, @RequestBody TodoAssistantService.TurnCommand input) {
        return assistant.retry(userId(request), input.id());
    }

    @PostMapping("/restart")
    @XxlSso(permission = "todo:default")
    public Response<String> restart(HttpServletRequest request, @RequestBody TodoAssistantService.TurnCommand input) {
        return engine.restart(userId(request), input.id());
    }

    private int userId(HttpServletRequest request) {
        return Integer.parseInt(XxlSsoHelper.loginCheckWithAttr(request).getData().getUserId());
    }
}
