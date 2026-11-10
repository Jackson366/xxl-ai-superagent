package com.xxl.ai.api.business.todo.model.dto;

import java.util.List;

/**
 * 超级助理 待办详情（时间线）
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoThreadDetailDTO {

    private TodoThreadDTO thread;               /* 待办 */
    private List<TodoTriggerDTO> triggers;      /* 触发与去向 */
    private List<TodoDecisionDTO> decisions;    /* 研判决定 */
    private List<TodoTicketDTO> tickets;        /* 工单 */
    private List<TodoMessageDTO> messages;      /* 发给你的消息 */

    public TodoThreadDTO getThread() {
        return thread;
    }

    public void setThread(TodoThreadDTO thread) {
        this.thread = thread;
    }

    public List<TodoTriggerDTO> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<TodoTriggerDTO> triggers) {
        this.triggers = triggers;
    }

    public List<TodoDecisionDTO> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<TodoDecisionDTO> decisions) {
        this.decisions = decisions;
    }

    public List<TodoTicketDTO> getTickets() {
        return tickets;
    }

    public void setTickets(List<TodoTicketDTO> tickets) {
        this.tickets = tickets;
    }

    public List<TodoMessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<TodoMessageDTO> messages) {
        this.messages = messages;
    }

}
