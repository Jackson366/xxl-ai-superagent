package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理消息 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoMessageMapper {

    TodoMessage load(@Param("id") long id);

    /** 写入消息（幂等键唯一，重复写入抛 DuplicateKeyException，由调用方忽略） */
    int insert(TodoMessage message);

    /** 按幂等键查询 */
    TodoMessage loadByKey(@Param("messageKey") String messageKey);

    /** 更新投递状态（仅从 pending 流转，已送达或已丢弃的不再变化） */
    int updateStatus(@Param("id") long id, @Param("status") String status);

    /** 丢弃工单全部待发送消息（人工停止时） */
    int dropPendingByTicket(@Param("ticketId") long ticketId);

    /** 查询工单的全部消息 */
    List<TodoMessage> listByTicket(@Param("ticketId") long ticketId);

    /** 分页查询收件箱（只含已送达；readStatus=-1 不过滤） */
    List<TodoMessage> pageList(@Param("userId") int userId,
                               @Param("readStatus") int readStatus,
                               @Param("offset") int offset,
                               @Param("pagesize") int pagesize);

    /** 收件箱总数 */
    int pageListCount(@Param("userId") int userId, @Param("readStatus") int readStatus);

    /** 标记已读（只处理本人的未读消息） */
    int markRead(@Param("userId") int userId, @Param("ids") List<Long> ids);

}
