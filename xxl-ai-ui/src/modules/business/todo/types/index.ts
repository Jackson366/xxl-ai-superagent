/**
 * 超级助理 类型定义
 */
import type { ListQuery } from '@/types'

/** 待办列表行（对应后端 TodoThreadDTO） */
export interface TodoThread {
  id: number
  /** 标题 */
  title: string
  /** 阶段编码：delivery/answer/blocked/failed/queued/idle/working/ended/evaluating/unavailable/ignored */
  stage: string
  /** 当前工单ID */
  ticketId?: number
  /** 待你回答的问题 */
  question?: string
  /** 提问ID（回答时携带） */
  attentionId?: string
  /** 最新报告摘要 */
  summary?: string
  /** 无工单时的最新决定原因编码 */
  reasonCode?: string
  /** 原因补充文本 */
  reasonText?: string
  lastTime?: string
  addTime?: string
}

/** 触发与去向 */
export interface TodoTrigger {
  id: number
  /** 类型：create/append/answer */
  kind: string
  content: string
  evalId?: string
  decisionId?: number
  /** 送达的工单ID */
  fanout: number[]
  /** 已确认接手的工单ID */
  acked: number[]
  /** 退回记录 */
  handbacks: string[]
  /** 人工重新研判过 */
  requeued: boolean
  eventTime: string
}

/** 研判决定 */
export interface TodoDecision {
  id: number
  evalId: string
  /** 决定：accept/stop/ignore/unavailable */
  kind: string
  /** 强度：explicit/inferred */
  strength: string
  triggerIds: number[]
  reasonCode?: string
  reasonText?: string
  ticketId?: number
  /** 授权范围 */
  scope?: string
  /** 观察模式：会接单但未执行 */
  routerDecision?: string
  routerScope?: string
  wouldAccept: boolean
  /** 未完成问题已忽略：0-否、1-是 */
  dismissed: number
  policyVersion: string
  eventTime: string
}

/** 派工 */
export interface TodoAssignment {
  id: number
  /** 状态：pending/running/ended */
  state: string
  reportState?: string
  reportSummary?: string
  reportTime?: string
  startedTime?: string
  endedTime?: string
  endedReason?: string
}

/** 工单 */
export interface TodoTicket {
  id: number
  title: string
  stage: string
  scope?: string
  strength?: string
  policyVersion?: string
  question?: string
  attentionId?: string
  endedBy?: string
  endedOutcome?: string
  endedReason?: string
  endedTime?: string
  /** 可重试 */
  canRetry: boolean
  addTime: string
  assignments: TodoAssignment[]
}

/** 收件箱消息 */
export interface TodoMessage {
  id: number
  ticketId?: number
  threadId?: number
  ticketTitle?: string
  /** 用途：acknowledgement/answer/attention/result/notice */
  purpose: string
  /** 消息内容（Markdown，按纯文本展示） */
  content: string
  /** 已读：0-未读、1-已读 */
  readStatus: number
  addTime: string
}

/** 待办详情（时间线） */
export interface TodoThreadDetail {
  thread: TodoThread
  triggers: TodoTrigger[]
  decisions: TodoDecision[]
  tickets: TodoTicket[]
  messages: TodoMessage[]
}

/** 概览：要我吗、健康吗 */
export interface TodoOverview {
  workers?: TodoWorker[]
  needMe: TodoThread[]
  problems: string[]
  /** 未就绪原因编码，就绪为空 */
  readyCode?: string
  paused: boolean
  busySlots: number
  maxWorkers: number
  unread: number
}

/** 提交入参（requestId 由 useSubmit 生成） */
export interface TodoSubmit {
  requestId: string
  threadId?: number
  /** 类型：create-新建、append-追加、answer-回答 */
  kind: 'create' | 'append' | 'answer'
  content: string
  attentionId?: string
}

/** 待办列表查询参数 */
export interface TodoQuery {
  pageNum: number
  pageSize: number
}

/** 待办列表接口请求参数 */
export type TodoListQuery = ListQuery<TodoQuery>

/** 收件箱查询参数 */
export interface TodoMessageQuery {
  pageNum: number
  pageSize: number
  /** -1-全部、0-未读 */
  readStatus: number
}

/** 收件箱接口请求参数 */
export type TodoMessageListQuery = ListQuery<TodoMessageQuery>

/** 配置块 */
export type TodoSettingsBlock = 'model' | 'worker' | 'policy' | 'prompts'

/** 配置版本 */
export interface TodoSettings<T = unknown> {
  block: TodoSettingsBlock
  /** 版本号，0 表示从未保存 */
  version: number
  content: T
  /** 默认值（提示词块） */
  defaults?: T
  userId?: number
  addTime?: string
}

/** 模型块 */
export interface TodoModelSettings {
  spaceId: number
  routerSupplierId: number
  routerModelId: number
  workerSupplierId: number
  workerModelId: number
}

/** 执行块 */
export interface TodoWorkerSettings {
  mcpIds: number[]
  skillIds: number[]
  maxWorkers: number
  paused: boolean
}

/** 接单块 */
export interface TodoPolicySettings {
  mode: 'off' | 'observe' | 'auto'
}

/** 提示词块 */
export interface TodoPromptsSettings {
  router?: string
  worker?: string
}

/** 下拉选项 */
export interface TodoOption {
  id: number
  name: string
  /** 供应商下的对话模型 */
  models?: TodoOption[]
}

/** 设置页下拉 */
export interface TodoOptions {
  spaces: TodoOption[]
  suppliers?: TodoOption[]
  mcps?: TodoOption[]
  skills?: TodoOption[]
}

/** 持久化执行事件；ID 同时作为断线续传游标 */
export interface TodoEvent {
  id: number
  threadId: number
  evalId?: string
  ticketId?: number
  assignmentId?: number
  roundId?: string
  callId?: string
  kind: string
  status: string
  summary?: string
  detail?: string
  durationMs?: number
  eventTime: string
}

/** 当前用户的活动派工 */
export interface TodoWorker {
  threadId: number
  ticketId: number
  assignmentId: number
  title: string
  state: string
  reportState?: string
  reportSummary?: string
  startedTime?: string
  idleTime?: string
  deliveredTime?: string
  lastEventTime?: string
  lastKind?: string
  lastStatus?: string
  lastSummary?: string
}

/** 超级助理的单条消息及其后续结果 */
export interface TodoAssistantTurn {
  id: number
  content: string
  reply?: string
  state: 'pending' | 'answered' | 'proposed' | 'submitted' | 'failed' | 'replaced'
  referenceId?: number
  referenceTitle?: string
  targetTicketId?: number
  attentionId?: string
  proposal?: string
  errorCode?: string
  threadId?: number
}

/** 对话中需要用户处理的待办消息 */
export interface TodoAssistantNotice {
  id: string
  threadId: number
  ticketId: number
  title: string
  stage: 'answer' | 'failed' | 'delivery'
  content?: string
  attentionId?: string
  canRetry: boolean
}
