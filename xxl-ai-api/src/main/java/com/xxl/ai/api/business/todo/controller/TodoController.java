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
