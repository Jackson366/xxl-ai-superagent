package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理配置版本 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoSettings {

    private long id;                 /* 配置版本ID */
    private String settingsBlock;    /* 配置块：policy-策略、router-研判、worker-执行、prompts-提示词 */
    private int version;             /* 块内版本号，从1递增 */
    private String content;          /* 配置内容(JSON) */
    private String baseline;         /* 覆盖默认值时记录的默认基线(JSON) */
    private int userId;              /* 保存人用户ID */
    private Date addTime;            /* 新增时间 */
    private Date updateTime;         /* 更新时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSettingsBlock() {
        return settingsBlock;
    }

    public void setSettingsBlock(String settingsBlock) {
        this.settingsBlock = settingsBlock;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBaseline() {
        return baseline;
    }

    public void setBaseline(String baseline) {
        this.baseline = baseline;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Date getAddTime() {
        return addTime;
    }

    public void setAddTime(Date addTime) {
        this.addTime = addTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

}
