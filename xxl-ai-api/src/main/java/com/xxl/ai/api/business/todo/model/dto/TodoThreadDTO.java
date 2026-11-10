package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理 待办列表行
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoThreadDTO {

    private long id;               /* 待办ID */
    private String title;          /* 标题 */
    private String stage;          /* 阶段：见 TodoConstant.STAGE_* */
    private Long ticketId;         /* 当前工单ID */
    private String question;       /* 待你回答的问题 */
    private String attentionId;    /* 提问ID */
    private String summary;        /* 最新报告摘要 */
    private String reasonCode;     /* 无工单时的最新决定原因编码 */
    private String reasonText;     /* 原因补充文本 */
    private String lastTime;       /* 最新提交时间 */
    private String addTime;        /* 新增时间 */

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

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

    public String getLastTime() {
        return lastTime;
    }

    public void setLastTime(String lastTime) {
        this.lastTime = lastTime;
    }

    public String getAddTime() {
        return addTime;
    }

    public void setAddTime(String addTime) {
        this.addTime = addTime;
    }

}
