package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoThread;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理线程 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoThreadMapper {

    int insert(TodoThread entity);

    int update(TodoThread entity);

    TodoThread load(@Param("id") long id);

    /** 加行锁查询（单一写入者：命令事务内先锁线程再写） */
    TodoThread loadForUpdate(@Param("id") long id);

    /** 查询有待研判或研判中的线程（调度扫描用） */
    List<TodoThread> listActive();

    /** 分页查询用户的线程（最新在前） */
    List<TodoThread> pageList(@Param("userId") int userId,
                              @Param("offset") int offset,
                              @Param("pagesize") int pagesize);

    /** 用户线程总数 */
    int pageListCount(@Param("userId") int userId);

}
