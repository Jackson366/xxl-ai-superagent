package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理工单 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoTicket {

    private long id;                 /* 工单ID */
    private int userId;              /* 归属用户ID */
    private long threadId;           /* 发起线程ID */
    private long decisionId;         /* 接单决定ID */
    private long originTriggerId;    /* 发起触发ID */
    private String title;            /* 工单标题 */
    private String mandate;          /* 授权(JSON)，不可修改 */
    private String attention;        /* 待人工决定(JSON)：id、question、triggerId */
    private String undelivered;      /* 未投递给Worker的输入ID(JSON数组) */
    private String deliveryError;    /* 最近一次投递失败(JSON)：error、at */
    private String workerModel;      /* Worker模型快照(JSON)：spaceId、supplierId、modelId */
    private Date endedTime;          /* 结束时间，空为进行中 */
    private String endedBy;          /* 结束方：executor-执行器、human-人工 */
    private String endedOutcome;     /* 结束结果：completed-完成、dropped-无需处理、stopped-已停止 */
    private String endedReason;      /* 结束补充说明 */
    private Date addTime;            /* 新增时间 */
    private Date updateTime;         /* 更新时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public long getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(long decisionId) {
        this.decisionId = decisionId;
    }

    public long getOriginTriggerId() {
        return originTriggerId;
    }

    public void setOriginTriggerId(long originTriggerId) {
        this.originTriggerId = originTriggerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMandate() {
        return mandate;
    }

    public void setMandate(String mandate) {
        this.mandate = mandate;
    }

    public String getAttention() {
        return attention;
    }

    public void setAttention(String attention) {
        this.attention = attention;
    }

    public String getUndelivered() {
        return undelivered;
    }

    public void setUndelivered(String undelivered) {
        this.undelivered = undelivered;
    }

    public String getDeliveryError() {
        return deliveryError;
    }

    public void setDeliveryError(String deliveryError) {
        this.deliveryError = deliveryError;
    }

    public String getWorkerModel() {
        return workerModel;
    }

    public void setWorkerModel(String workerModel) {
        this.workerModel = workerModel;
    }

    public Date getEndedTime() {
        return endedTime;
    }

    public void setEndedTime(Date endedTime) {
        this.endedTime = endedTime;
    }

    public String getEndedBy() {
        return endedBy;
    }

    public void setEndedBy(String endedBy) {
        this.endedBy = endedBy;
    }

    public String getEndedOutcome() {
        return endedOutcome;
    }

    public void setEndedOutcome(String endedOutcome) {
        this.endedOutcome = endedOutcome;
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
