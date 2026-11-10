package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoInput;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理工单输入 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoInputMapper {

    int insert(TodoInput entity);

    int update(TodoInput entity);

    TodoInput load(@Param("id") long id);

    /** 查询工单的全部输入（按时间正序） */
    List<TodoInput> listByTicket(@Param("ticketId") long ticketId);

    /** 按ID集合查询 */
    List<TodoInput> listByIds(@Param("ids") List<Long> ids);

}
