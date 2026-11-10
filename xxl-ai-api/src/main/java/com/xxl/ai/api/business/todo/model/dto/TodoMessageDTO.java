package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理 收件箱消息
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoMessageDTO {

    private long id;               /* 消息ID */
    private Long ticketId;         /* 工单ID */
    private Long threadId;         /* 待办ID */
    private String ticketTitle;    /* 工单标题 */
    private String purpose;        /* 用途：acknowledgement、answer、attention、result、notice */
    private String content;        /* 消息内容(Markdown) */
    private int readStatus;        /* 已读：0-未读、1-已读 */
    private String addTime;        /* 新增时间 */

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public String getTicketTitle() {
        return ticketTitle;
    }

    public void setTicketTitle(String ticketTitle) {
        this.ticketTitle = ticketTitle;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(int readStatus) {
        this.readStatus = readStatus;
    }

    public String getAddTime() {
        return addTime;
    }

    public void setAddTime(String addTime) {
        this.addTime = addTime;
    }

}
