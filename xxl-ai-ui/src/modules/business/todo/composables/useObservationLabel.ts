/**
 * 运行观测文案和状态映射
 *
 * @author Codex 2026-11-11
 */
import { t, type MessageKey } from '@/i18n'
import type { TodoEvent, TodoWorker } from '../types'
import { endedReasonLabel, type TagType } from './useTodoLabel'

export function eventLabel(kind?: string) {
  if (!kind) return t('business.todo.observe.unknown')
  const key = `business.todo.observe.events.${kind.replaceAll('.', '_')}` as MessageKey
  const text = t(key)
  return text === key ? kind : text
}
export function statusLabel(status: string) {
  const key = `business.todo.observe.status.${status}` as MessageKey
  const text = t(key)
  return text === key ? status : text
}
export function eventType(status: string): TagType {
  if (['failed', 'timeout', 'unavailable'].includes(status)) return 'danger'
  if (['blocked', 'waiting', 'cancelled'].includes(status)) return 'warning'
  if (['success', 'ready', 'accept'].includes(status)) return 'success'
  return status === 'running' ? 'primary' : 'info'
}
export function eventIcon(status: string) {
  if (['failed', 'timeout', 'unavailable'].includes(status)) return 'CircleClose'
  if (['blocked', 'waiting', 'queued'].includes(status)) return 'Clock'
  if (status === 'running') return 'Loading'
  return 'CircleCheck'
}
export function elapsed(ms?: number) {
  if (ms == null) return '—'
  return t('business.todo.observe.seconds', [(ms / 1000).toFixed(ms < 10000 ? 1 : 0)])
}
export function workerActivity(worker: TodoWorker) {
  if (worker.state === 'pending') return t('business.todo.observe.waitingSlot')
  const idle = worker.idleTime && worker.deliveredTime && worker.idleTime >= worker.deliveredTime
  if (idle) return statusLabel(worker.reportState || 'waiting')
  return eventLabel(worker.lastKind)
}
export function visibleSummary(event: TodoEvent) {
  if (event.kind === 'assignment.ended') return endedReasonLabel(event.summary)
  if (event.kind.startsWith('tool.') || event.kind === 'worker.reported' || event.kind === 'ticket.created') return event.summary
  return undefined
}
