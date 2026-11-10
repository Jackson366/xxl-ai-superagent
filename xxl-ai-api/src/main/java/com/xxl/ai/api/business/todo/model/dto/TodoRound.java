package com.xxl.ai.api.business.todo.model.dto;

import java.util.Date;
import java.util.List;

/**
 * 超级助理 Worker 一轮执行的输入（引擎在事务内组装，调度器在事务外执行）
 *
 * @param ticketId     工单ID
 * @param assignmentId 派工ID
 * @param launch       是否为启动（false 为投递追问）
 * @param spaceId      超级助理空间ID（模型快照）
 * @param supplierId   供应商ID（模型快照）
 * @param modelId      模型ID（模型快照）
 * @param mcpIds       可用 MCP（逗号分隔，工具快照）
 * @param skillIds     可用 Skill（逗号分隔，工具快照）
 * @param systemPrompt Worker 系统提示词（含本工单授权范围）
 * @param roleList     历史消息角色
 * @param contentList  历史消息内容
 * @param content      本轮输入
 * @param inputIds     本轮带上的输入ID
 * @param startedAt    本轮开始时间
 * @author xxl-ai 2026-11-10
 */
public record TodoRound(long ticketId,
                        long assignmentId,
                        boolean launch,
                        long spaceId,
                        long supplierId,
                        long modelId,
                        String mcpIds,
                        String skillIds,
                        String systemPrompt,
                        List<String> roleList,
                        List<String> contentList,
                        String content,
                        List<Long> inputIds,
                        Date startedAt) {
}
