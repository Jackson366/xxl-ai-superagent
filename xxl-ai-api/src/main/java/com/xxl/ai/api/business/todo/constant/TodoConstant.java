package com.xxl.ai.api.business.todo.constant;

/**
 * 超级助理常量（虚拟Agent、领域取值、调度参数）
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoConstant {

    private TodoConstant() {
    }

    // ---------------------- 虚拟 Agent ----------------------

    /** 超级助理虚拟AgentID：不入库，仅作 harness 工具工厂的缓存键与沙箱目录名（取负数，不与自增ID冲突） */
    public static final long SUPERAGENT_AGENT_ID = -1L;

    // ---------------------- 触发类型 ----------------------

    /** 触发类型：新建待办 */
    public static final String TRIGGER_CREATE = "create";
    /** 触发类型：追加说明 */
    public static final String TRIGGER_APPEND = "append";
    /** 触发类型：回答待人工决定 */
    public static final String TRIGGER_ANSWER = "answer";

    // ---------------------- 决定与强度 ----------------------

    /** 决定：接单 */
    public static final String DECISION_ACCEPT = "accept";
    /** 决定：停止 */
    public static final String DECISION_STOP = "stop";
    /** 决定：忽略 */
    public static final String DECISION_IGNORE = "ignore";
    /** 决定：未完成（超时或研判失败） */
    public static final String DECISION_UNAVAILABLE = "unavailable";

    /** 强度：明确 */
    public static final String STRENGTH_EXPLICIT = "explicit";
    /** 强度：推断 */
    public static final String STRENGTH_INFERRED = "inferred";

    // ---------------------- 派工与报告 ----------------------

    /** 派工状态：待启动 */
    public static final String ASSIGNMENT_PENDING = "pending";
    /** 派工状态：执行中 */
    public static final String ASSIGNMENT_RUNNING = "running";
    /** 派工状态：已结束 */
    public static final String ASSIGNMENT_ENDED = "ended";

    /** 报告：进展 */
    public static final String REPORT_PROGRESS = "progress";
    /** 报告：等待新触发 */
    public static final String REPORT_WAITING = "waiting";
    /** 报告：结果就绪 */
    public static final String REPORT_READY = "ready";
    /** 报告：受阻 */
    public static final String REPORT_BLOCKED = "blocked";
    /** 报告：失败 */
    public static final String REPORT_FAILED = "failed";
    /** 报告：无需处理 */
    public static final String REPORT_DROPPED = "dropped";

    // ---------------------- 工单结束 ----------------------

    /** 结束方：执行器 */
    public static final String ENDED_BY_EXECUTOR = "executor";
    /** 结束方：人工 */
    public static final String ENDED_BY_HUMAN = "human";

    /** 结束结果：完成 */
    public static final String OUTCOME_COMPLETED = "completed";
    /** 结束结果：无需处理 */
    public static final String OUTCOME_DROPPED = "dropped";
    /** 结束结果：人工停止 */
    public static final String OUTCOME_STOPPED = "stopped";

    // ---------------------- 工单输入 ----------------------

    /** 输入类型：触发 */
    public static final String INPUT_TRIGGER = "trigger";
    /** 输入类型：策略变更 */
    public static final String INPUT_POLICY_CHANGED = "policy-changed";
    /** 输入类型：恢复 */
    public static final String INPUT_RESUMED = "resumed";
    /** 输入类型：文本（如重启恢复说明） */
    public static final String INPUT_TEXT = "text";

    // ---------------------- 消息 ----------------------

    /** 消息用途：确认 */
    public static final String PURPOSE_ACKNOWLEDGEMENT = "acknowledgement";
    /** 消息用途：回答 */
    public static final String PURPOSE_ANSWER = "answer";
    /** 消息用途：提问 */
    public static final String PURPOSE_ATTENTION = "attention";
    /** 消息用途：结果 */
    public static final String PURPOSE_RESULT = "result";
    /** 消息用途：通知 */
    public static final String PURPOSE_NOTICE = "notice";

    /** 消息状态：待发送 */
    public static final String MESSAGE_PENDING = "pending";
    /** 消息状态：已送达 */
    public static final String MESSAGE_SENT = "sent";
    /** 消息状态：已丢弃 */
    public static final String MESSAGE_DROPPED = "dropped";

    // ---------------------- 副作用类型 ----------------------

    /** 副作用：研判（key=evalId） */
    public static final String EFFECT_ASSESSMENT = "assessment";
    /** 副作用：启动派工（key=assignmentId） */
    public static final String EFFECT_LAUNCH = "launch";
    /** 副作用：投递输入（key=ticketId） */
    public static final String EFFECT_DELIVERY = "delivery";
    /** 副作用：发送消息（key=messageKey） */
    public static final String EFFECT_REPLY = "reply";

    // ---------------------- 调度参数 ----------------------

    /** 内部副作用退避：初始 2s，翻倍至 60s */
    public static final long RETRY_INITIAL_MS = 2_000L;
    public static final long RETRY_MAX_MS = 60_000L;
    /** 消息投递退避：初始 5s，翻倍至 300s */
    public static final long REPLY_RETRY_INITIAL_MS = 5_000L;
    public static final long REPLY_RETRY_MAX_MS = 300_000L;
    /** 连续失败达到该次数才算健康问题 */
    public static final int FAILURES_BEFORE_ERROR = 3;
    /** 抢占租期（秒）：实例宕机后，其他实例在租期过后接手 */
    public static final int EFFECT_LEASE_SECONDS = 120;

    /** Eval 超时（秒） */
    public static final int EVAL_TIMEOUT_SECONDS = 120;
    /** Eval 超时后重新排队的次数，再超时记 unavailable */
    public static final int EVAL_REQUEUES = 2;

    /** 默认并发执行槽位数 */
    public static final int DEFAULT_MAX_WORKERS = 3;
    /** 留给人工追问的备用槽位数 */
    public static final int HUMAN_RESERVED_SLOTS = 1;
    /** 每轮执行时限（分钟），到时中断本轮并报 waiting */
    public static final int BACKGROUND_LIMIT_MINUTES = 30;
    /** 自动重试：单工单最大次数（按派工结束原因 watchdog 计数） */
    public static final int WATCHDOG_MAX_RETRIES = 2;
    /** 自动重试：报告落库后等待时长(ms)，避免对同一错误立即重跑 */
    public static final long WATCHDOG_RETRY_DELAY_MS = 60_000L;
    /** 派工结束原因：watchdog 自动重试 */
    public static final String ENDED_REASON_WATCHDOG = "watchdog";

    // ---------------------- 配置块 ----------------------

    /** 配置块：策略（accept mode），进入策略版本 */
    public static final String BLOCK_POLICY = "policy";
    /** 配置块：模型（超级助理空间、Router/Worker 模型） */
    public static final String BLOCK_MODEL = "model";
    /** 配置块：执行（MCP、Skill、并发、暂停） */
    public static final String BLOCK_WORKER = "worker";
    /** 配置块：提示词（Router、Worker） */
    public static final String BLOCK_PROMPTS = "prompts";
    /** 策略版本修订号：授权语义变化时递增，使旧版本决定失效 */
    public static final String POLICY_REVISION = "mandate-v1";

    // ---------------------- 工单阶段（自上而下首条命中） ----------------------

    /** 阶段：输入投递失败，等人重试 */
    public static final String STAGE_DELIVERY = "delivery";
    /** 阶段：等人回答 */
    public static final String STAGE_ANSWER = "answer";
    /** 阶段：受阻，等人处理 */
    public static final String STAGE_BLOCKED = "blocked";
    /** 阶段：失败，等人重试 */
    public static final String STAGE_FAILED = "failed";
    /** 阶段：排队等待槽位 */
    public static final String STAGE_QUEUED = "queued";
    /** 阶段：本轮结束，等待新消息 */
    public static final String STAGE_IDLE = "idle";
    /** 阶段：执行中 */
    public static final String STAGE_WORKING = "working";
    /** 阶段：已结束 */
    public static final String STAGE_ENDED = "ended";
    /** 线程状态：研判中（尚无工单） */
    public static final String STAGE_EVALUATING = "evaluating";
    /** 线程状态：研判未完成，等人重新研判或忽略 */
    public static final String STAGE_UNAVAILABLE = "unavailable";
    /** 线程状态：研判决定不接单 */
    public static final String STAGE_IGNORED = "ignored";

    // ---------------------- 原因编码（前端按 business.todo.reason.{code} 展示） ----------------------

    /** 原因：模型给出的原话（text 字段） */
    public static final String REASON_SAID = "said";
    /** 原因：提交人账号已停用 */
    public static final String REASON_SENDER_INACTIVE = "senderInactive";
    /** 原因：提交人不是管理员 */
    public static final String REASON_SENDER_NOT_ADMIN = "senderNotAdmin";
    /** 原因：未配置超级助理模型 */
    public static final String REASON_MODEL_NOT_CONFIGURED = "modelNotConfigured";
    /** 原因：超级助理空间已停用或已删除 */
    public static final String REASON_SPACE_DISABLED = "spaceDisabled";
    /** 原因：空间未标记“超级助理使用” */
    public static final String REASON_SPACE_NOT_MARKED = "spaceNotMarked";
    /** 原因：模型或供应商不可用 */
    public static final String REASON_MODEL_UNAVAILABLE = "modelUnavailable";
    /** 原因：研判期间策略变更 */
    public static final String REASON_POLICY_CHANGED = "policyChanged";
    /** 原因：研判超时 */
    public static final String REASON_TIMEOUT = "timeout";
    /** 原因：研判调用失败 */
    public static final String REASON_ASSESSMENT_FAILED = "assessmentFailed";
    /** 原因：该触发已发起过工单 */
    public static final String REASON_STARTED_TICKET = "startedTicket";
    /** 原因：观察模式，会接单但未执行（text 为模型理由） */
    public static final String REASON_OBSERVED = "observed";

    // ---------------------- 授权依据 ----------------------

    /** 授权依据：管理员在 Web 表单直接提交 */
    public static final String BASIS_ADDRESSED = "addressed";

    // ---------------------- 接单模式 ----------------------

    /** 接单模式：推断请求不进入研判 */
    public static final String MODE_OFF = "off";
    /** 接单模式：推断请求只记录“会接单”，不执行（默认） */
    public static final String MODE_OBSERVE = "observe";
    /** 接单模式：推断请求研判通过即接单 */
    public static final String MODE_AUTO = "auto";

}
