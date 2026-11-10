package com.xxl.ai.api.business.todo.service;

import com.xxl.ai.api.business.todo.model.dto.TodoMessageDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoOverviewDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoThreadDetailDTO;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

import java.util.List;

/**
 * 超级助理 查询 Service（只读：待办列表、时间线、概览、收件箱；唯一的写操作是标记已读）
 *
 * @author xxl-ai 2026-11-10
 */
public interface TodoQueryService {

    /**
     * 分页查询待办（最新在前），每行带阶段和“在等谁、需要什么”
     */
    PageModel<TodoThreadDTO> pageList(int userId, int offset, int pagesize);

    /**
     * 待办详情：触发与去向、研判决定、工单与派工、发给你的消息
     */
    Response<TodoThreadDetailDTO> load(int userId, long threadId);

    /**
     * 概览：要我吗（需要你处理的待办）、健康吗（系统问题、就绪、负载、未读）
     */
    TodoOverviewDTO overview(int userId);

    /**
     * 收件箱分页（readStatus=-1 不过滤）
     */
    PageModel<TodoMessageDTO> messagePageList(int userId, int readStatus, int offset, int pagesize);

    /**
     * 标记已读
     */
    Response<String> markRead(int userId, List<Long> ids);

}
