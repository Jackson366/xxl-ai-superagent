package com.xxl.ai.api.business.todo.controller;

import com.xxl.ai.api.business.todo.model.dto.TodoMessageDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoOverviewDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoSubmitDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDetailDTO;
import com.xxl.ai.api.business.todo.service.TodoEngineService;
import com.xxl.ai.api.business.todo.service.TodoQueryService;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.xxl.ai.api.business.todo.model.entity.TodoEvent;
import com.xxl.ai.api.business.todo.service.TodoObservationService;
import com.xxl.ai.api.business.harness.superagent.SuperagentEventStreamTool;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 超级助理 Controller（待办、工单、收件箱、概览；仅管理员）
 *
 * @author xxl-ai 2026-11-10
 */
@RestController
@RequestMapping("/todo")
public class TodoController {

    @Resource
    private TodoEngineService engine;
    @Resource
    private TodoQueryService queryService;
    @Resource
    private TodoObservationService observation;
    @Resource
    private SuperagentEventStreamTool eventStream;

    /**
     * 分页查询待办
     */
    @RequestMapping("/thread/pageList")
    @XxlSso(permission = "todo:default")
    public Response<PageModel<TodoThreadDTO>> pageList(HttpServletRequest request,
                                                       @RequestParam(required = false, defaultValue = "0") int offset,
                                                       @RequestParam(required = false, defaultValue = "10") int pagesize) {
        return Response.ofSuccess(queryService.pageList(userId(request), offset, pagesize));
    }

    /**
     * 待办详情（时间线）
     */
    @RequestMapping("/thread/load")
    @XxlSso(permission = "todo:default")
    public Response<TodoThreadDetailDTO> load(HttpServletRequest request, @RequestParam long id) {
        return queryService.load(userId(request), id);
    }

    /* 持久化事件的增量读取；刷新后可从游标补齐 */
    @GetMapping("/events")
    @XxlSso(permission = "todo:default")
    public Response<List<TodoEvent>> events(HttpServletRequest request, @RequestParam long threadId,
                                            @RequestParam(defaultValue = "0") long afterId,
                                            @RequestParam(defaultValue = "200") int limit) {
        try {
            return Response.ofSuccess(observation.events(userId(request), threadId, afterId, limit));
        } catch (IllegalArgumentException error) {
            return Response.ofFail(error.getMessage());
        }
    }

    /* SSE 只读历史，不触发模型或工具；每轮扫描重新校验归属 */
    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @XxlSso(permission = "todo:default")
    public SseEmitter eventStream(HttpServletRequest request, @RequestParam long threadId,
                                  @RequestParam(defaultValue = "0") long afterId) {
        int currentUser = userId(request);
        observation.events(currentUser, threadId, afterId, 1);
        return eventStream.open(afterId, cursor -> observation.events(currentUser, threadId, cursor, 200), TodoEvent::getId);
    }

    /**
     * 提交：新建、追加、回答（requestId 幂等）
     */
    @RequestMapping("/thread/submit")
    @XxlSso(permission = "todo:default")
    public Response<Long> submit(HttpServletRequest request, @RequestBody TodoSubmitDTO dto) {
        int userId = userId(request);
        try {
            return engine.submit(userId, dto);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：另一请求已受理，返回同一结果
            Long threadId = engine.findSubmitted(userId, dto.getRequestId());
            return threadId != null ? Response.ofSuccess(threadId) : Response.ofFail("请求标识冲突");
        }
    }

    /**
     * 重新研判一条提交
     */
    @RequestMapping("/thread/requeue")
    @XxlSso(permission = "todo:default")
    public Response<String> requeue(HttpServletRequest request, @RequestParam long triggerId) {
        return engine.requeue(userId(request), triggerId);
    }

    /**
     * 忽略未完成的研判
     */
    @RequestMapping("/decision/dismiss")
    @XxlSso(permission = "todo:default")
    public Response<String> dismiss(HttpServletRequest request, @RequestParam long id) {
        return engine.dismiss(userId(request), id);
    }

    /**
     * 停止工单
     */
    @RequestMapping("/ticket/stop")
    @XxlSso(permission = "todo:default")
    public Response<String> stop(HttpServletRequest request, @RequestParam long id) {
        return engine.stop(userId(request), id);
    }

    /**
     * 重试工单
     */
    @RequestMapping("/ticket/retry")
    @XxlSso(permission = "todo:default")
    public Response<String> retry(HttpServletRequest request, @RequestParam long id) {
        return engine.retry(userId(request), id);
    }

    /**
     * 收件箱分页
     */
    @RequestMapping("/message/pageList")
    @XxlSso(permission = "todo:default")
    public Response<PageModel<TodoMessageDTO>> messagePageList(HttpServletRequest request,
                                                               @RequestParam(required = false, defaultValue = "0") int offset,
                                                               @RequestParam(required = false, defaultValue = "10") int pagesize,
                                                               @RequestParam(required = false, defaultValue = "-1") int readStatus) {
        return Response.ofSuccess(queryService.messagePageList(userId(request), readStatus, offset, pagesize));
    }

    /**
     * 标记已读
     */
    @RequestMapping("/message/read")
    @XxlSso(permission = "todo:default")
    public Response<String> read(HttpServletRequest request, @RequestBody List<Long> ids) {
        return queryService.markRead(userId(request), ids);
    }

    /**
     * 概览：要我吗、健康吗
     */
    @RequestMapping("/overview")
    @XxlSso(permission = "todo:default")
    public Response<TodoOverviewDTO> overview(HttpServletRequest request) {
        return Response.ofSuccess(queryService.overview(userId(request)));
    }

    /**
     * 当前登录用户ID
     */
    private int userId(HttpServletRequest request) {
        LoginInfo loginInfo = XxlSsoHelper.loginCheckWithAttr(request).getData();
        return Integer.parseInt(loginInfo.getUserId());
    }

}
