package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoTrigger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理触发 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoTriggerMapper {

    int insert(TodoTrigger entity);

    TodoTrigger load(@Param("id") long id);

    /** 按幂等键查询 */
    TodoTrigger loadByKey(@Param("triggerKey") String triggerKey);

    /** 按ID集合查询 */
    List<TodoTrigger> listByIds(@Param("ids") List<Long> ids);

    /** 查询线程全部触发（按时间正序） */
    List<TodoTrigger> listByThread(@Param("threadId") long threadId);

    /** 更新去向（触发本身不可修改，只有去向随处理推进） */
    int updateDisposition(@Param("id") long id, @Param("disposition") String disposition);

}
