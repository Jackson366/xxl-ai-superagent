package com.xxl.ai.api.business.todo.model.dto;

/**
 * 超级助理 研判候选（引擎在事务内组装，调度器在事务外交给 Router）
 *
 * @param policyVersion 研判开始时的策略版本
 * @param prompt        Router 系统提示词
 * @param spaceId       超级助理空间ID
 * @param supplierId    Router 供应商ID
 * @param modelId       Router 模型ID
 * @param text          候选卡片文本
 * @author xxl-ai 2026-11-10
 */
public record TodoCandidate(String policyVersion,
                            String prompt,
                            long spaceId,
                            long supplierId,
                            long modelId,
                            String text) {
}
