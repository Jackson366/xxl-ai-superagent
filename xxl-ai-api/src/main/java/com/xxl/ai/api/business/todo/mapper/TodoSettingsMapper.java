package com.xxl.ai.api.business.todo.mapper;

import com.xxl.ai.api.business.todo.model.entity.TodoSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 超级助理配置版本 Mapper
 *
 * @author xxl-ai 2026-11-10
 */
@Mapper
public interface TodoSettingsMapper {

    int insert(TodoSettings entity);

    TodoSettings load(@Param("id") long id);

    /** 查询配置块的最新版本 */
    TodoSettings loadLatest(@Param("settingsBlock") String settingsBlock);

    /** 查询配置块的历史版本（最新在前） */
    List<TodoSettings> listByBlock(@Param("settingsBlock") String settingsBlock);

}
