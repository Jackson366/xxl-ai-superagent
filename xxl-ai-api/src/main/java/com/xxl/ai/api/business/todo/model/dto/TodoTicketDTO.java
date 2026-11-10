package com.xxl.ai.api.business.todo.model.dto;

import java.util.List;

/**
 * 超级助理 工单
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoTicketDTO {

    private long id;                                /* 工单ID */
    private String title;                           /* 标题 */
    private String stage;                           /* 阶段：见 TodoConstant.STAGE_* */
    private String scope;                           /* 授权范围 */
    private String strength;                        /* 授权强度 */
    private String policyVersion;                   /* 接单时的策略版本 */
    private String question;                        /* 待你回答的问题 */
    private String attentionId;                     /* 提问ID */
    private String endedBy;                         /* 结束方 */
    private String endedOutcome;                    /* 结束结果 */
    private String endedReason;                     /* 结束补充说明 */
    private String endedTime;                       /* 结束时间 */
    private boolean canRetry;                       /* 可重试 */
    private String addTime;                         /* 新增时间 */
    private List<TodoAssignmentDTO> assignments;    /* 派工记录 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStrength() {
        return strength;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAttentionId() {
        return attentionId;
    }

    public void setAttentionId(String attentionId) {
        this.attentionId = attentionId;
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

    public String getEndedTime() {
        return endedTime;
    }

    public void setEndedTime(String endedTime) {
        this.endedTime = endedTime;
    }

    public boolean isCanRetry() {
        return canRetry;
    }

    public void setCanRetry(boolean canRetry) {
        this.canRetry = canRetry;
    }

    public String getAddTime() {
        return addTime;
    }

    public void setAddTime(String addTime) {
        this.addTime = addTime;
    }

    public List<TodoAssignmentDTO> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<TodoAssignmentDTO> assignments) {
        this.assignments = assignments;
    }

}
