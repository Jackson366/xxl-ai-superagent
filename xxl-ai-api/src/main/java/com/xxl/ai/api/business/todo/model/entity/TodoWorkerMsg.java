package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理Worker会话消息 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoWorkerMsg {

    private long id;              /* 消息ID */
    private long ticketId;        /* 工单ID */
    private long assignmentId;    /* 派工ID */
    private String role;          /* 角色：user、assistant、tool */
    private String inputIds;      /* 本条承载的输入ID(JSON数组)，用于标记已读 */
    private String content;       /* 消息内容(JSON)：文本、工具调用与结果 */
    private Date addTime;         /* 新增时间 */
    private Date updateTime;      /* 更新时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTicketId() {
        return ticketId;
    }

    public void setTicketId(long ticketId) {
        this.ticketId = ticketId;
    }

    public long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getInputIds() {
        return inputIds;
    }

    public void setInputIds(String inputIds) {
        this.inputIds = inputIds;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
