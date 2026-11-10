package com.xxl.ai.api.business.todo.model.json;

import java.util.ArrayList;
import java.util.List;

/**
 * 超级助理配置块内容（xxl_ai_todo_settings.content 列的 JSON 值对象），按块分类
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoSettingsContent {

    private TodoSettingsContent() {
    }

    /**
     * 策略块：进入策略版本
     */
    public static class Policy {

        public String mode = "observe";    /* 推断请求的接单方式：off、observe、auto（明确请求不受影响） */

    }

    /**
     * 模型块：先选超级助理空间，再选该空间的供应商模型
     */
    public static class Model {

        public long spaceId;               /* 超级助理空间ID */
        public long routerSupplierId;      /* Router 供应商ID */
        public long routerModelId;         /* Router 模型ID */
        public long workerSupplierId;      /* Worker 供应商ID */
        public long workerModelId;         /* Worker 模型ID */

    }

    /**
     * 执行块：工作工具、并发、暂停
     */
    public static class Worker {

        public List<Long> mcpIds = new ArrayList<>();      /* 可用 MCP */
        public List<Long> skillIds = new ArrayList<>();    /* 可用 Skill */
        public int maxWorkers = 3;                         /* 并发执行槽位数 */
        public boolean paused;                             /* 暂停处理：不开始新研判、不启动新一轮执行 */

    }

    /**
     * 提示词块：团队可改的 Router、Worker 指令（固定规则由代码追加，不可覆盖）
     */
    public static class Prompts {

        public String router;              /* Router 指令 */
        public String worker;              /* Worker 指令 */

    }

}
