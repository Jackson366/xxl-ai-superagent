package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理派工 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoAssignment {

    private long id;                 /* 派工ID */
    private long ticketId;           /* 工单ID */
    private String state;            /* 状态：pending-待启动、running-执行中、ended-已结束 */
    private String reportState;      /* 最新报告：progress、waiting、ready、blocked、failed、dropped */
    private String reportSummary;    /* 最新报告摘要 */
    private Date reportTime;         /* 最新报告时间 */
    private String tools;            /* 工作工具快照(JSON)：mcpIds、skillIds */
    private Date deliveredTime;      /* 最近一次输入送达时间 */
    private Date idleTime;           /* 最近一次无后台任务的轮次结束时间 */
    private Date startedTime;        /* 开始时间 */
    private Date endedTime;          /* 结束时间 */
    private String endedReason;      /* 结束原因编码，如 hostRestarted、retried */
    private Date addTime;            /* 新增时间 */
    private Date updateTime;         /* 更新时间 */

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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getReportState() {
        return reportState;
    }

    public void setReportState(String reportState) {
        this.reportState = reportState;
    }

    public String getReportSummary() {
        return reportSummary;
    }

    public void setReportSummary(String reportSummary) {
        this.reportSummary = reportSummary;
    }

    public Date getReportTime() {
        return reportTime;
    }

    public void setReportTime(Date reportTime) {
        this.reportTime = reportTime;
    }

    public String getTools() {
        return tools;
    }

    public void setTools(String tools) {
        this.tools = tools;
    }

    public Date getDeliveredTime() {
        return deliveredTime;
    }

    public void setDeliveredTime(Date deliveredTime) {
        this.deliveredTime = deliveredTime;
    }

    public Date getIdleTime() {
        return idleTime;
    }

    public void setIdleTime(Date idleTime) {
        this.idleTime = idleTime;
    }

    public Date getStartedTime() {
        return startedTime;
    }

    public void setStartedTime(Date startedTime) {
        this.startedTime = startedTime;
    }

    public Date getEndedTime() {
        return endedTime;
    }

    public void setEndedTime(Date endedTime) {
        this.endedTime = endedTime;
    }

    public String getEndedReason() {
        return endedReason;
    }

    public void setEndedReason(String endedReason) {
        this.endedReason = endedReason;
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
