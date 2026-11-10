package com.xxl.ai.api.business.todo.service;

import java.util.List;

/**
 * 超级助理 副作用调度器：事务提交后执行副作用（研判、启动、投递、发消息），含退避重试与槽位控制
 *
 * @author xxl-ai 2026-11-10
 */
public interface TodoEffectService {

    /**
     * 健康概览：连续失败达到阈值的副作用描述
     */
    List<String> problems();

    /**
     * 占用的执行槽位（进行中的一轮 Worker 执行）
     */
    int busySlots();

}
