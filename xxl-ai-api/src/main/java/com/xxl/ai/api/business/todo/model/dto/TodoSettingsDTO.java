package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理 配置版本
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoSettingsDTO {

    private String block;       /* 配置块 */
    private int version;        /* 版本号，0 表示从未保存（使用默认值） */
    private Object content;     /* 配置内容 */
    private Object defaults;    /* 默认值（提示词块） */
    private int userId;         /* 保存人用户ID */
    private String addTime;     /* 保存时间 */

    public String getBlock() {
        return block;
    }

    public void setBlock(String block) {
        this.block = block;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public Object getDefaults() {
        return defaults;
    }

    public void setDefaults(Object defaults) {
        this.defaults = defaults;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getAddTime() {
        return addTime;
    }

    public void setAddTime(String addTime) {
        this.addTime = addTime;
    }

}
