/**
 * useTodoLabel - 超级助理编码 → 文案（阶段、原因、决定、报告等）
 *
 * 编码由后端给出，文案统一在 business.todo.* 下维护；未知编码回退显示原编码。
 */
import { t, type MessageKey } from '@/i18n'

/** 标签颜色 */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 需要人处理的阶段（面板“要我吗”） */
const PERSON_STAGES = ['delivery', 'answer', 'blocked', 'failed', 'unavailable']

/** 是否需要人处理 */
export function needPerson(stage?: string): boolean {
  return !!stage && PERSON_STAGES.includes(stage)
}

/** 阶段标签颜色 */
export function stageType(stage?: string): TagType {
  if (needPerson(stage)) {
    return stage === 'answer' ? 'warning' : 'danger'
  }
  if (stage === 'working') return 'primary'
  if (stage === 'ended') return 'success'
  return 'info'
}

/** 编码转文案：键不存在时 t() 返回键本身，此时回退原编码 */
function label(prefix: string, code?: string | null): string {
  if (!code) return ''
  const key = `${prefix}.${code}` as MessageKey
  const text = t(key)
  return text === key ? code : text
}

export const stageLabel = (code?: string | null) => label('business.todo.stage', code)
export const reasonLabel = (code?: string | null) => label('business.todo.reason', code)
export const decisionLabel = (code?: string | null) => label('business.todo.decision', code)
export const strengthLabel = (code?: string | null) => label('business.todo.strength', code)
export const reportLabel = (code?: string | null) => label('business.todo.report', code)
export const assignmentStateLabel = (code?: string | null) => label('business.todo.assignmentState', code)
export const endedReasonLabel = (code?: string | null) => label('business.todo.endedReason', code)
export const outcomeLabel = (code?: string | null) => label('business.todo.outcome', code)
export const purposeLabel = (code?: string | null) => label('business.todo.purpose', code)
export const triggerKindLabel = (code?: string | null) => label('business.todo.triggerKind', code)

/** 原因展示：模型原话优先，其次按编码取文案 */
export function reasonDisplay(code?: string | null, text?: string | null): string {
  if (text) return text
  return code === 'said' ? '' : reasonLabel(code)
}
