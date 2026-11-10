package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理工单输入 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoInput {

    private long id;             /* 输入ID */
    private long ticketId;       /* 工单ID */
    private String kind;         /* 类型：trigger、policy-changed、resumed、text、note */
    private Long triggerId;      /* 触发ID（kind=trigger/resumed） */
    private int human;           /* 人工提交：0-否、1-是 */
    private String content;      /* 内容(JSON)：策略变更影响、文本等 */
    private Long deliveredTo;    /* 最近送达的派工ID */
    private Date readTime;       /* Worker读取时间，空为未读 */
    private Date eventTime;      /* 产生时间 */
    private Date addTime;        /* 新增时间 */
    private Date updateTime;     /* 更新时间 */

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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Long getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(Long triggerId) {
        this.triggerId = triggerId;
    }

    public int getHuman() {
        return human;
    }

    public void setHuman(int human) {
        this.human = human;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getDeliveredTo() {
        return deliveredTo;
    }

    public void setDeliveredTo(Long deliveredTo) {
        this.deliveredTo = deliveredTo;
    }

    public Date getReadTime() {
        return readTime;
    }

    public void setReadTime(Date readTime) {
        this.readTime = readTime;
    }

    public Date getEventTime() {
        return eventTime;
    }

    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
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
