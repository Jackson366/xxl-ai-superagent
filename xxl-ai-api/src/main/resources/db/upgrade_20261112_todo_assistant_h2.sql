SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS xxl_ai_todo_assistant_turn (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    user_id INT NOT NULL COMMENT '归属用户ID',
    request_id VARCHAR(64) NOT NULL COMMENT '请求幂等键',
    content TEXT NOT NULL COMMENT '用户消息',
    state VARCHAR(16) NOT NULL COMMENT '消息状态',
    reply TEXT COMMENT '助理回复',
    reference_id BIGINT COMMENT '引用待办ID',
    reference_title VARCHAR(100) COMMENT '引用待办标题',
    revises_id BIGINT COMMENT '被修订的建议消息ID',
    target_ticket_id BIGINT COMMENT '确认后投递的目标工单ID',
    attention_id VARCHAR(64) COMMENT '确认后回答的提问ID',
    proposal TEXT COMMENT '待确认的执行范围',
    error_code VARCHAR(64) COMMENT '未完成原因',
    thread_id BIGINT COMMENT '确认后关联的待办ID',
    add_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT i_todo_assistant_user_request UNIQUE (user_id, request_id)
);

CREATE INDEX IF NOT EXISTS i_todo_assistant_user_id ON xxl_ai_todo_assistant_turn (user_id, id);
