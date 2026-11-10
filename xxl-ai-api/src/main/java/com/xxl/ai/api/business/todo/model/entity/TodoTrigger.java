package com.xxl.ai.api.business.todo.model.entity;

import java.util.Date;

/**
 * 超级助理触发 实体
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoTrigger {

    private long id;               /* 触发ID */
    private String triggerKey;     /* 幂等键（前端 requestId） */
    private long threadId;         /* 线程ID */
    private int userId;            /* 提交人用户ID */
    private String kind;           /* 类型：create-新建、append-追加、answer-回答 */
    private String attentionId;    /* 回答的待人工决定ID（kind=answer） */
    private String content;        /* 提交内容（外部数据，不作为指令） */
    private String disposition;    /* 去向(JSON)：evalId、decisionId、fanout、handbacks、acked、requeued */
    private Date eventTime;        /* 提交时间 */
    private Date addTime;          /* 新增时间 */
    private Date updateTime;       /* 更新时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTriggerKey() {
        return triggerKey;
    }

    public void setTriggerKey(String triggerKey) {
        this.triggerKey = triggerKey;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getAttentionId() {
        return attentionId;
    }

    public void setAttentionId(String attentionId) {
        this.attentionId = attentionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
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
