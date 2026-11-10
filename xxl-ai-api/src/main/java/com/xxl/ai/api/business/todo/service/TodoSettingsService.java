package com.xxl.ai.api.business.todo.service;

import com.xxl.ai.api.business.todo.model.dto.TodoSettingsDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoSettingsSaveDTO;
import com.xxl.ai.api.business.todo.model.json.TodoSettingsContent;
import com.xxl.ai.api.business.supplier.model.SupplierRuntime;
import com.xxl.tool.response.Response;

import java.util.List;
import java.util.Map;

/**
 * 超级助理 配置 Service（按块版本化保存，可回溯）
 *
 * @author xxl-ai 2026-11-10
 */
public interface TodoSettingsService {

    /**
     * 读取配置块当前版本（从未保存时返回默认值，版本号为 0）
     */
    Response<TodoSettingsDTO> load(String block);

    /**
     * 保存配置块：baseVersion 与当前版本不一致时拒绝，防止覆盖他人修改
     */
    Response<String> save(int userId, TodoSettingsSaveDTO dto);

    /**
     * 配置块历史版本（最新在前）
     */
    Response<List<TodoSettingsDTO>> history(String block);

    /**
     * 设置页下拉：超级助理空间；传 spaceId 时附带该空间的对话模型、MCP、Skill
     */
    Response<Map<String, Object>> options(Long spaceId);

    /**
     * 当前策略块
     */
    TodoSettingsContent.Policy policy();

    /**
     * 当前模型块
     */
    TodoSettingsContent.Model model();

    /**
     * 当前执行块
     */
    TodoSettingsContent.Worker worker();

    /**
     * 当前提示词块（未覆盖的字段取默认值）
     */
    TodoSettingsContent.Prompts prompts();

    /**
     * 当前策略版本：hash(修订号 + 策略块)；模型、并发、暂停、提示词不进入策略版本
     */
    String policyVersion();

    /**
     * 是否就绪：返回未就绪原因编码（TodoConstant.REASON_*），就绪返回 null
     */
    String readyCode();

    /**
     * 解析模型运行时：校验空间仍标记且正常，再经供应商服务校验归属与状态
     */
    Response<SupplierRuntime> runtime(long spaceId, long supplierId, long modelId);

}
