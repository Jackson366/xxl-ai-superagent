package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理 派工
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoAssignmentDTO {

    private long id;                 /* 派工ID */
    private String state;            /* 状态：pending、running、ended */
    private String reportState;      /* 最新报告 */
    private String reportSummary;    /* 最新报告摘要 */
    private String reportTime;       /* 最新报告时间 */
    private String startedTime;      /* 开始时间 */
    private String endedTime;        /* 结束时间 */
    private String endedReason;      /* 结束原因编码 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getReportTime() {
        return reportTime;
    }

    public void setReportTime(String reportTime) {
        this.reportTime = reportTime;
    }

    public String getStartedTime() {
        return startedTime;
    }

    public void setStartedTime(String startedTime) {
        this.startedTime = startedTime;
    }

    public String getEndedTime() {
        return endedTime;
    }

    public void setEndedTime(String endedTime) {
        this.endedTime = endedTime;
    }

    public String getEndedReason() {
        return endedReason;
    }

    public void setEndedReason(String endedReason) {
        this.endedReason = endedReason;
    }

}
