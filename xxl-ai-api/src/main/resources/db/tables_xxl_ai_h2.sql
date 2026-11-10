--
-- XXL-AI 本地调试建表脚本（H2，MySQL 兼容模式；local profile 首次启动自动执行）
--
-- 由 doc/db/tables_xxl_ai.sql 转换而来，差异：
--   1、去掉 CREATE DATABASE / USE / START TRANSACTION / COMMIT（H2 不支持，库由 JDBC URL 指定）；
--   2、索引名追加表名（H2 索引名需全库唯一），如 i_space_id -> i_mcp_space_id；
--   3、含换行转义的字符串改用 STRINGDECODE()（H2 不解析反斜杠转义）。
-- 修改 doc/db/tables_xxl_ai.sql 时须同步本文件。

SET NAMES utf8mb4;

-- ==================== AI 业务表 ====================

-- 1、业务空间表
CREATE TABLE IF NOT EXISTS `xxl_ai_space` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `name`          VARCHAR(50)     NOT NULL                COMMENT '空间名称',
    `code`          VARCHAR(50)     NOT NULL                COMMENT '空间编码',
    `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态：0-正常、1-停用',
    `superagent_status` TINYINT     NOT NULL DEFAULT 0      COMMENT '超级助理使用：0-否、1-是',
    `remark`        VARCHAR(255)    NULL DEFAULT NULL       COMMENT '备注',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_space_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务空间表';

-- 2、用户-空间关联表
CREATE TABLE IF NOT EXISTS `xxl_ai_user_space` (
    `id`            BIGINT          NOT NULL    AUTO_INCREMENT,
    `user_id`       INT             NOT NULL    COMMENT '用户ID',
    `space_id`      BIGINT          NOT NULL    COMMENT '空间ID',
    `add_time`      DATETIME        NOT NULL    COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL    COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_user_space_user_space` (`user_id`, `space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-空间关联表';

-- 3、供应商表
CREATE TABLE IF NOT EXISTS `xxl_ai_supplier` (
    `id`            BIGINT          NOT NULL            AUTO_INCREMENT,
    `space_id`      BIGINT          NOT NULL            COMMENT '空间ID',
    `name`          VARCHAR(50)     NOT NULL            COMMENT '供应商名称',
    `base_url`      VARCHAR(200)    NOT NULL            COMMENT '接口地址',
    `api_key`       VARCHAR(200)    NULL DEFAULT NULL   COMMENT 'API密钥',
    `headers`       VARCHAR(2000)   NULL DEFAULT NULL   COMMENT '请求Header',
    `status`        TINYINT         NOT NULL DEFAULT 0  COMMENT '状态：0-正常、1-停用',
    `remark`        VARCHAR(255)    NULL DEFAULT NULL   COMMENT '备注',
    `add_time`      DATETIME        NOT NULL            COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_supplier_space_id` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- 4、供应商模型表
CREATE TABLE IF NOT EXISTS `xxl_ai_supplier_model` (
    `id`            BIGINT          NOT NULL            AUTO_INCREMENT,
    `supplier_id`   BIGINT          NOT NULL            COMMENT '供应商ID',
    `name`          VARCHAR(50)     NOT NULL            COMMENT '模型展示名称',
    `model`         VARCHAR(100)    NOT NULL            COMMENT '模型标识',
    `type`          TINYINT         NOT NULL DEFAULT 0  COMMENT '类型：0-对话、1-嵌入',
    `status`        TINYINT         NOT NULL DEFAULT 0  COMMENT '状态：0-正常、1-停用',
    `add_time`      DATETIME        NOT NULL            COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_supplier_model_supplier_id` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商模型表';

-- 5、MCP 服务表
CREATE TABLE IF NOT EXISTS `xxl_ai_mcp` (
    `id`            BIGINT          NOT NULL            AUTO_INCREMENT,
    `space_id`      BIGINT          NOT NULL            COMMENT '空间ID',
    `name`          VARCHAR(100)    NOT NULL            COMMENT 'MCP名称',
    `type`          TINYINT         NOT NULL DEFAULT 0  COMMENT '服务类型：0-远程(Streamable HTTP)、1-本地(stdio)',
    `url`           VARCHAR(200)    NULL DEFAULT NULL   COMMENT '服务地址(远程必填，本地可为空)',
    `headers`       VARCHAR(500)    NULL DEFAULT NULL   COMMENT '请求头(JSON)',
    `config`        TEXT            NULL DEFAULT NULL   COMMENT '完整MCP配置(JSON)：远程{transport,url,headers} 本地{transport,command,args,env,cwd}',
    `status`        TINYINT         NOT NULL DEFAULT 0  COMMENT '状态：0-正常、1-停用',
    `remark`        VARCHAR(500)    NULL DEFAULT NULL   COMMENT '备注',
    `add_time`      DATETIME        NOT NULL            COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_mcp_space_id` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP服务表';

-- 6、SKILL 表（重设计：移除 content/source/source_url；名称空间内唯一）
CREATE TABLE IF NOT EXISTS `xxl_ai_skill` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `space_id`      BIGINT          NOT NULL                COMMENT '空间ID',
    `name`          VARCHAR(100)    NOT NULL                COMMENT 'SKILL名称(目录名，空间内唯一)',
    `description`   VARCHAR(500)    NULL DEFAULT NULL       COMMENT 'SKILL描述',
    `version`       VARCHAR(20)     NOT NULL DEFAULT '1.0'  COMMENT '版本',
    `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态：0-正常、1-停用',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_skill_space_id_name` (`space_id`, `name`),
    KEY `i_skill_space_id` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKILL表';

-- 6-1、SKILL 内容文件表（文件树，parent_id 父子层级；locked 固定文件仅 SKILL.md 与约定目录）
CREATE TABLE IF NOT EXISTS `xxl_ai_skill_file` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `skill_id`      BIGINT          NOT NULL                COMMENT 'SKILL ID',
    `parent_id`     BIGINT          NOT NULL DEFAULT 0      COMMENT '父目录ID(0为根级)',
    `name`          VARCHAR(200)    NOT NULL                COMMENT '文件/目录名称',
    `type`          TINYINT         NOT NULL DEFAULT 1      COMMENT '类型：0-目录、1-文件',
    `file_type`     VARCHAR(20)     NULL DEFAULT NULL       COMMENT '文件类型(扩展名，目录为空)',
    `content`       MEDIUMTEXT      NULL DEFAULT NULL       COMMENT '文件内容(目录为空)',
    `locked`        TINYINT         NOT NULL DEFAULT 0      COMMENT '是否固定：0-否、1-是(不可删除/改名/移动)',
    `sort`          INT             NOT NULL DEFAULT 0      COMMENT '排序',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_skill_file_skill_parent_name` (`skill_id`, `parent_id`, `name`),
    KEY `i_skill_file_skill_id` (`skill_id`),
    KEY `i_skill_file_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKILL内容文件表';

-- 7、知识库表
CREATE TABLE IF NOT EXISTS `xxl_ai_knowledge_base` (
    `id`                  BIGINT          NOT NULL                AUTO_INCREMENT,
    `space_id`            BIGINT          NOT NULL                COMMENT '空间ID',
    `name`                VARCHAR(100)    NOT NULL                COMMENT '知识库名称',
    `description`         VARCHAR(500)    NULL DEFAULT NULL       COMMENT '描述',
    `embed_supplier_id`   BIGINT          NOT NULL DEFAULT 0      COMMENT '向量化供应商ID',
    `embed_model_id`      BIGINT          NOT NULL DEFAULT 0      COMMENT '向量化模型ID',
    `chunk_size`          INT             NOT NULL DEFAULT 500    COMMENT '分片大小',
    `chunk_overlap`       INT             NOT NULL DEFAULT 50     COMMENT '分片重叠',
    `top_k`               INT             NOT NULL DEFAULT 5      COMMENT '检索数量',
    `status`              TINYINT         NOT NULL DEFAULT 0      COMMENT '状态：0-正常、1-停用',
    `add_time`            DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`         DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_knowledge_base_space_id` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 8、知识文档表
CREATE TABLE IF NOT EXISTS `xxl_ai_knowledge_doc` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `space_id`      BIGINT          NOT NULL                COMMENT '空间ID',
    `base_id`       BIGINT          NOT NULL                COMMENT '知识库ID',
    `name`          VARCHAR(200)    NOT NULL                COMMENT '文档名称',
    `content`       MEDIUMTEXT      NULL DEFAULT NULL       COMMENT '文档内容',
    `chunk_count`   INT             NOT NULL DEFAULT 0      COMMENT '分片数量',
    `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态：0-未处理、1-已向量化、2-失败',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_knowledge_doc_base_id` (`base_id`),
    KEY `i_knowledge_doc_space_id` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档表';

-- 9、Agent 表
CREATE TABLE IF NOT EXISTS `xxl_ai_agent` (
    `id`                  BIGINT          NOT NULL              AUTO_INCREMENT,
    `space_id`            BIGINT          NOT NULL              COMMENT '空间ID',
    `name`                VARCHAR(100)    NOT NULL              COMMENT 'Agent名称',
    `intro`               VARCHAR(500)    NULL DEFAULT NULL     COMMENT 'Agent介绍',
    `model_supplier_id`   BIGINT          NOT NULL DEFAULT 0    COMMENT '模型供应商ID',
    `model_id`            BIGINT          NOT NULL DEFAULT 0    COMMENT '模型ID',
    `system_prompt`       TEXT            NULL DEFAULT NULL     COMMENT '系统指令',
    `kb_ids`              VARCHAR(500)    NULL DEFAULT NULL     COMMENT '知识库ID集合(逗号分隔)',
    `mcp_ids`             VARCHAR(500)    NULL DEFAULT NULL     COMMENT 'MCP ID集合(逗号分隔)',
    `skill_ids`           VARCHAR(500)    NULL DEFAULT NULL     COMMENT 'Skill ID集合(逗号分隔)',
    `memory_status`       TINYINT         NOT NULL DEFAULT 0    COMMENT '记忆开关：0-关闭、1-开启',
    `publish_status`      TINYINT         NOT NULL DEFAULT 0    COMMENT '发布状态：0-未发布、1-已发布',
    `uuid`                VARCHAR(32)     NULL DEFAULT NULL     COMMENT '访问UUID',
    `add_time`            DATETIME        NOT NULL              COMMENT '新增时间',
    `update_time`         DATETIME        NOT NULL              COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_agent_space_id` (`space_id`),
    UNIQUE KEY `i_agent_uuid` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent表';

-- 10、Agent 对话表
CREATE TABLE IF NOT EXISTS `xxl_ai_session` (
    `id`            BIGINT          NOT NULL            AUTO_INCREMENT,
    `agent_id`      BIGINT          NOT NULL            COMMENT 'AgentID',
    `visitor_id`    VARCHAR(64)     NOT NULL            COMMENT '访客标识',
    `title`         VARCHAR(100)    NOT NULL DEFAULT '新对话'  COMMENT '对话标题',
    `add_time`      DATETIME        NOT NULL            COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_session_agent_visitor` (`agent_id`, `visitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent对话表';

-- 11、Agent 消息表
CREATE TABLE IF NOT EXISTS `xxl_ai_session_msg` (
    `id`            BIGINT          NOT NULL            AUTO_INCREMENT,
    `session_id`    BIGINT          NOT NULL            COMMENT '对话ID',
    `role`          VARCHAR(10)     NOT NULL            COMMENT '角色：user/assistant',
    `reasoning`     TEXT            NULL DEFAULT NULL   COMMENT '思考过程（推理模型 reasoning_content）',
    `content`       TEXT            NOT NULL            COMMENT '消息内容',
    `status`        TINYINT         NOT NULL DEFAULT 1  COMMENT '状态：0-生成中、1-完成、2-失败（助手消息ID复用为结果流标识）',
    `add_time`      DATETIME        NOT NULL            COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_session_msg_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息表';

-- 12、Agent 长期记忆表（Agent × 访客 维度，单字段 Markdown 维护记忆内容）
CREATE TABLE IF NOT EXISTS `xxl_ai_memory` (
    `id`            BIGINT          NOT NULL            AUTO_INCREMENT  COMMENT '记忆ID',
    `agent_id`      BIGINT          NOT NULL                            COMMENT 'AgentID',
    `visitor_id`    VARCHAR(64)     NOT NULL                            COMMENT '访客标识',
    `content`       TEXT            NULL DEFAULT NULL                   COMMENT '记忆内容(Markdown，按维度分节)',
    `last_msg_id`   BIGINT          NOT NULL DEFAULT 0                  COMMENT '最后归档处理的消息ID（增量水位线）',
    `add_time`      DATETIME        NOT NULL                            COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_memory_agent_visitor` (`agent_id`, `visitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent长期记忆表';

-- ==================== 超级助理表（与 xxl-ai-spec/20261110-todo/todo-table.sql 保持一致） ====================

-- 1、触发表：一次表单提交即一条 Trigger，除 disposition 外不可修改
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_trigger` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `trigger_key`   VARCHAR(64)     NOT NULL                COMMENT '幂等键（前端 requestId）',
    `thread_id`     BIGINT          NOT NULL                COMMENT '线程ID',
    `user_id`       INT             NOT NULL                COMMENT '提交人用户ID',
    `kind`          VARCHAR(20)     NOT NULL                COMMENT '类型：create-新建、append-追加、answer-回答',
    `attention_id`  VARCHAR(64)     NULL DEFAULT NULL       COMMENT '回答的待人工决定ID（kind=answer）',
    `content`       TEXT            NOT NULL                COMMENT '提交内容（外部数据，不作为指令）',
    `disposition`   TEXT            NULL DEFAULT NULL       COMMENT '去向(JSON)：evalId、decisionId、fanout、handbacks、acked、requeued',
    `event_time`    DATETIME(3)     NOT NULL                COMMENT '提交时间',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_todo_trigger_trigger_key` (`trigger_key`),
    KEY `i_todo_trigger_thread_id` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理触发表';

-- 2、线程表：一条 todo 一个 Thread，同一时刻至多一个 Eval
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_thread` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `user_id`       INT             NOT NULL                COMMENT '归属用户ID',
    `title`         VARCHAR(200)    NOT NULL                COMMENT '标题（取首条提交内容）',
    `pending`       TEXT            NULL DEFAULT NULL       COMMENT '待研判触发ID(JSON数组)',
    `evaluating`    TEXT            NULL DEFAULT NULL       COMMENT '进行中的Eval(JSON)：id、triggerIds、strength、policyVersion、deadline',
    `expired`       INT             NOT NULL DEFAULT 0      COMMENT '连续超时重排次数，产生Decision后清零',
    `subscriptions` TEXT            NULL DEFAULT NULL       COMMENT '订阅的工单ID(JSON数组)',
    `last_time`     DATETIME(3)     NULL DEFAULT NULL       COMMENT '最新触发时间',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_todo_thread_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理线程表';

-- 3、决定表：每次 Eval 的结果，一个 Eval 只产生一条
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_decision` (
    `id`             BIGINT          NOT NULL               AUTO_INCREMENT,
    `eval_id`        VARCHAR(64)     NOT NULL               COMMENT 'EvalID',
    `thread_id`      BIGINT          NOT NULL               COMMENT '线程ID',
    `trigger_ids`    TEXT            NOT NULL               COMMENT '本批触发ID(JSON数组)',
    `policy_version` VARCHAR(64)     NOT NULL               COMMENT '策略版本',
    `kind`           VARCHAR(20)     NOT NULL               COMMENT '决定：accept-接单、stop-停止、ignore-忽略、unavailable-未完成',
    `strength`       VARCHAR(20)     NOT NULL               COMMENT '强度：explicit-明确、inferred-推断',
    `reason`         TEXT            NOT NULL               COMMENT '原因(JSON数组)：code + values',
    `ticket_id`      BIGINT          NULL DEFAULT NULL      COMMENT '接单或停止的工单ID',
    `mandate`        TEXT            NULL DEFAULT NULL      COMMENT '授权(JSON)：strength、policyVersion、basis、actor、scope',
    `audit`          TEXT            NULL DEFAULT NULL      COMMENT '研判审计(JSON)：规则判定、direction、would_accept',
    `dismissed`      TINYINT         NOT NULL DEFAULT 0     COMMENT '未完成问题已忽略：0-否、1-是',
    `event_time`     DATETIME(3)     NOT NULL               COMMENT '决定时间',
    `add_time`       DATETIME        NOT NULL               COMMENT '新增时间',
    `update_time`    DATETIME        NOT NULL               COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_todo_decision_eval_id` (`eval_id`),
    KEY `i_todo_decision_thread_id` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理决定表';

-- 4、工单表：只有 Eval 能创建；同一触发重复接单只得到一个工单
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_ticket` (
    `id`                BIGINT          NOT NULL            AUTO_INCREMENT,
    `user_id`           INT             NOT NULL            COMMENT '归属用户ID',
    `thread_id`         BIGINT          NOT NULL            COMMENT '发起线程ID',
    `decision_id`       BIGINT          NOT NULL            COMMENT '接单决定ID',
    `origin_trigger_id` BIGINT          NOT NULL            COMMENT '发起触发ID',
    `title`             VARCHAR(200)    NOT NULL            COMMENT '工单标题',
    `mandate`           TEXT            NOT NULL            COMMENT '授权(JSON)，不可修改',
    `attention`         TEXT            NULL DEFAULT NULL   COMMENT '待人工决定(JSON)：id、question、triggerId',
    `undelivered`       TEXT            NULL DEFAULT NULL   COMMENT '未投递给Worker的输入ID(JSON数组)',
    `delivery_error`    TEXT            NULL DEFAULT NULL   COMMENT '最近一次投递失败(JSON)：error、at',
    `worker_model`      VARCHAR(500)    NULL DEFAULT NULL   COMMENT 'Worker模型快照(JSON)：spaceId、supplierId、modelId，首次建会话时写入',
    `ended_time`        DATETIME(3)     NULL DEFAULT NULL   COMMENT '结束时间，空为进行中',
    `ended_by`          VARCHAR(20)     NULL DEFAULT NULL   COMMENT '结束方：executor-执行器、human-人工',
    `ended_outcome`     VARCHAR(20)     NULL DEFAULT NULL   COMMENT '结束结果：completed-完成、dropped-无需处理、stopped-已停止',
    `ended_reason`      VARCHAR(500)    NULL DEFAULT NULL   COMMENT '结束补充说明',
    `add_time`          DATETIME        NOT NULL            COMMENT '新增时间',
    `update_time`       DATETIME        NOT NULL            COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_todo_ticket_origin_trigger_id` (`origin_trigger_id`),
    KEY `i_todo_ticket_user_id` (`user_id`),
    KEY `i_todo_ticket_thread_id` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理工单表';

-- 5、派工表：工单与一次 Worker 执行的绑定，生命周期独立于工单
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_assignment` (
    `id`             BIGINT          NOT NULL               AUTO_INCREMENT,
    `ticket_id`      BIGINT          NOT NULL               COMMENT '工单ID',
    `state`          VARCHAR(20)     NOT NULL               COMMENT '状态：pending-待启动、running-执行中、ended-已结束',
    `report_state`   VARCHAR(20)     NULL DEFAULT NULL      COMMENT '最新报告：progress、waiting、ready、blocked、failed、dropped',
    `report_summary` TEXT            NULL DEFAULT NULL      COMMENT '最新报告摘要',
    `report_time`    DATETIME(3)     NULL DEFAULT NULL      COMMENT '最新报告时间',
    `tools`          TEXT            NULL DEFAULT NULL      COMMENT '工作工具快照(JSON)：mcpIds、skillIds，启动时写入',
    `delivered_time` DATETIME(3)     NULL DEFAULT NULL      COMMENT '最近一次输入送达时间',
    `idle_time`      DATETIME(3)     NULL DEFAULT NULL      COMMENT '最近一次无后台任务的轮次结束时间',
    `started_time`   DATETIME(3)     NULL DEFAULT NULL      COMMENT '开始时间',
    `ended_time`     DATETIME(3)     NULL DEFAULT NULL      COMMENT '结束时间',
    `ended_reason`   VARCHAR(100)    NULL DEFAULT NULL      COMMENT '结束原因编码，如 hostRestarted、retried',
    `add_time`       DATETIME        NOT NULL               COMMENT '新增时间',
    `update_time`    DATETIME        NOT NULL               COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_todo_assignment_ticket_id` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理派工表';

-- 6、工单输入表：送达工单的上下文，含已读状态
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_input` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `ticket_id`     BIGINT          NOT NULL                COMMENT '工单ID',
    `kind`          VARCHAR(20)     NOT NULL                COMMENT '类型：trigger-触发、policy-changed-策略变更、resumed-恢复、text-文本、note-备注',
    `trigger_id`    BIGINT          NULL DEFAULT NULL       COMMENT '触发ID（kind=trigger/resumed）',
    `human`         TINYINT         NOT NULL DEFAULT 0      COMMENT '人工提交：0-否、1-是',
    `content`       TEXT            NULL DEFAULT NULL       COMMENT '内容(JSON)：策略变更影响、文本等',
    `delivered_to`  BIGINT          NULL DEFAULT NULL       COMMENT '最近送达的派工ID',
    `read_time`     DATETIME(3)     NULL DEFAULT NULL       COMMENT 'Worker读取时间，空为未读',
    `event_time`    DATETIME(3)     NOT NULL                COMMENT '产生时间',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_todo_input_ticket_id` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理工单输入表';

-- 7、Worker 会话消息表：一工单一会话，跨派工保留历史
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_worker_msg` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `ticket_id`     BIGINT          NOT NULL                COMMENT '工单ID',
    `assignment_id` BIGINT          NOT NULL                COMMENT '派工ID',
    `role`          VARCHAR(20)     NOT NULL                COMMENT '角色：user、assistant、tool',
    `input_ids`     VARCHAR(500)    NULL DEFAULT NULL       COMMENT '本条承载的输入ID(JSON数组)，用于标记已读',
    `content`       MEDIUMTEXT      NOT NULL                COMMENT '消息内容（文本）',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `i_todo_worker_msg_ticket_id` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理Worker会话消息表';

-- 8、副作用队列表：命令事务内入队，提交后执行；自增 id 即队列位置，合并时按 kind + effect_key 原地更新，不改 id，保留原位置
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_effect` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `kind`          VARCHAR(20)     NOT NULL                COMMENT '类型：assessment-研判、launch-启动派工、delivery-投递输入、reply-发送消息',
    `effect_key`    VARCHAR(100)    NOT NULL                COMMENT '对象键，同类型同对象合并为一条',
    `payload`       TEXT            NULL DEFAULT NULL       COMMENT '参数(JSON)',
    `generation`    INT             NOT NULL DEFAULT 0      COMMENT '参数版本，执行中的旧版本不删除新参数',
    `attempts`      INT             NOT NULL DEFAULT 0      COMMENT '连续失败次数',
    `due_time`      DATETIME(3)     NOT NULL                COMMENT '下次可执行时间',
    `error`         VARCHAR(1000)   NULL DEFAULT NULL       COMMENT '最近一次错误',
    `owner`         VARCHAR(64)     NULL DEFAULT NULL       COMMENT '抢占的实例标识',
    `owner_until`   DATETIME(3)     NULL DEFAULT NULL       COMMENT '抢占有效期，过期可被其他实例接手',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_todo_effect_kind_key` (`kind`, `effect_key`),
    KEY `i_todo_effect_due_time` (`due_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理副作用队列表';

-- 9、消息表：Web 渠道的发件回执与收件箱
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_message` (
    `id`            BIGINT          NOT NULL                AUTO_INCREMENT,
    `message_key`   VARCHAR(100)    NOT NULL                COMMENT '幂等键，重复发送只保留一条',
    `user_id`       INT             NOT NULL                COMMENT '接收用户ID',
    `ticket_id`     BIGINT          NULL DEFAULT NULL       COMMENT '工单ID',
    `trigger_id`    BIGINT          NULL DEFAULT NULL       COMMENT '回复的触发ID',
    `purpose`       VARCHAR(20)     NOT NULL                COMMENT '用途：acknowledgement-确认、answer-回答、attention-提问、result-结果、notice-通知',
    `content`       TEXT            NOT NULL                COMMENT '消息内容(Markdown)',
    `status`        VARCHAR(20)     NOT NULL                COMMENT '投递状态：pending-待发送、sent-已送达、dropped-已丢弃',
    `read_status`   TINYINT         NOT NULL DEFAULT 0      COMMENT '已读：0-未读、1-已读',
    `read_time`     DATETIME        NULL DEFAULT NULL       COMMENT '已读时间',
    `add_time`      DATETIME        NOT NULL                COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_todo_message_message_key` (`message_key`),
    KEY `i_todo_message_user_read` (`user_id`, `read_status`),
    KEY `i_todo_message_ticket_id` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理消息表';

-- 10、配置版本表：按块保存，每次保存新增一个版本，可回溯
CREATE TABLE IF NOT EXISTS `xxl_ai_todo_settings` (
    `id`             BIGINT          NOT NULL               AUTO_INCREMENT,
    `settings_block` VARCHAR(20)     NOT NULL               COMMENT '配置块：policy-策略、router-研判、worker-执行、prompts-提示词',
    `version`        INT             NOT NULL               COMMENT '块内版本号，从1递增',
    `content`        MEDIUMTEXT      NOT NULL               COMMENT '配置内容(JSON)',
    `baseline`       MEDIUMTEXT      NULL DEFAULT NULL      COMMENT '覆盖默认值时记录的默认基线(JSON)',
    `user_id`        INT             NOT NULL               COMMENT '保存人用户ID',
    `add_time`       DATETIME        NOT NULL               COMMENT '新增时间',
    `update_time`    DATETIME        NOT NULL               COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_todo_settings_block_version` (`settings_block`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级助理配置版本表';


-- ================== user and base ==================

-- 1、用户表
CREATE TABLE IF NOT EXISTS `xxl_ai_user` (
    `id`            INT             NOT NULL AUTO_INCREMENT   COMMENT '用户ID',
    `username`      VARCHAR(50)     NOT NULL                  COMMENT '账号',
    `password`      VARCHAR(100)    NOT NULL                  COMMENT '密码加密信息',
    `token`         VARCHAR(100)    NULL DEFAULT NULL         COMMENT '登录token',
    `status`        TINYINT         NOT NULL                  COMMENT '状态：0-正常、1-禁用',
    `role`          VARCHAR(20)     NOT NULL DEFAULT 'user'   COMMENT '角色编码：admin-管理员、user-普通用户',
    `real_name`     VARCHAR(50)     NULL DEFAULT NULL         COMMENT '真实姓名',
    `email`         VARCHAR(100)    NULL DEFAULT NULL         COMMENT '邮箱',
    `add_time`      DATETIME        NOT NULL                  COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                  COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_user_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2、系统配置表
CREATE TABLE IF NOT EXISTS `xxl_ai_config` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT     COMMENT '配置ID',
    `name`          VARCHAR(100)    NOT NULL                    COMMENT '配置名称',
    `key`           VARCHAR(100)    NOT NULL                    COMMENT '配置Key',
    `value`         VARCHAR(500)    NOT NULL                    COMMENT '配置Value',
    `status`        TINYINT         NOT NULL                    COMMENT '状态：0-正常、1-停用',
    `remark`        VARCHAR(500)    NULL DEFAULT NULL           COMMENT '备注',
    `add_time`      DATETIME        NOT NULL                    COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                    COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_config_type` (`key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 3、日志表
CREATE TABLE IF NOT EXISTS `xxl_ai_log` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '日志ID',
    `type`          INT             NOT NULL                     COMMENT '日志类型（如操作日志、登陆日志）',
    `module`        INT             NOT NULL                     COMMENT '系统模块（如用户管理）',
    `title`         VARCHAR(100)    NOT NULL                     COMMENT '日志标题',
    `content`       TEXT            NOT NULL                     COMMENT '日志内容',
    `operator`      VARCHAR(20)     NULL DEFAULT NULL            COMMENT '操作人',
    `ip`            VARCHAR(50)     NULL DEFAULT NULL            COMMENT '操作IP',
    `add_time`      DATETIME        NOT NULL                     COMMENT '新增时间',
    `update_time`   DATETIME        NOT NULL                     COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志表';


-- ================== for default data ==================


-- 1、默认用户
INSERT INTO `xxl_ai_user` (`id`, `username`, `password`, `token`, `status`, `role`, `real_name`, `add_time`, `update_time`)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '', 0, 'admin', 'XXL', now(), now()),
       (2, 'user', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '', 0, 'user', 'XXL', now(), now());

-- 2、系统配置
INSERT INTO `xxl_ai_config` (`name`, `key`, `value`, `status`, `remark`, `add_time`, `update_time`)
VALUES ('系统配置-登录验证码启用开关', 'system.login.captcha.enabled', 'true', 0, 'true 开启，false 关闭', now(), now()),
       ('系统配置-界面语言', 'system.i18n.language', 'zh', 0, 'zh 中文，en 英文', now(), now()),
       ('长期记忆内容最大长度', 'memory.content.max.length', '2000', 0, '生成记忆内容的最大字符数，超出截断', now(), now());

-- 3、默认业务空间
INSERT INTO `xxl_ai_space` (`name`, `code`, `status`, `remark`, `add_time`, `update_time`)
VALUES ('默认空间', 'default', 0, '系统默认业务空间', NOW(), NOW());

-- 3-1、用户-空间授权（普通用户默认授权默认空间，保证顶部空间切换器可用）
INSERT INTO `xxl_ai_user_space` (`user_id`, `space_id`, `add_time`, `update_time`)
SELECT 2, `id`, NOW(), NOW() FROM `xxl_ai_space` WHERE `code` = 'default';

-- 4、预设供应商（admin 在页面可随时修改 BaseURL/Key；OpenCodeGo 配置请求Header：x-opencode-session 会话头 {session} 占位替换）
INSERT INTO `xxl_ai_supplier` (`id`,`space_id`, `name`, `base_url`, `api_key`, `headers`, `status`, `remark`, `add_time`, `update_time`)
VALUES
    (1, 1, 'OpenCodeGo', 'https://opencode.ai/zen/go/v1', '', '{"x-opencode-session":"{session}"}', 0, 'OpenCode Go模型', NOW(), NOW()),
    (2, 1, 'Ollama', 'http://127.0.0.1:11434', '', null, 0, 'Ollama 模型', NOW(), NOW()),
    (3, 1, 'Deepseek', 'https://api.deepseek.com', '', null,0, 'Deepseek 模型', NOW(), NOW()),
    (4, 1, '智谱GLM', 'https://open.bigmodel.cn/api/paas/v4', '', null, 0, '智谱 模型', NOW(), NOW());

-- 5、预设供应商模型
INSERT INTO `xxl_ai_supplier_model` (`id`, `supplier_id`, `name`, `model`, `type`, `status`, `add_time`, `update_time`)
VALUES
    (1, 1, 'deepseek v4.1 flash', 'deepseek-v4.1-flash', 0, 0, NOW(), NOW()),
    (2, 1, 'glm 5.3', 'glm-5.3', 0, 0, NOW(), NOW()),
    (3, 2, 'Qwen3.5-0.8B', 'qwen3.5:0.8b', 0, 0, NOW(), NOW()),
    (4, 2, 'Qwen3.5-4B', 'qwen3.5:4b', 0, 0, NOW(), NOW()),
    (5, 2, 'Qwen-Embedding-0.8B', 'qwen3-embedding:0.6b', 1, 0, NOW(), NOW()),
    (6, 3, 'deepseek v4.1 flash', 'deepseek-v4.1-flash', 0, 0, NOW(), NOW()),
    (7, 3, 'deepseek v4 pro', 'deepseek-v4-pro', 0, 0, NOW(), NOW()),
    (8, 4, 'glm 5.3 flash', 'glm-5.3-flash', 0, 0, NOW(), NOW()),
    (9, 4, 'glm 5.3', 'glm-5.3', 0, 0, NOW(), NOW());

-- 6、预设 MCP 服务（内置 Java 远程示例 + 社区本地 stdio，作为「连接测试」联调用例）
INSERT INTO `xxl_ai_mcp` (`space_id`, `name`, `type`, `url`, `headers`, `config`, `status`, `remark`, `add_time`, `update_time`)
VALUES
    -- 远程 Streamable HTTP：示例MCP（由独立应用 xxl-ai-sample 提供，单端点聚合多 Tool 功能；）
    (1, '远程MCP服务（示例）', 0, 'http://127.0.0.1:8091/sample/mcp', null, '{"transport":"http","url":"http://127.0.0.1:8091/sample/mcp","headers":{}}', 0, '示例MCP服务（远程）：提供 get_current_time/calculator 等多工具能力', NOW(), NOW()),
    -- 本地 stdio 进程：：示例MCP
    (1, 'Fetch 网页抓取', 1, null, null, '{"transport":"stdio","command":"npx","args":["-y","mcp-fetch-server"],"env":{}}', 0, '网页抓取与内容提取', NOW(), NOW()),
    (1, 'Filesystem 文件系统', 1, null, null, '{"transport":"stdio","command":"npx","args":["-y","@modelcontextprotocol/server-filesystem","/tmp"],"env":{}}', 0, '本地文件系统读写（请按需调整授权目录参数）', NOW(), NOW()),
    (1, 'Playwright 浏览器工具', 1, null, null, '{"transport":"stdio","command":"npx","args":["@playwright/mcp@latest","--headless","--isolated"]}', 0, '浏览器操作工具', NOW(), NOW());

-- 7、预设 SKILL（开箱即用：sql-optimizer 慢SQL优化、ppt 演示文稿生成）
INSERT INTO `xxl_ai_skill` (`id`, `space_id`, `name`, `description`, `version`, `status`, `add_time`, `update_time`)
VALUES
    (1, 1, 'sql-optimizer', 'SQL 优化 Skill：基于 EXPLAIN 执行计划分析慢查询，输出索引优化与 SQL 改写建议（全表扫描/低效索引/深分页等）', '1.0', 0, NOW(), NOW()),
    (2, 1, 'ppt', '演示文稿生成 Skill：基于 python-pptx 将结构化内容输出为排版规范的 .pptx 演示文稿，供汇报演示使用', '1.0', 0, NOW(), NOW());

-- 7-1、预设 SKILL 固定文件树（SKILL.md + scripts/ + reference/ 为锁定骨架，与新增播种结构一致）
INSERT INTO `xxl_ai_skill_file` (`skill_id`, `parent_id`, `name`, `type`, `file_type`, `content`, `locked`, `sort`, `add_time`, `update_time`)
VALUES
    -- sql-optimizer：骨架（锁定）
    (1, 0, 'SKILL.md', 1, 'md', CONCAT(STRINGDECODE('---\n'), STRINGDECODE('name: sql-optimizer\n'), STRINGDECODE('description: SQL 优化，基于 EXPLAIN 执行计划分析慢查询，输出索引与改写建议\n'), STRINGDECODE('---\n'), STRINGDECODE('\n'), STRINGDECODE('# sql-optimizer\n'), STRINGDECODE('\n'), STRINGDECODE('对慢查询执行结构化分析：借助 EXPLAIN 执行计划定位全表扫描、低效索引与深分页等问题，输出分级优化建议。\n'), STRINGDECODE('\n'), STRINGDECODE('## 使用方式\n'), STRINGDECODE('1. 获取待分析 SQL 与其 EXPLAIN 执行计划（含 type/key/rows/Extra 字段）。\n'), STRINGDECODE('2. 查阅 reference/explain-guide.md 解读执行计划关键字段。\n'), STRINGDECODE('3. 按 reference/optimization-strategy.md 的索引与改写策略逐条核对。\n'), STRINGDECODE('4. 汇总输出优化报告，可借助 scripts/plan-summary.py 生成 Markdown 报告。\n'), STRINGDECODE('\n'), STRINGDECODE('## 目录说明\n'), STRINGDECODE('- SKILL.md：入口与流程说明\n'), STRINGDECODE('- scripts/：执行计划分析脚本\n'), '- reference/：执行计划解读与优化策略'), 1, 1, NOW(), NOW()),
    (1, 0, 'scripts', 0, NULL, NULL, 1, 2, NOW(), NOW()),
    (1, 0, 'reference', 0, NULL, NULL, 1, 3, NOW(), NOW()),
    -- sql-optimizer：子级
    ( 1, 2, 'plan-summary.py', 1, 'py', CONCAT(STRINGDECODE('#!/usr/bin/env python3\n'), STRINGDECODE('"""基于 JSON 输入的问题列表生成 SQL 优化建议报告（Markdown）。"""\n'), STRINGDECODE('import json\n'), STRINGDECODE('import sys\n'), STRINGDECODE('\n'), STRINGDECODE('def main():\n'), STRINGDECODE('    items = json.load(sys.stdin)\n'), STRINGDECODE('    for item in items:\n'), STRINGDECODE('        level = item.get("level", "P2")\n'), STRINGDECODE('        print("- [{}] {}: {}".format(level, item.get("table", ""), item.get("msg", "")))\n'), STRINGDECODE('\n'), STRINGDECODE('if __name__ == "__main__":\n'), '    main()'), 0, 1, NOW(), NOW()),
    ( 1, 3, 'explain-guide.md', 1, 'md', CONCAT(STRINGDECODE('# EXPLAIN 执行计划解读\n'), STRINGDECODE('\n'), STRINGDECODE('- type（访问类型，性能从优到劣）：system/const/eq_ref > ref/range > index/ALL，ALL 全表扫描需重点排查\n'), STRINGDECODE('- key：实际命中的索引，NULL 表示未使用索引\n'), STRINGDECODE('- rows：预估扫描行数，与 type 结合判断是否选错索引\n'), STRINGDECODE('- Extra 高频项：\n'), STRINGDECODE('  - Using filesort：结果排序未走索引，需增加排序列索引\n'), STRINGDECODE('  - Using temporary：group by/order by 与 where 字段不一致产生临时表\n'), '  - Using index：覆盖索引扫描，理想状态'), 0, 1, NOW(), NOW()),
    ( 1, 3, 'optimization-strategy.md', 1, 'md', CONCAT(STRINGDECODE('# SQL 优化策略\n'), STRINGDECODE('\n'), STRINGDECODE('## 索引\n'), STRINGDECODE('- WHERE 等值/范围条件字段优先建索引，多条件按最左前缀原则组合\n'), STRINGDECODE('- ORDER BY / GROUP BY 字段并入索引，消除 filesort / temporary\n'), STRINGDECODE('- 低基数字段（状态/性别）慎建独立索引，组合索引时置于右侧\n'), STRINGDECODE('- 避免在索引列上做函数运算或隐式类型转换，防止索引失效\n'), STRINGDECODE('\n'), STRINGDECODE('## SQL 改写\n'), STRINGDECODE('- 深分页改为键集分页：WHERE id > 上一页最大值 替代 LIMIT 大偏移\n'), STRINGDECODE('- 大 IN 子句用 EXISTS 替换，关联子查询可改 JOIN\n'), STRINGDECODE('- 避免 SELECT *，只取所需列以利用覆盖索引\n'), '- 大批量更新/删除分批执行，缩短事务与锁持有时间'), 0, 2, NOW(), NOW()),
    -- ppt：骨架（锁定）
    (2, 0, 'SKILL.md', 1, 'md', CONCAT(STRINGDECODE('---\n'), STRINGDECODE('name: ppt\n'), STRINGDECODE('description: 演示文稿生成，基于 python-pptx 一键生成排版规范的 .pptx 演示文稿\n'), STRINGDECODE('---\n'), STRINGDECODE('\n'), STRINGDECODE('# ppt\n'), STRINGDECODE('\n'), STRINGDECODE('生成 PowerPoint 演示文稿：内容结构化输入，输出排版规范（标题/要点/表格）的 .pptx 文件。\n'), STRINGDECODE('\n'), STRINGDECODE('## 使用方式\n'), STRINGDECODE('1. 在 scripts/ 目录安装依赖：pip install -r requirements.txt。\n'), STRINGDECODE('2. 参照 scripts/ppt.py 提供的辅助函数组织幻灯片内容（标题/要点/表格）。\n'), STRINGDECODE('3. 生成结果以 .pptx 落盘，供汇报与演示使用。\n'), STRINGDECODE('\n'), STRINGDECODE('## 目录说明\n'), STRINGDECODE('- SKILL.md：入口与使用说明\n'), STRINGDECODE('- scripts/：python-pptx 封装脚本与依赖清单\n'), '- reference/：排版与配色参考'), 1, 1, NOW(), NOW()),
    (2, 0, 'scripts', 0, NULL, NULL, 1, 2, NOW(), NOW()),
    (2, 0, 'reference', 0, NULL, NULL, 1, 3, NOW(), NOW()),
    -- ppt：子级
    ( 2, 8, 'ppt.py', 1, 'py', CONCAT(STRINGDECODE('"""python-pptx 演示文稿生成封装：标题/要点/表格统一样式。"""\n'), STRINGDECODE('from pptx import Presentation\n'), STRINGDECODE('\n'), STRINGDECODE('def build(slide_titles, bullets=None):\n'), STRINGDECODE('    prs = Presentation()\n'), STRINGDECODE('    for title, items in zip(slide_titles, bullets or []):\n'), STRINGDECODE('        slide = prs.slides.add_slide(prs.slide_layouts[1])\n'), STRINGDECODE('        slide.shapes.title.text = title\n'), STRINGDECODE('        body = slide.placeholders[1].text_frame\n'), STRINGDECODE('        for i, text in enumerate(items):\n'), STRINGDECODE('            para = body.paragraphs[0] if i == 0 else body.add_paragraph()\n'), STRINGDECODE('            para.text = text\n'), STRINGDECODE('    return prs\n'), STRINGDECODE('\n'), STRINGDECODE('def save(prs, path):\n'), '    prs.save(path)'), 0, 1, NOW(), NOW()),
    ( 2, 8, 'requirements.txt', 1, 'txt', 'python-pptx>=0.6.21', 0, 2, NOW(), NOW()),
    ( 2, 9, 'style-guide.md', 1, 'md', CONCAT(STRINGDECODE('# 排版规范参考\n'), STRINGDECODE('\n'), STRINGDECODE('- 封面页用布局 0，内容页用标题+要点布局（每页要点不超过 6 条）\n'), STRINGDECODE('- 表格页表头加粗、列宽自适应，避免单元格文字溢出\n'), STRINGDECODE('- 配色统一使用主题色，装饰克制，聚焦内容\n'), '- 文件命名：{主题}-{yyyyMMdd}.pptx'), 0, 1, NOW(), NOW());


-- 8、预设知识库与知识文档（RAG 测试数据：《三体》主题，嵌入模型使用本地 Ollama qwen3-embedding，开箱即可向量化/检索）
INSERT INTO `xxl_ai_knowledge_base` (`id`, `space_id`, `name`, `description`, `embed_supplier_id`, `embed_model_id`, `chunk_size`, `chunk_overlap`, `top_k`, `status`, `add_time`, `update_time`)
VALUES
    (1, 1, '三体知识库', '刘慈欣《三体》系列知识库：主要人物、核心设定、关键情节，作为 RAG 知识问答联调用例', 2, 5, 500, 50, 5, 0, NOW(), NOW());

INSERT INTO `xxl_ai_knowledge_doc` (`id`, `space_id`, `base_id`, `name`, `content`, `chunk_count`, `status`, `add_time`, `update_time`)
VALUES
    (1, 1, 1, '主要人物.md', CONCAT(STRINGDECODE('# 三体 · 主要人物\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 叶文洁\n'),
        STRINGDECODE('- 红岸基地工程师，向宇宙发出地球文明的第一声呼唤，成为三体危机的源头。\n'),
        STRINGDECODE('- 提出宇宙社会学基本框架，深刻影响面壁计划与黑暗森林理论的诞生。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 罗辑\n'),
        STRINGDECODE('- 三体世界的面壁人，以雪地工程与摇篮计划构建对三体的真实威慑，成为黑暗森林威慑的执剑人。\n'),
        STRINGDECODE('- 曾长期隐居，威慑纪元的关键人物，其个人意识决定地球文明的存亡。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 程心\n'),
        STRINGDECODE('- 接替罗辑成为执剑人，因威慑失败导致威慑纪元终结，地球文明被迫进入威慑后纪元。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 史强\n'),
        STRINGDECODE('- 地球防务安全部警官，多次在危机中救下汪淼与罗辑，是古筝行动等关键任务的执行者。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 云天明\n'),
        '- 通过阶梯计划向三体世界送出大脑，三体人得到他后，以童话故事向人类传递了曲率驱动等关键信息。'), 0, 0, NOW(), NOW()),
    (2, 1, 1, '核心设定.md', CONCAT(STRINGDECODE('# 三体 · 核心设定\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 三体文明\n'),
        STRINGDECODE('- 位于半人马座三星系统，受三体运动困扰，文明在毁灭与重生之间历经数百次轮回。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 智子\n'),
        STRINGDECODE('- 三体人派往地球的高维微观智能，可干扰粒子对撞实验、封锁地球基础科学进步。\n'),
        STRINGDECODE('- 能实时监视地球文明动向，是人类面临的最大压迫来源之一。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 面壁计划\n'),
        STRINGDECODE('- 地球为应对三体危机推行的战略计划，面壁者以人类不可见的思维挣脱智子监视。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 黑暗森林理论\n'),
        STRINGDECODE('- 宇宙社会学的核心推论：文明是带枪的猎人，暴露坐标即遭毁灭；生存是第一需求，物质总量守恒。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 水滴与二向箔\n'),
        STRINGDECODE('- 水滴：三体强相互作用力探测器，以碾压式速度展示文明代差。\n'),
        '- 二向箔：将三维空间向二维坍缩的降维打击武器，太阳系最终毁于二向箔。'), 0, 0, NOW(), NOW()),
    (3, 1, 1, '关键情节.md', CONCAT(STRINGDECODE('# 三体 · 关键情节\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 红岸基地\n'),
        STRINGDECODE('- 叶文洁在此利用太阳增益反射发送了地球文明的第一条星际信息，埋下三体危机的伏笔。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 古筝行动\n'),
        STRINGDECODE('- 以纳米丝切割审判日号，截获三体世界与地球叛军的关键通讯情报。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 威慑纪元\n'),
        STRINGDECODE('- 罗辑通过摇篮系统建立执剑人威慑，地球与三体维持脆弱和平数十年。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 黑暗森林打击\n'),
        STRINGDECODE('- 人类监听所有恒星坐标，暴露坐标的恒星会被高等文明定向清理。\n'),
        STRINGDECODE('\n'),
        STRINGDECODE('## 威慑后纪元与逃亡主义\n'),
        '- 威慑失败后地球进入威慑后纪元，人类整体战略转向逃亡主义与本土生存博弈。'), 0, 0, NOW(), NOW());

-- 9、预设 Agent（开箱即用示例：RAG 知识问答 / Skill 工具 / MCP 工具三类能力演示）
INSERT INTO `xxl_ai_agent` (id, space_id, name, intro, model_supplier_id, model_id, system_prompt, kb_ids, mcp_ids, skill_ids, memory_status, publish_status, uuid, add_time, update_time)
VALUES
    -- 基础示例：绑定知识库=1 三体知识库，便于 RAG 知识问答联调（默认开启长期记忆）
    (1, 1, 'Hi Agent', '你的专属个人助理、思路接力伙伴！', 1, 1,
     '你叫Jason，是用户的专属个人助理。请根据用户的提问，结合知识库内容与自身知识，给出简明、准确、专业的回答。人格特征：耐心、细致、善于总结。请尽量使用中文回答，必要时可使用英文术语。',
     null, '2,3', null, 1, 1, '40bd05136fc34d11b88e1e401d26f50e', NOW(), NOW()),
    -- RAG 示例：绑定知识库=1，回答仅依据检索内容（模型：Deepseek / DeepSeek V4 Pro）
    (2, 1, '三体知识问答Agent', '基于《三体》知识库的 RAG 问答助手，回答仅依据知识库内容', 1, 1,
     '你是《三体》知识问答助手。请仅依据检索到的知识库内容回答用户问题，优先引用原著中的人物、设定与情节；知识库中没有的信息如实说明，不要编造。',
     '1', null, null, 0, 0, NULL, NOW(), NOW()),
    -- Skill 示例：绑定 sql-optimizer 技能 + 示例 MCP 工具（模型：智谱GLM / GLM-5.3）
    (3, 1, 'SQL优化Agent', '基于 sql-optimizer Skill 的 SQL 诊断与优化建议助手', 1, 1,
     '你是资深数据库性能优化专家。收到 SQL 后，先获取其表结构与 EXPLAIN 执行计划，再结合 sql-optimizer 技能的分析方法定位全表扫描、索引失效、深分页等问题，按优先级输出可落地的索引与 SQL 改写建议。',
     null, null, '1', 0, 0, NULL, NOW(), NOW()),
    -- MCP 示例：绑定 Fetch 网页抓取 MCP（模型：Deepseek / DeepSeek V4 Flash）
    (4, 1, '网页总结Agent', '基于 Fetch MCP 抓取网页正文并提炼要点', 1, 1,
     '你是网页内容总结助手。根据用户提供的 URL，调用网页抓取工具获取正文，提炼核心要点与结论，输出结构清晰的中文摘要，并附上原文链接。',
     null, '2', null, 0, 0, NULL, NOW(), NOW());


