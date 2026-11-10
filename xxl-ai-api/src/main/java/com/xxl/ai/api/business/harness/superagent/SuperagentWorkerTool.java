package com.xxl.ai.api.business.harness.superagent;

import com.google.gson.Gson;
import com.xxl.ai.api.business.agent.model.entity.Agent;
import com.xxl.ai.api.business.harness.llm.LlmChatTool;
import com.xxl.ai.api.business.harness.mcp.McpToolFactory;
import com.xxl.ai.api.business.harness.skill.SkillShellTracker;
import com.xxl.ai.api.business.harness.skill.SkillToolFactory;
import com.xxl.ai.api.business.supplier.model.SupplierRuntime;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 超级助理 Worker：执行工单的一轮工作
 *
 * 工具装配 = 内置上报工具（todo_read / todo_ack / todo_report / todo_handback）+ 管理员勾选的 MCP 与 Skill。
 * Agent 由 business 按 superagent 配置拼装后传入（虚拟 AgentID，不入库），本类不读写任何 business 表；
 * 上报经 {@link WorkerReportHandler} 回调落库。Shell 进程经 tracker 执行，本轮结束统一回收。
 *
 * @author xxl-ai 2026-11-10
 */
@Component
public class SuperagentWorkerTool {

    private static final Logger logger = LoggerFactory.getLogger(SuperagentWorkerTool.class);
    private static final Gson GSON = new Gson();

    @Resource
    private LlmChatTool llmChatTool;
    @Resource
    private McpToolFactory mcpToolFactory;
    @Resource
    private SkillToolFactory skillToolFactory;

    /**
     * 执行一轮工作
     *
     * @param runtime      Worker 模型运行时配置（工单首次建会话时的快照）
     * @param agent        拼装的虚拟 Agent：id、spaceId、mcpIds、skillIds、systemPrompt
     * @param ticketId     工单ID
     * @param assignmentId 派工ID
     * @param roleList     历史消息角色（user/assistant）
     * @param contentList  历史消息内容
     * @param content      本轮输入（未读的触发、输入、恢复说明）
     * @param handler      上报回调
     * @return 本轮模型回复
     */
    public String runRound(SupplierRuntime runtime, Agent agent, long ticketId, long assignmentId,
                           List<String> roleList, List<String> contentList, String content,
                           WorkerReportHandler handler, Consumer<SuperagentTraceEvent> observer) {
        long start = System.nanoTime();
        AtomicBoolean responded = new AtomicBoolean();
        SuperagentTraceTool.emit(observer, new SuperagentTraceEvent("model.started", "running", null, null, null, null));
        SkillShellTracker tracker = skillToolFactory.newShellTracker(agent.getId());
        try {
            Object[] tools = buildTools(agent, tracker, ticketId, assignmentId, handler);
            LlmChatTool.ChatText result = llmChatTool.chat(runtime, agent.getSystemPrompt(), roleList, contentList,
                    content, SuperagentTraceTool.wrap(tools, observer), null, "todo-ticket-" + ticketId, null, chunk -> {
                        if (responded.compareAndSet(false, true)) {
                            SuperagentTraceTool.emit(observer, new SuperagentTraceEvent("model.responding", "running", null, null, null, null));
                        }
                    });
            SuperagentTraceTool.emit(observer, new SuperagentTraceEvent("model.finished", "success", null, result.getContent(), null,
                    (System.nanoTime() - start) / 1000000));
            logger.info("超级助理 Worker 本轮结束, ticketId={}, assignmentId={}", ticketId, assignmentId);
            return result.getContent();
        } catch (RuntimeException error) {
            SuperagentTraceTool.emit(observer, new SuperagentTraceEvent("model.failed", "failed", null, error.getMessage(), null,
                    (System.nanoTime() - start) / 1000000));
            throw error;
        } finally {
            // 本轮结束回收 Shell 进程，避免占用槽位外的资源
            tracker.cancel();
        }
    }

    /**
     * 工具装配：内置上报工具 + MCP 工具 + Skill 工具（无技能时不注册）+ 终端/文件执行工具
     */
    private Object[] buildTools(Agent agent, SkillShellTracker tracker, long ticketId, long assignmentId,
                                WorkerReportHandler handler) {
        List<Object> tools = new ArrayList<>(buildReportTools(ticketId, assignmentId, handler));
        tools.addAll(mcpToolFactory.buildTools(agent));
        Object skillTool = skillToolFactory.buildTool(agent);
        if (skillTool != null) {
            tools.add(skillTool);
        }
        tools.addAll(skillToolFactory.buildExecutorTools(agent, tracker));
        return tools.toArray();
    }

    /**
     * 内置上报工具：名称固定，参数 Schema 固定
     */
    private List<ToolCallback> buildReportTools(long ticketId, long assignmentId, WorkerReportHandler handler) {
        List<ToolCallback> list = new ArrayList<>();
        list.add(callback("todo_read",
                "读取本工单的资源：triggers（触发与去向）、inputs（送达的输入）、reports（历史报告）。按 next_offset 翻页。",
                """
                {"type":"object","properties":{"resource":{"type":"string","enum":["triggers","inputs","reports"]},"offset":{"type":"integer"},"limit":{"type":"integer"}},"required":["resource"]}""",
                args -> handler.read(ticketId, args)));
        list.add(callback("todo_ack",
                "开始实质工作前确认一条人工请求，确认会出现在提交人的收件箱。每条请求只确认一次。",
                """
                {"type":"object","properties":{"trigger_id":{"type":"string"},"text":{"type":"string","description":"一句话说明将要做什么"}},"required":["trigger_id","text"]}""",
                args -> handler.ack(ticketId, args)));
        list.add(callback("todo_report",
                "上报进展或结果。ready：结果已就绪；blocked：需要人处理才能继续；failed：执行失败；dropped：无需处理；progress：阶段进展。需要向人提问时填 human_action。",
                """
                {"type":"object","properties":{"state":{"type":"string","enum":["progress","ready","blocked","failed","dropped"]},"summary":{"type":"string"},"human_action":{"type":"string","description":"一个具体问题，提问会等待人回答"},"reply_to_trigger_id":{"type":"string","description":"本报告回答的人工提问"}},"required":["state","summary"]}""",
                args -> handler.report(ticketId, assignmentId, args)));
        list.add(callback("todo_handback",
                "把要求超出本工单范围的追问退回重新研判，每条只能退回一次。",
                """
                {"type":"object","properties":{"trigger_id":{"type":"string"},"reason":{"type":"string","description":"一句话说明超出了什么"}},"required":["trigger_id","reason"]}""",
                args -> handler.handback(ticketId, args)));
        return list;
    }

    /**
     * 构建单个工具回调：回调异常转为工具结果文本，避免中断整轮对话
     */
    private ToolCallback callback(String name, String description, String schema,
                                  Function<Map<String, Object>, String> function) {
        Function<Map<String, Object>, String> safe = args -> {
            try {
                return function.apply(args);
            } catch (Exception e) {
                logger.warn("超级助理上报工具执行失败, tool={}, err={}", name, e.getMessage());
                return GSON.toJson(Map.of("error", String.valueOf(e.getMessage())));
            }
        };
        return FunctionToolCallback.builder(name, safe)
                .description(description)
                .inputType(Map.class)
                .inputSchema(schema.trim())
                .build();
    }

}
