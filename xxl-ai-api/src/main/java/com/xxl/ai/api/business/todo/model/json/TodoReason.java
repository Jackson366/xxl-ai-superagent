package com.xxl.ai.api.business.todo.model.json;

/**
 * 决定原因（xxl_ai_todo_decision.reason 列数组元素）：前端按 code 取文案，code=said 时展示 text
 *
 * @author xxl-ai 2026-11-10
 */
public class TodoReason {

    public String code;        /* 原因编码，见 TodoConstant.REASON_* */
    public String text;        /* 补充文本：模型原话或错误信息 */
    public Long triggerId;     /* 针对的触发ID，为空表示整批 */

    public TodoReason() {
    }

    public TodoReason(String code, String text) {
        this.code = code;
        this.text = text;
    }

}
