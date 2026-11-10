package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoDecision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理决定 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoDecisionMapper {

    int insert(TodoDecision entity);

    TodoDecision load(@Param("id") long id);

    /** 按EvalID查询（一个Eval只产生一条决定） */
    TodoDecision loadByEvalId(@Param("evalId") String evalId);

    /** 查询线程全部决定（按时间正序） */
    List<TodoDecision> listByThread(@Param("threadId") long threadId);

    /** 忽略未完成问题（决定本身不可修改，只记录人工忽略） */
    int updateDismissed(@Param("id") long id);

    /** 回填接单的工单ID（接单时先写决定再写工单，仅允许回填一次） */
    int updateTicketId(@Param("id") long id, @Param("ticketId") long ticketId);

}
