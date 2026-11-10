package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理决定 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoDecision {

    private long id;                 /* 决定ID */
    private String evalId;           /* EvalID */
    private long threadId;           /* 线程ID */
    private String triggerIds;       /* 本批触发ID(JSON数组) */
    private String policyVersion;    /* 策略版本 */
    private String kind;             /* 决定：accept-接单、stop-停止、ignore-忽略、unavailable-未完成 */
    private String strength;         /* 强度：explicit-明确、inferred-推断 */
    private String reason;           /* 原因(JSON数组)：code + values */
    private Long ticketId;           /* 接单或停止的工单ID */
    private String mandate;          /* 授权(JSON)：strength、policyVersion、basis、actor、scope */
    private String audit;            /* 研判审计(JSON)：规则判定、direction、would_accept */
    private int dismissed;           /* 未完成问题已忽略：0-否、1-是 */
    private Date eventTime;          /* 决定时间 */
    private Date addTime;            /* 新增时间 */
    private Date updateTime;         /* 更新时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getEvalId() {
        return evalId;
    }

    public void setEvalId(String evalId) {
        this.evalId = evalId;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public String getTriggerIds() {
        return triggerIds;
    }

    public void setTriggerIds(String triggerIds) {
        this.triggerIds = triggerIds;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStrength() {
        return strength;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getMandate() {
        return mandate;
    }

    public void setMandate(String mandate) {
        this.mandate = mandate;
    }

    public String getAudit() {
        return audit;
    }

    public void setAudit(String audit) {
        this.audit = audit;
    }

    public int getDismissed() {
        return dismissed;
    }

    public void setDismissed(int dismissed) {
        this.dismissed = dismissed;
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
