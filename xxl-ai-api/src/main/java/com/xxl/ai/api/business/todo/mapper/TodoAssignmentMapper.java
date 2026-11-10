package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理派工 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoAssignmentMapper {

    int insert(TodoAssignment entity);

    int update(TodoAssignment entity);

    TodoAssignment load(@Param("id") long id);

    /** 查询工单的全部派工（按创建顺序） */
    List<TodoAssignment> listByTicket(@Param("ticketId") long ticketId);

    /** 按状态查询派工（重启恢复、槽位统计用） */
    List<TodoAssignment> listByState(@Param("state") String state);

}
