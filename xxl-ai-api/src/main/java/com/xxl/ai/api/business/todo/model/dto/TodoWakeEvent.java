package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理调度唤醒事件：引擎写入副作用后发布，事务提交后由调度器处理（解耦引擎与调度器，避免循环依赖）
 *
 * @param stopTicketId 人工停止的工单ID，调度器据此中断正在执行的轮次；为空表示普通唤醒
 * @author xxl-ai 2026-11-10
 */
public record TodoWakeEvent(Long stopTicketId) {
}
