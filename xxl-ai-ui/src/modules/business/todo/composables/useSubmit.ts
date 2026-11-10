/**
 * useSubmit - 超级助理提交（幂等）
 *
 * 每次提交带 requestId：用相同内容重试时复用同一个，服务端只受理一次；内容变化或提交成功后换新标识。
 */
import { ref } from 'vue'
import { submitTodo } from '../api'
import type { TodoSubmit } from '../types'

/** 生成请求标识：非安全上下文（HTTP 且非 localhost）没有 crypto.randomUUID，退回时间戳 + 随机数 */
function newRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

export function useSubmit() {
  const loading = ref(false) /* 提交中 */
  let requestId = newRequestId() /* 当前请求标识 */
  let lastPayload = '' /* 上次提交的内容，用于判断是否为重试 */

  /** 提交，返回待办ID；失败时抛出，由调用方决定是否重试 */
  async function send(data: Omit<TodoSubmit, 'requestId'>): Promise<number> {
    const payload = JSON.stringify(data)
    // 内容变化即新的请求
    if (payload !== lastPayload) {
      requestId = newRequestId()
      lastPayload = payload
    }
    loading.value = true
    try {
      const res = await submitTodo({ ...data, requestId })
      lastPayload = ''
      return res.data
    } finally {
      loading.value = false
    }
  }

  return { loading, send }
}
