package com.xxl.ai.api.business.todo.service.impl;

import com.xxl.ai.api.business.agent.model.entity.Agent;
import com.xxl.ai.api.business.harness.superagent.SuperagentRouterTool;
import com.xxl.ai.api.business.harness.superagent.SuperagentWorkerTool;
import com.xxl.ai.api.business.supplier.model.SupplierRuntime;
import com.xxl.ai.api.business.todo.constant.TodoConstant;
import com.xxl.ai.api.business.todo.mapper.TodoAssignmentMapper;
import com.xxl.ai.api.business.todo.mapper.TodoEffectMapper;
import com.xxl.ai.api.business.todo.mapper.TodoThreadMapper;
import com.xxl.ai.api.business.todo.mapper.TodoTicketMapper;
import com.xxl.ai.api.business.todo.model.dto.TodoCandidate;
import com.xxl.ai.api.business.todo.model.dto.TodoRound;
import com.xxl.ai.api.business.todo.model.dto.TodoWakeEvent;
import com.xxl.ai.api.business.todo.model.entity.TodoAssignment;
import com.xxl.ai.api.business.todo.model.entity.TodoEffect;
import com.xxl.ai.api.business.todo.model.entity.TodoThread;
import com.xxl.ai.api.business.todo.model.entity.TodoTicket;
import com.xxl.ai.api.business.todo.model.json.TodoEvaluating;
import com.xxl.ai.api.business.todo.model.json.TodoSettingsContent;
import com.xxl.ai.api.business.todo.service.TodoEffectService;
import com.xxl.ai.api.business.todo.service.TodoAssistantService;
import com.xxl.ai.api.business.todo.service.TodoEngineService;
import com.xxl.ai.api.business.todo.service.TodoSettingsService;
import com.xxl.ai.api.business.todo.service.TodoObservationService;
import com.xxl.ai.api.business.harness.superagent.SuperagentTraceEvent;
import com.xxl.ai.api.business.todo.util.TodoJsonTool;
import com.xxl.ai.api.business.todo.util.TodoStageTool;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 超级助理 副作用调度器实现
 *
 * 1、调度线程（单线程）：每秒一次，或事务提交后被唤醒；依次开始研判、检查研判超时、派发副作用。
 * 2、副作用执行前先抢占（owner + 租期），成功后删除；失败按指数退避重试，连续失败达到阈值计为健康问题。
 * 3、启动与投递只在事务内“开始一轮”后立即完成副作用，模型调用在执行线程池里跑，不长期占用副作用；
 *    进程中断时由启动恢复（recover）把被打断的一轮记为 hostRestarted 并重新派工。
 * 4、槽位：普通启动最多 maxWorkers 个；追问投递可多用 1 个备用槽，新工作不会挡住人继续推进已有工单。
 *
 * 限制：槽位计数与启动恢复按单实例设计；多实例部署时副作用抢占仍成立，但槽位可能短暂超出、恢复会误判其他实例的执行。
 *
 * @author xxl-ai 2026-11-10
 */
@Service
public class TodoEffectServiceImpl implements TodoEffectService {

    private static final Logger logger = LoggerFactory.getLogger(TodoEffectServiceImpl.class);

    /** 每次扫描最多派发的消息数 */
    private static final int REPLY_BATCH = 20;
    /** 研判并发数 */
    private static final int ASSESSMENT_CONCURRENCY = 1;
    /** watchdog 扫描间隔（秒） */
    private static final int WATCHDOG_INTERVAL_SECONDS = 30;

    @Resource
    private TodoEngineService engine;
    @Resource
    private TodoSettingsService settingsService;
    @Resource
    private TodoEffectMapper effectMapper;
    @Resource
    private TodoThreadMapper threadMapper;
    @Resource
    private TodoAssignmentMapper assignmentMapper;
    @Resource
    private TodoTicketMapper ticketMapper;
    @Resource
    private SuperagentRouterTool routerTool;
    @Resource
    @Lazy
    private TodoAssistantService assistant; /* 仅在调度时调用，助理读端概览依赖调度器 */
    @Resource
    private SuperagentWorkerTool workerTool;
    @Resource
    private TodoObservationService observation;

    /** 本实例标识（副作用抢占） */
    private final String owner = "todo-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    /** 调度线程 */
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "todo-dispatch"));
    /** 研判线程池 */
    private final ExecutorService assessmentPool = Executors.newFixedThreadPool(ASSESSMENT_CONCURRENCY, r -> daemon(r, "todo-router"));
    /** Worker 执行线程池（上限由槽位控制，线程按需创建） */
    private final ExecutorService workerPool = Executors.newCachedThreadPool(r -> daemon(r, "todo-worker"));
    /** 超时计时 */
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "todo-timer"));
    /** watchdog：自动修复可恢复中断的扫描线程 */
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "todo-watchdog"));
    /** 进行中的研判数 */
    private final AtomicInteger assessing = new AtomicInteger();
    /** 进行中的 Worker 轮次（工单ID → 执行上下文），供停止中断 */
    private final Map<Long, RoundRun> runs = new ConcurrentHashMap<>();
    /** 已排队一次立即调度，避免重复唤醒堆积 */
    private final AtomicBoolean kicked = new AtomicBoolean();
    /** 已启动（应用就绪后） */
    private volatile boolean started;
    /** 正在关闭：被打断的一轮留给下次启动恢复，不写失败报告 */
    private volatile boolean stopping;
    /** 上次扫描失败的错误，相同错误不重复打印 */
    private volatile String lastTickError;

    /**
     * 一轮 Worker 执行的上下文
     */
    private static class RoundRun {

        private volatile Thread thread;                             /* 执行线程 */
        private final AtomicBoolean cancelled = new AtomicBoolean(); /* 人工停止 */
        private final AtomicBoolean timedOut = new AtomicBoolean();  /* 超过单轮时限 */

    }

    // ---------------------- 生命周期 ----------------------

    /**
     * 应用就绪：先恢复被打断的执行，再开始周期调度
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            engine.recover();
        } catch (Exception e) {
            // 未执行建表脚本时不影响应用启动，调度循环里会持续提示
            logger.warn("超级助理启动恢复失败, err={}", e.getMessage());
        }
        started = true;
        dispatcher.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
        watchdog.scheduleWithFixedDelay(this::watch, WATCHDOG_INTERVAL_SECONDS, WATCHDOG_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("超级助理调度器已启动, owner={}", owner);
    }

    /**
     * 关闭：中断进行中的执行，被打断的一轮由下次启动恢复
     */
    @PreDestroy
    public void stop() {
        stopping = true;
        dispatcher.shutdownNow();
        timer.shutdownNow();
        watchdog.shutdownNow();
        assessmentPool.shutdownNow();
        workerPool.shutdownNow();
        try {
            effectMapper.releaseByOwner(owner);
        } catch (Exception e) {
            logger.warn("超级助理释放副作用抢占失败, err={}", e.getMessage());
        }
    }

    /**
     * 事务提交后唤醒：人工停止时中断该工单正在执行的一轮
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onWake(TodoWakeEvent event) {
        if (event.stopTicketId() != null) {
            RoundRun run = runs.get(event.stopTicketId());
            if (run != null) {
                run.cancelled.set(true);
                interrupt(run);
            }
        }
        kick();
    }

    /**
     * 立即安排一次调度（已排队则跳过）
     */
    private void kick() {
        if (started && !stopping && kicked.compareAndSet(false, true)) {
            try {
                dispatcher.execute(this::tick);
            } catch (Exception e) {
                kicked.set(false);
            }
        }
    }

    // ---------------------- 调度 ----------------------

    /**
     * 一次调度：研判 → 消息 → 研判执行 → 启动 → 投递
     */
    private void tick() {
        kicked.set(false);
        if (stopping) {
            return;
        }
        try {
            TodoSettingsContent.Worker worker = settingsService.worker();
            dispatchReplies();
            if (!worker.paused) {
                startEvals();
                dispatchAssessments(TodoConstant.EFFECT_ASSESSMENT, this::assess);
                dispatchRounds(TodoConstant.EFFECT_LAUNCH, worker.maxWorkers);
                dispatchRounds(TodoConstant.EFFECT_DELIVERY, worker.maxWorkers + TodoConstant.HUMAN_RESERVED_SLOTS);
            }
            dispatchAssessments(TodoConstant.EFFECT_ASSISTANT, this::answerAssistant);
            lastTickError = null;
        } catch (Exception e) {
            String error = String.valueOf(e.getMessage());
            if (!error.equals(lastTickError)) {
                logger.error("超级助理调度失败, err={}", error, e);
                lastTickError = error;
            }
        }
    }

    /**
     * 开始研判与检查超时：有待研判触发的线程开始 Eval；研判中的线程到期后重排或记未完成
     */
    private void startEvals() {
        long now = System.currentTimeMillis();
        for (TodoThread thread : threadMapper.listActive()) {
            try {
                TodoEvaluating evaluating = TodoJsonTool.fromJson(thread.getEvaluating(), TodoEvaluating.class);
                if (evaluating == null) {
                    engine.startEval(thread.getId());
                } else if (evaluating.delivered > 0 && evaluating.deadline <= now) {
                    engine.expireEval(thread.getId());
                }
            } catch (Exception e) {
                logger.warn("超级助理开始研判失败, threadId={}, err={}", thread.getId(), e.getMessage());
            }
        }
    }

    /**
     * 派发消息：Web 渠道写收件箱即送达
     */
    private void dispatchReplies() {
        for (TodoEffect effect : effectMapper.listDue(TodoConstant.EFFECT_REPLY, REPLY_BATCH, new Date())) {
            if (!claim(effect)) {
                continue;
            }
            try {
                engine.deliverMessage(effect.getEffectKey());
                finish(effect);
            } catch (Exception e) {
                fail(effect, e, TodoConstant.REPLY_RETRY_INITIAL_MS, TodoConstant.REPLY_RETRY_MAX_MS);
            }
        }
    }

    /**
     * 派发研判：并发受限，Router 调用在研判线程池执行
     */
    private void dispatchAssessments(String kind, Consumer<TodoEffect> run) {
        int free = ASSESSMENT_CONCURRENCY - assessing.get();
        if (free <= 0) {
            return;
        }
        for (TodoEffect effect : effectMapper.listDue(kind, free, new Date())) {
            if (!claim(effect)) {
                continue;
            }
            assessing.incrementAndGet();
            assessmentPool.execute(() -> {
                try {
                    run.accept(effect);
                } finally {
                    assessing.decrementAndGet();
                    kick();
                }
            });
        }
    }

    /* 回复与研判共用受限模型线程池，独立于 Worker 暂停开关 */
    private void answerAssistant(TodoEffect effect) {
        try {
            assistant.respond(Math.toIntExact(payloadLong(effect, "userId")), Long.parseLong(effect.getEffectKey()));
            finish(effect);
        } catch (Exception error) {
            if (!stopping) {
                fail(effect, error, TodoConstant.RETRY_INITIAL_MS, TodoConstant.RETRY_MAX_MS);
            }
        }
    }

    /**
     * 执行一次研判
     */
    private void assess(TodoEffect effect) {
        long threadId = payloadLong(effect, "threadId");
        String evalId = effect.getEffectKey();
        try {
            TodoCandidate candidate = engine.candidate(threadId, evalId);
            if (candidate == null) {
                finish(effect);
                return;
            }
            Response<SupplierRuntime> runtime = settingsService.runtime(candidate.spaceId(), candidate.supplierId(), candidate.modelId());
            if (!runtime.isSuccess()) {
                engine.unavailable(threadId, evalId, TodoConstant.REASON_MODEL_UNAVAILABLE, runtime.getMsg());
                finish(effect);
                return;
            }
            Map<String, String> result = routerTool.assess(runtime.getData(), candidate.prompt(), candidate.text(), evalId,
                    event -> observation.append(threadId, evalId, null, null, null, event));
            engine.decide(threadId, evalId, result);
            finish(effect);
        } catch (Exception e) {
            if (stopping) {
                return;
            }
            // 连续失败达到阈值：记未完成，可在面板重新研判
            if (effect.getAttempts() + 1 >= TodoConstant.FAILURES_BEFORE_ERROR) {
                try {
                    engine.unavailable(threadId, evalId, TodoConstant.REASON_ASSESSMENT_FAILED, e.getMessage());
                    finish(effect);
                    return;
                } catch (Exception ex) {
                    logger.warn("超级助理记录研判未完成失败, evalId={}, err={}", evalId, ex.getMessage());
                }
            }
            fail(effect, e, TodoConstant.RETRY_INITIAL_MS, TodoConstant.RETRY_MAX_MS);
        }
    }

    /**
     * 派发启动或投递：槽位已满时不抢占，等槽位释放（不计失败）
     */
    private void dispatchRounds(String kind, int limit) {
        int free = limit - busySlots();
        if (free <= 0) {
            return;
        }
        for (TodoEffect effect : effectMapper.listDue(kind, free + runs.size(), new Date())) {
            if (free == 0) {
                break;
            }
            boolean launch = TodoConstant.EFFECT_LAUNCH.equals(kind);
            long ticketId = launch ? payloadLong(effect, "ticketId") : Long.parseLong(effect.getEffectKey());
            // 同一工单先等上一轮退出，再启动停止后的新派工
            if (runs.containsKey(ticketId) || !claim(effect)) {
                continue;
            }
            try {
                Long assignmentId = launch ? Long.parseLong(effect.getEffectKey()) : null;
                TodoRound round = engine.startRound(ticketId, assignmentId, launch);
                finish(effect);
                if (round != null) {
                    runRound(round);
                    free--;
                }
            } catch (Exception e) {
                fail(effect, e, TodoConstant.RETRY_INITIAL_MS, TodoConstant.RETRY_MAX_MS);
            }
        }
    }

    // ---------------------- Worker 执行 ----------------------

    /**
     * 在执行线程池跑一轮，超过单轮时限中断
     */
    private void runRound(TodoRound round) {
        RoundRun run = new RoundRun();
        runs.put(round.ticketId(), run);
        workerPool.execute(() -> {
            run.thread = Thread.currentThread();
            ScheduledFuture<?> limit = timer.schedule(() -> {
                run.timedOut.set(true);
                interrupt(run);
            }, TodoConstant.BACKGROUND_LIMIT_MINUTES, TimeUnit.MINUTES);
            try {
                execute(round, run);
            } finally {
                limit.cancel(false);
                runs.remove(round.ticketId(), run);
                run.thread = null;
                kick();
            }
        });
    }

    /**
     * 执行一轮：解析模型 → 拼装虚拟 Agent → 调用 Worker → 结束本轮
     */
    private void execute(TodoRound round, RoundRun run) {
        String reply = null;
        String error = null;
        try {
            Response<SupplierRuntime> runtime = settingsService.runtime(round.spaceId(), round.supplierId(), round.modelId());
            if (!runtime.isSuccess()) {
                error = "模型不可用：" + runtime.getMsg();
            } else {
                long threadId = ticketMapper.load(round.ticketId()).getThreadId();
                reply = workerTool.runRound(runtime.getData(), buildAgent(round), round.ticketId(), round.assignmentId(),
                        round.roleList(), round.contentList(), round.content(), engine,
                        event -> observation.append(threadId, null,
                                round.ticketId(), round.assignmentId(), round.roundId(), event));
            }
        } catch (Exception e) {
            // 人工停止与超时引起的中断不算失败
            if (!run.cancelled.get() && !run.timedOut.get()) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        // 清除中断标记，避免影响后续数据库操作
        Thread.interrupted();
        if (stopping) {
            return;
        }
        try {
            engine.endRound(round, reply, error, run.timedOut.get());
        } catch (Exception e) {
            logger.error("超级助理结束执行失败, ticketId={}, assignmentId={}, err={}", round.ticketId(), round.assignmentId(), e.getMessage(), e);
        }
    }

    /**
     * 拼装虚拟 Agent：不入库，仅供 harness 工具工厂装配 MCP 与 Skill
     */
    private Agent buildAgent(TodoRound round) {
        Agent agent = new Agent();
        agent.setId(TodoConstant.SUPERAGENT_AGENT_ID);
        agent.setSpaceId(round.spaceId());
        agent.setName("superagent");
        agent.setMcpIds(round.mcpIds());
        agent.setSkillIds(round.skillIds());
        agent.setSystemPrompt(round.systemPrompt());
        agent.setMemoryStatus(0);
        return agent;
    }

    // ---------------------- 健康 ----------------------

    @Override
    public List<String> problems() {
        List<String> list = new ArrayList<>();
        for (TodoEffect effect : effectMapper.listProblem(TodoConstant.FAILURES_BEFORE_ERROR)) {
            list.add(effect.getKind() + " #" + effect.getEffectKey() + "：" + TodoJsonTool.abbreviate(effect.getError(), 200));
        }
        if (lastTickError != null) {
            list.add("dispatch：" + TodoJsonTool.abbreviate(lastTickError, 200));
        }
        return list;
    }

    @Override
    public int busySlots() {
        int count = 0;
        for (TodoAssignment assignment : assignmentMapper.listByState(TodoConstant.ASSIGNMENT_RUNNING)) {
            if (TodoStageTool.busy(assignment)) {
                count++;
            }
        }
        return count;
    }

    // ---------------------- 自动修复（watchdog） ----------------------

    /**
     * 自动修复：扫描已停下、但属于可恢复中断的轮次，交给引擎自动重试
     */
    private void watch() {
        if (stopping) {
            return;
        }
        try {
            for (TodoAssignment assignment : assignmentMapper.listByState(TodoConstant.ASSIGNMENT_RUNNING)) {
                String reason = recoverableReason(assignment);
                if (reason == null) {
                    continue;
                }
                // 报告落库后先等待一个间隔，避免对同一错误立刻重跑
                Date reportTime = assignment.getReportTime();
                if (reportTime == null
                        || System.currentTimeMillis() - reportTime.getTime() < TodoConstant.WATCHDOG_RETRY_DELAY_MS) {
                    continue;
                }
                TodoTicket ticket = ticketMapper.load(assignment.getTicketId());
                if (ticket == null || ticket.getEndedTime() != null) {
                    continue;
                }
                // 状态与次数上限由引擎统一判断
                if (engine.autoRetry(ticket.getId(), recoverableLabel(reason), recoverableNote(reason))) {
                    logger.warn("超级助理 watchdog 自动重试, ticketId={}, assignmentId={}, reason={}",
                            ticket.getId(), assignment.getId(), reason);
                }
            }
        } catch (Exception e) {
            logger.warn("超级助理 watchdog 扫描失败, err={}", e.getMessage());
        }
    }

    /**
     * 可恢复中断分类：stream-流式连接中断、tool-调用了未注册工具、limit-工具调用次数超限
     */
    private String recoverableReason(TodoAssignment assignment) {
        String summary = assignment.getReportSummary();
        if (StringTool.isBlank(summary)) {
            return null;
        }
        String text = summary.toLowerCase();
        if (TodoConstant.REPORT_FAILED.equals(assignment.getReportState())) {
            if (text.contains("stream failed") || text.contains("timeout")
                    || text.contains("socket closed") || text.contains("connection reset")
                    || text.contains("502") || text.contains("503") || text.contains("504")) {
                return "stream";
            }
            if (text.contains("no toolcallback found")) {
                return "tool";
            }
            return null;
        }
        // 工具次数超限被截断：本轮以 waiting 收尾，但任务并未完成
        if (TodoConstant.REPORT_WAITING.equals(assignment.getReportState()) && text.contains("tool call limit")) {
            return "limit";
        }
        return null;
    }

    /**
     * 中断原因文案（通知用）
     */
    private String recoverableLabel(String reason) {
        return switch (reason) {
            case "stream" -> "流式连接中断";
            case "tool" -> "调用了未注册工具";
            default -> "工具调用次数超限";
        };
    }

    /**
     * 下一轮纠正提示（随新派工投递）
     */
    private String recoverableNote(String reason) {
        return switch (reason) {
            case "stream" -> "上一轮因模型流式连接中断（Stream failed/timeout）被终止，已自动重试。请从中断处继续完成未完成的工作。";
            case "tool" -> "上一轮调用了未注册的工具导致失败。只能使用已配置的 MCP / Skill 工具、Bash/Read/Write/Edit/Glob/Grep/ListDirectory 与 todo_* 上报工具，不要调用 shell、exec、terminal 等未注册名称。";
            default -> "上一轮因工具调用次数超限被截断。请减少工具调用：用一次 Write 写完文件，Bash 总数不超过 4 次，修改用 Edit。";
        };
    }

    // ---------------------- 副作用抢占与回执 ----------------------

    /**
     * 抢占副作用：版本一致且未被他人持有时成功
     */
    private boolean claim(TodoEffect effect) {
        // 时间统一取应用时钟（MySQL 与 H2 通用，不依赖数据库时间函数）
        long now = System.currentTimeMillis();
        // 助理回复可能持续至模型的流式超时，较长租期减少跨实例重复生成
        long lease = TodoConstant.EFFECT_ASSISTANT.equals(effect.getKind()) ? 600 : TodoConstant.EFFECT_LEASE_SECONDS;
        Date ownerUntil = new Date(now + lease * 1000L);
        return effectMapper.claim(effect.getId(), effect.getGeneration(), owner, ownerUntil, new Date(now)) == 1;
    }

    /**
     * 执行成功：删除同一版本；执行期间被合并了新参数时保留并释放抢占，等下次执行
     */
    private void finish(TodoEffect effect) {
        effectMapper.finish(effect.getId(), effect.getGeneration());
        effectMapper.release(effect.getId(), owner);
    }

    /**
     * 执行失败：指数退避后重试
     */
    private void fail(TodoEffect effect, Exception e, long initialMs, long maxMs) {
        int attempts = effect.getAttempts() + 1;
        long delay = Math.min(maxMs, initialMs * (1L << Math.min(attempts - 1, 20)));
        String error = TodoJsonTool.abbreviate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 900);
        try {
            effectMapper.fail(effect.getId(), effect.getGeneration(), attempts, error, new Date(System.currentTimeMillis() + delay));
            effectMapper.release(effect.getId(), owner);
        } catch (Exception ex) {
            logger.warn("超级助理记录副作用失败出错, kind={}, key={}, err={}", effect.getKind(), effect.getEffectKey(), ex.getMessage());
        }
        logger.warn("超级助理副作用执行失败, kind={}, key={}, attempts={}, err={}", effect.getKind(), effect.getEffectKey(), attempts, error);
        // 副作用成功即删除，重试信息另以事件保留
        try {
            Long ticketId = null;
            Long assignmentId = null;
            long threadId;
            String evalId = null;
            if (TodoConstant.EFFECT_ASSESSMENT.equals(effect.getKind())) {
                threadId = payloadLong(effect, "threadId");
                evalId = effect.getEffectKey();
            } else if (TodoConstant.EFFECT_LAUNCH.equals(effect.getKind()) || TodoConstant.EFFECT_DELIVERY.equals(effect.getKind())) {
                ticketId = TodoConstant.EFFECT_LAUNCH.equals(effect.getKind())
                        ? payloadLong(effect, "ticketId") : Long.parseLong(effect.getEffectKey());
                assignmentId = TodoConstant.EFFECT_LAUNCH.equals(effect.getKind()) ? Long.parseLong(effect.getEffectKey()) : null;
                threadId = ticketMapper.load(ticketId).getThreadId();
            } else {
                return;
            }
            observation.append(threadId, evalId, ticketId, assignmentId, null,
                    new SuperagentTraceEvent("effect.retry", "queued", effect.getKind(),
                            TodoJsonTool.toJson(Map.of("attempt", attempts, "nextRetryAt", System.currentTimeMillis() + delay,
                                    "error", error)), null, null));
        } catch (RuntimeException traceError) {
            logger.warn("超级助理重试事件记录失败", traceError);
        }
    }

    // ---------------------- 工具 ----------------------

    /**
     * 读取副作用参数中的数字（Gson 解析为 Double）
     */
    private long payloadLong(TodoEffect effect, String key) {
        Map<?, ?> payload = TodoJsonTool.fromJson(effect.getPayload(), Map.class);
        Object value = payload == null ? null : payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("副作用参数缺失：" + key);
    }

    private static void interrupt(RoundRun run) {
        Thread thread = run.thread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

}
