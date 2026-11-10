package com.xxl.ai.api.business.todo.controller;

import com.xxl.ai.api.business.todo.model.dto.TodoSettingsDTO;
import com.xxl.ai.api.business.todo.model.dto.TodoSettingsSaveDTO;
import com.xxl.ai.api.business.todo.service.TodoSettingsService;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.response.Response;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 超级助理 设置 Controller（按块版本化保存；仅管理员）
 *
 * @author xxl-ai 2026-11-10
 */
@RestController
@RequestMapping("/todo/settings")
public class TodoSettingsController {

    @Resource
    private TodoSettingsService settingsService;

    /**
     * 读取配置块当前版本
     */
    @RequestMapping("/load")
    @XxlSso(permission = "todo:default")
    public Response<TodoSettingsDTO> load(@RequestParam String block) {
        return settingsService.load(block);
    }

    /**
     * 保存配置块（baseVersion 不一致时拒绝）
     */
    @RequestMapping("/save")
    @XxlSso(permission = "todo:default")
    public Response<String> save(HttpServletRequest request, @RequestBody TodoSettingsSaveDTO dto) {
        LoginInfo loginInfo = XxlSsoHelper.loginCheckWithAttr(request).getData();
        return settingsService.save(Integer.parseInt(loginInfo.getUserId()), dto);
    }

    /**
     * 配置块历史版本
     */
    @RequestMapping("/history")
    @XxlSso(permission = "todo:default")
    public Response<List<TodoSettingsDTO>> history(@RequestParam String block) {
        return settingsService.history(block);
    }

    /**
     * 设置页下拉：超级助理空间；传 spaceId 时附带该空间的模型、MCP、Skill
     */
    @RequestMapping("/options")
    @XxlSso(permission = "todo:default")
    public Response<Map<String, Object>> options(@RequestParam(required = false) Long spaceId) {
        return settingsService.options(spaceId);
    }

}
