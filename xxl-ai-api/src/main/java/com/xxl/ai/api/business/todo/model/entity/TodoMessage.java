package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理消息 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoMessage {

    private long id;              /* 消息ID */
    private String messageKey;    /* 幂等键，重复发送只保留一条 */
    private int userId;           /* 接收用户ID */
    private Long ticketId;        /* 工单ID */
    private Long triggerId;       /* 回复的触发ID */
    private String purpose;       /* 用途：acknowledgement、answer、attention、result、notice */
    private String content;       /* 消息内容(Markdown) */
    private String status;        /* 投递状态：pending-待发送、sent-已送达、dropped-已丢弃 */
    private int readStatus;       /* 已读：0-未读、1-已读 */
    private Date readTime;        /* 已读时间 */
    private Date addTime;         /* 新增时间 */
    private Date updateTime;      /* 更新时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(Long triggerId) {
        this.triggerId = triggerId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(int readStatus) {
        this.readStatus = readStatus;
    }

    public Date getReadTime() {
        return readTime;
    }

    public void setReadTime(Date readTime) {
        this.readTime = readTime;
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
