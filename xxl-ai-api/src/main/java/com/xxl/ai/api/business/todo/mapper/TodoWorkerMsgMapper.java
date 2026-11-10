package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoWorkerMsg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理Worker会话消息 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoWorkerMsgMapper {

    int insert(TodoWorkerMsg entity);

    TodoWorkerMsg load(@Param("id") long id);

    /** 查询工单的会话消息（按写入顺序） */
    List<TodoWorkerMsg> listByTicket(@Param("ticketId") long ticketId);

}
