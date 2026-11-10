package com.xxl.ai.api.business.todo.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/**
 * 超级助理运行观测数据
 *
 * @author Codex 2026-11-11
 */
public class TodoEvent {
    private long id; /* 事件游标 */
    private long threadId; /* 待办ID */
    private String evalId; /* 研判ID */
    private Long ticketId; /* 工单ID */
    private Long assignmentId; /* 派工ID */
    private String roundId; /* 执行轮次ID */
    private String callId; /* 工具调用ID */
    private String kind; /* 事件类型 */
    private String status; /* 状态编码 */
    private String summary; /* 简要信息 */
    private String detail; /* 脱敏详情 */
    private Long durationMs; /* 耗时毫秒 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
    private Date eventTime; /* 事件时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public String getEvalId() {
        return evalId;
    }

    public void setEvalId(String evalId) {
        this.evalId = evalId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Date getEventTime() {
        return eventTime;
    }

    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
    }

}
