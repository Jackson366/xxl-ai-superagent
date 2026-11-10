package com.xxl.ai.api.business.todo.model.dto;

import java.util.List;

/**
 * 超级助理 概览（要我吗、健康吗）
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoOverviewDTO {

    private List<TodoThreadDTO> needMe;    /* 需要你处理的待办 */
    private List<String> problems;         /* 系统问题 */
    private String readyCode;              /* 未就绪原因编码，就绪为空 */
    private boolean paused;                /* 已暂停处理 */
    private int busySlots;                 /* 占用的执行槽位 */
    private int maxWorkers;                /* 执行槽位上限 */
    private int unread;                    /* 未读消息数 */

    public List<TodoThreadDTO> getNeedMe() {
        return needMe;
    }

    public void setNeedMe(List<TodoThreadDTO> needMe) {
        this.needMe = needMe;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }

    public String getReadyCode() {
        return readyCode;
    }

    public void setReadyCode(String readyCode) {
        this.readyCode = readyCode;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public int getBusySlots() {
        return busySlots;
    }

    public void setBusySlots(int busySlots) {
        this.busySlots = busySlots;
    }

    public int getMaxWorkers() {
        return maxWorkers;
    }

    public void setMaxWorkers(int maxWorkers) {
        this.maxWorkers = maxWorkers;
    }

    public int getUnread() {
        return unread;
    }

    public void setUnread(int unread) {
        this.unread = unread;
    }

}
