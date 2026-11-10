package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoEffect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 超级助理副作用队列 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoEffectMapper {

    TodoEffect load(@Param("id") long id);

    /** 入队：新增一条（同类型同对象已存在时抛 DuplicateKeyException，由调用方改走 updateByKey） */
    int insert(TodoEffect effect);

    /** 合并：同类型同对象已存在时换成新参数、版本加一、清零失败次数，保留队列位置（id 不变） */
    int updateByKey(TodoEffect effect);

    /** 按类型与对象键查询 */
    TodoEffect loadByKey(@Param("kind") String kind, @Param("effectKey") String effectKey);

    /** 查询到期且未被抢占的副作用（按队列位置） */
    List<TodoEffect> listDue(@Param("kind") String kind, @Param("limit") int limit, @Param("now") Date now);

    /** 查询某类型最早的一条（有序类型：前一条未完成时后续等待） */
    TodoEffect loadHead(@Param("kind") String kind);

    /** 抢占：版本一致且未被他人持有时成功，返回 1 */
    int claim(@Param("id") long id,
              @Param("generation") int generation,
              @Param("owner") String owner,
              @Param("ownerUntil") Date ownerUntil,
              @Param("now") Date now);

    /** 执行成功：仅删除同一版本（执行期间被合并的新参数保留，待下次执行） */
    int finish(@Param("id") long id, @Param("generation") int generation);

    /** 执行失败或暂不可执行：记录失败次数与错误，延迟后重试，并释放抢占 */
    int fail(@Param("id") long id,
             @Param("generation") int generation,
             @Param("attempts") int attempts,
             @Param("error") String error,
             @Param("dueTime") Date dueTime);

    /** 释放抢占（执行结束后调用；版本已变时由此让新参数可被执行） */
    int release(@Param("id") long id, @Param("owner") String owner);

    /** 撤销（如工单停止时撤回待发送消息） */
    int deleteByKey(@Param("kind") String kind, @Param("effectKey") String effectKey);

    /** 查询连续失败达到阈值的副作用（健康问题） */
    List<TodoEffect> listProblem(@Param("minAttempts") int minAttempts);

    /** 释放本实例持有的全部抢占（重启后调用，避免等待租期过期） */
    int releaseByOwner(@Param("owner") String owner);

}
