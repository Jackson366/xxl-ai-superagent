package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理线程 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoThread {

    private long id;                 /* 线程ID */
    private int userId;              /* 归属用户ID */
    private String title;            /* 标题（取首条提交内容） */
    private String pending;          /* 待研判触发ID(JSON数组)，为空时存 NULL */
    private String evaluating;       /* 进行中的Eval(JSON)，无则为 NULL */
    private int expired;             /* 连续超时重排次数，产生Decision后清零 */
    private String subscriptions;    /* 订阅的工单ID(JSON数组) */
    private Date lastTime;           /* 最新触发时间 */
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPending() {
        return pending;
    }

    public void setPending(String pending) {
        this.pending = pending;
    }

    public String getEvaluating() {
        return evaluating;
    }

    public void setEvaluating(String evaluating) {
        this.evaluating = evaluating;
    }

    public int getExpired() {
        return expired;
    }

    public void setExpired(int expired) {
        this.expired = expired;
    }

    public String getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(String subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Date getLastTime() {
        return lastTime;
    }

    public void setLastTime(Date lastTime) {
        this.lastTime = lastTime;
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
