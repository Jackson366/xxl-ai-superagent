package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoAssistantTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理入口消息持久化
 *
 * @author xxl-ai 2026-11-12
 */
@Mapper
public interface TodoAssistantTurnMapper {

    int insert(@Param("userId") int userId, @Param("requestId") String requestId,
               @Param("content") String content, @Param("referenceId") Long referenceId,
               @Param("referenceTitle") String referenceTitle, @Param("revisesId") Long revisesId);

    TodoAssistantTurn loadByRequest(@Param("userId") int userId, @Param("requestId") String requestId,
                                    @Param("lock") boolean lock);

    TodoAssistantTurn load(@Param("userId") int userId, @Param("id") long id,
                           @Param("lock") boolean lock);

    List<TodoAssistantTurn> recent(@Param("userId") int userId, @Param("beforeId") Long beforeId,
                                   @Param("limit") int limit, @Param("resolvedOnly") boolean resolvedOnly);

    int resolve(@Param("userId") int userId, @Param("id") long id, @Param("state") String state,
                @Param("reply") String reply, @Param("proposal") String proposal,
                @Param("targetTicketId") Long targetTicketId, @Param("attentionId") String attentionId,
                @Param("errorCode") String errorCode);

    int submit(@Param("userId") int userId, @Param("id") long id, @Param("threadId") long threadId);

    int replaceProposal(@Param("userId") int userId, @Param("id") long id);

    int retry(@Param("userId") int userId, @Param("id") long id);
}
