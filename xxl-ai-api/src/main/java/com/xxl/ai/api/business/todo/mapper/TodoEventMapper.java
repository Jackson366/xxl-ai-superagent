package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 超级助理追加事件 Mapper
 *
 * @author Codex 2026-11-11
 */
@Mapper
public interface TodoEventMapper {
    List<com.xxl.ai.api.business.todo.model.dto.TodoWorkerDTO> workers(@Param("userId") int userId);

    int insert(TodoEvent event);

    List<TodoEvent> listAfter(@Param("threadId") long threadId, @Param("afterId") long afterId,
                              @Param("limit") int limit);
}
