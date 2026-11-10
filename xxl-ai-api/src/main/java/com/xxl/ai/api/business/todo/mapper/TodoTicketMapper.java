package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理工单 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoTicketMapper {

    int insert(TodoTicket entity);

    int update(TodoTicket entity);

    TodoTicket load(@Param("id") long id);

    /** 加行锁查询（单一写入者：命令事务内先锁工单再写） */
    TodoTicket loadForUpdate(@Param("id") long id);

    /** 按发起触发查询（同一触发只建一个工单） */
    TodoTicket loadByOriginTrigger(@Param("originTriggerId") long originTriggerId);

    /** 查询线程的全部工单 */
    List<TodoTicket> listByThread(@Param("threadId") long threadId);

    /** 查询进行中的工单（调度与恢复用） */
    List<TodoTicket> listLive();

}
