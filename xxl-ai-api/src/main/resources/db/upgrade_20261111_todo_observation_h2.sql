SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS xxl_ai_todo_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件ID及续传游标',
    thread_id BIGINT NOT NULL COMMENT '待办ID',
    eval_id VARCHAR(80) DEFAULT NULL COMMENT '研判ID',
    ticket_id BIGINT DEFAULT NULL COMMENT '工单ID',
    assignment_id BIGINT DEFAULT NULL COMMENT '派工ID',
    round_id VARCHAR(64) DEFAULT NULL COMMENT '执行轮次ID',
    call_id VARCHAR(64) DEFAULT NULL COMMENT '工具调用ID',
    kind VARCHAR(64) NOT NULL COMMENT '事件类型',
    status VARCHAR(32) NOT NULL COMMENT '状态编码',
    summary VARCHAR(1000) DEFAULT NULL COMMENT '脱敏摘要',
    detail TEXT DEFAULT NULL COMMENT '脱敏详情',
    duration_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    event_time DATETIME(3) NOT NULL COMMENT '事件时间',
    add_time DATETIME NOT NULL COMMENT '新增时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY i_todo_event_thread (thread_id, id),
    KEY i_todo_event_assignment (assignment_id, id)
);
