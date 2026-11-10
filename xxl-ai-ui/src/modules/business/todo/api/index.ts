/**
 * 超级助理 接口封装
 */
import { request } from '@/utils/request'
import type { Response, PageModel } from '@/types'
import type {
  TodoListQuery,
  TodoMessage,
  TodoMessageListQuery,
  TodoOptions,
  TodoOverview,
  TodoSettings,
  TodoSettingsBlock,
  TodoSubmit,
  TodoThread,
  TodoThreadDetail
} from '../types'

/** 分页查询待办 */
export function listTodo(query: TodoListQuery): Promise<Response<PageModel<TodoThread>>> {
  return request({ url: '/todo/thread/pageList', method: 'get', params: query })
}

/** 待办详情（时间线） */
export function getTodo(id: number): Promise<Response<TodoThreadDetail>> {
  return request({ url: '/todo/thread/load', method: 'get', params: { id } })
}

/** 提交：新建、追加、回答（requestId 幂等，返回待办ID） */
export function submitTodo(data: TodoSubmit): Promise<Response<number>> {
  return request({ url: '/todo/thread/submit', method: 'post', data: data })
}

/** 重新研判一条提交 */
export function requeueTrigger(triggerId: number): Promise<Response<string>> {
  return request({ url: '/todo/thread/requeue', method: 'post', params: { triggerId } })
}

/** 忽略未完成的研判 */
export function dismissDecision(id: number): Promise<Response<string>> {
  return request({ url: '/todo/decision/dismiss', method: 'post', params: { id } })
}

/** 停止工单 */
export function stopTicket(id: number): Promise<Response<string>> {
  return request({ url: '/todo/ticket/stop', method: 'post', params: { id } })
}

/** 重试工单 */
export function retryTicket(id: number): Promise<Response<string>> {
  return request({ url: '/todo/ticket/retry', method: 'post', params: { id } })
}

/** 收件箱分页 */
export function listMessage(query: TodoMessageListQuery): Promise<Response<PageModel<TodoMessage>>> {
  return request({ url: '/todo/message/pageList', method: 'get', params: query })
}

/** 标记已读 */
export function readMessage(ids: number[]): Promise<Response<string>> {
  return request({ url: '/todo/message/read', method: 'post', data: ids })
}

/** 概览：要我吗、健康吗 */
export function getOverview(): Promise<Response<TodoOverview>> {
  return request({ url: '/todo/overview', method: 'get' })
}

/** 读取配置块当前版本 */
export function getSettings<T>(block: TodoSettingsBlock): Promise<Response<TodoSettings<T>>> {
  return request({ url: '/todo/settings/load', method: 'get', params: { block } })
}

/** 保存配置块（baseVersion 不一致时后端拒绝） */
export function saveSettings(data: { block: TodoSettingsBlock; baseVersion: number; content: unknown }): Promise<Response<string>> {
  return request({ url: '/todo/settings/save', method: 'post', data: data })
}

/** 配置块历史版本 */
export function listSettingsHistory(block: TodoSettingsBlock): Promise<Response<TodoSettings[]>> {
  return request({ url: '/todo/settings/history', method: 'get', params: { block } })
}

/** 设置页下拉：超级助理空间；传 spaceId 时附带该空间的模型、MCP、Skill */
export function getSettingsOptions(spaceId?: number): Promise<Response<TodoOptions>> {
  return request({ url: '/todo/settings/options', method: 'get', params: { spaceId } })
}
