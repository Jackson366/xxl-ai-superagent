package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理 配置保存入参
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoSettingsSaveDTO {

    private String block;       /* 配置块 */
    private int baseVersion;    /* 编辑基于的版本号，不一致时拒绝保存 */
    private Object content;     /* 配置内容 */

    public String getBlock() {
        return block;
    }

    public void setBlock(String block) {
        this.block = block;
    }

    public int getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(int baseVersion) {
        this.baseVersion = baseVersion;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

}
