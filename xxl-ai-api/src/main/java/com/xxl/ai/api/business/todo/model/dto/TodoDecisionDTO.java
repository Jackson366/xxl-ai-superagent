package com.xxl.ai.api.business.todo.model.dto;

import java.util.List;

/**
 * 超级助理 研判决定
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoDecisionDTO {

    private String routerDecision;  /* Router 建议，确定性决定时为空 */
    private String routerScope;     /* Router 建议范围 */
    private long id;                  /* 决定ID */
    private String evalId;            /* EvalID */
    private String kind;              /* 决定：accept、stop、ignore、unavailable */
    private String strength;          /* 强度：explicit、inferred */
    private List<Long> triggerIds;    /* 本批触发ID */
    private String reasonCode;        /* 原因编码 */
    private String reasonText;        /* 原因补充文本 */
    private Long ticketId;            /* 接单的工单ID */
    private String scope;             /* 授权范围 */
    private boolean wouldAccept;      /* 观察模式：会接单但未执行 */
    private int dismissed;            /* 未完成问题已忽略：0-否、1-是 */
    private String policyVersion;     /* 策略版本 */
    private String eventTime;         /* 决定时间 */

    public String getRouterDecision() {
        return routerDecision;
    }

    public void setRouterDecision(String routerDecision) {
        this.routerDecision = routerDecision;
    }

    public String getRouterScope() {
        return routerScope;
    }

    public void setRouterScope(String routerScope) {
        this.routerScope = routerScope;
    }

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

    public List<Long> getTriggerIds() {
        return triggerIds;
    }

    public void setTriggerIds(List<Long> triggerIds) {
        this.triggerIds = triggerIds;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isWouldAccept() {
        return wouldAccept;
    }

    public void setWouldAccept(boolean wouldAccept) {
        this.wouldAccept = wouldAccept;
    }

    public int getDismissed() {
        return dismissed;
    }

    public void setDismissed(int dismissed) {
        this.dismissed = dismissed;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

}
