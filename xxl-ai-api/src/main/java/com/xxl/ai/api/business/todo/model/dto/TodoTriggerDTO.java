package com.xxl.ai.api.business.todo.model.dto;

import java.util.List;

/**
 * 超级助理 触发与去向
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoTriggerDTO {

    private long id;                   /* 触发ID */
    private String kind;               /* 类型：create、append、answer */
    private String content;            /* 提交内容 */
    private String evalId;             /* 进入的EvalID */
    private Long decisionId;           /* 产生的决定ID */
    private List<Long> fanout;         /* 送达的工单ID */
    private List<Long> acked;          /* 已确认接手的工单ID */
    private List<String> handbacks;    /* 退回记录：#工单 原因 */
    private boolean requeued;          /* 人工重新研判过 */
    private String eventTime;          /* 提交时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEvalId() {
        return evalId;
    }

    public void setEvalId(String evalId) {
        this.evalId = evalId;
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(Long decisionId) {
        this.decisionId = decisionId;
    }

    public List<Long> getFanout() {
        return fanout;
    }

    public void setFanout(List<Long> fanout) {
        this.fanout = fanout;
    }

    public List<Long> getAcked() {
        return acked;
    }

    public void setAcked(List<Long> acked) {
        this.acked = acked;
    }

    public List<String> getHandbacks() {
        return handbacks;
    }

    public void setHandbacks(List<String> handbacks) {
        this.handbacks = handbacks;
    }

    public boolean isRequeued() {
        return requeued;
    }

    public void setRequeued(boolean requeued) {
        this.requeued = requeued;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

}
