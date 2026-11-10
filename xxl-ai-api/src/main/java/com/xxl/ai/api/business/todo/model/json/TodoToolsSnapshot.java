package com.xxl.ai.api.business.todo.model.json;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作工具快照（xxl_ai_todo_assignment.tools 列的 JSON 值对象）：派工启动时记下，配置变更只影响新派工
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoToolsSnapshot {

    public List<Long> mcpIds = new ArrayList<>();      /* 可用 MCP */
    public List<Long> skillIds = new ArrayList<>();    /* 可用 Skill */

}
