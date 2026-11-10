<!--
  TodoTimeline（超级助理时间线）
  只负责渲染一条待办“怎么走到这里”：工单卡片 + 提交、研判、报告、消息按时间排列；操作通过事件交给页面
-->
<template>
  <div class="todo-timeline">
    <!-- 工单 -->
    <div class="todo-timeline__section">{{ t('business.todo.tickets') }}</div>
    <div v-if="!detail.tickets.length" class="todo-timeline__empty">{{ t('business.todo.noTicket') }}</div>
    <el-card v-for="ticket in detail.tickets" :key="ticket.id" shadow="never" class="todo-timeline__ticket">
      <div class="todo-timeline__ticket-head">
        <el-tag :type="stageType(ticket.stage)" size="small">{{ stageLabel(ticket.stage) }}</el-tag>
        <span class="todo-timeline__ticket-title">#{{ ticket.id }} {{ ticket.title }}</span>
        <span v-if="ticket.endedOutcome" class="todo-timeline__muted">{{ outcomeLabel(ticket.endedOutcome) }}</span>
        <span class="todo-timeline__spacer" />
        <el-button v-if="ticket.canRetry" link type="primary" @click="emit('retry', ticket.id)">{{ t('business.todo.retry') }}</el-button>
        <el-button v-if="ticket.stage !== 'ended'" link type="danger" @click="emit('stop', ticket)">{{ t('business.todo.stop') }}</el-button>
      </div>
      <div v-if="ticket.scope" class="todo-timeline__text">{{ t('business.todo.scope') }}：{{ ticket.scope }}</div>
      <div v-if="ticket.question" class="todo-timeline__question">{{ t('business.todo.question') }}：{{ ticket.question }}</div>
    </el-card>

    <!-- 经过 -->
    <div class="todo-timeline__section">{{ t('business.todo.timeline') }}</div>
    <el-timeline>
      <el-timeline-item v-for="item in items" :key="item.key" :timestamp="item.time" :type="item.type" placement="top">
        <div class="todo-timeline__item-head">
          <span>{{ item.title }}</span>
          <el-button v-if="item.triggerId" link type="primary" size="small" @click="emit('requeue', item.triggerId)">
            {{ t('business.todo.requeue') }}
          </el-button>
          <el-button v-if="item.decisionId" link type="primary" size="small" @click="emit('dismiss', item.decisionId)">
            {{ t('business.todo.dismiss') }}
          </el-button>
        </div>
        <!-- 纯文本展示（不渲染 HTML），换行保留 -->
        <div v-if="item.content" class="todo-timeline__text">{{ item.content }}</div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'TodoTimeline' })
import { t } from '@/i18n'
import { computed } from 'vue'
import {
  assignmentStateLabel,
  decisionLabel,
  endedReasonLabel,
  outcomeLabel,
  purposeLabel,
  reasonDisplay,
  reportLabel,
  stageLabel,
  stageType,
  triggerKindLabel,
  type TagType
} from '../composables/useTodoLabel'
import type { TodoThreadDetail, TodoTicket } from '../types'

const props = defineProps<{ detail: TodoThreadDetail }>()
const emit = defineEmits<{
  stop: [ticket: TodoTicket]
  retry: [ticketId: number]
  requeue: [triggerId: number]
  dismiss: [decisionId: number]
}>()

/** 时间线条目 */
interface TimelineItem {
  key: string
  time: string
  title: string
  content: string
  type: TagType
  /** 可重新研判的提交 */
  triggerId?: number
  /** 可忽略的未完成研判 */
  decisionId?: number
}

/** 合并提交、研判、派工报告、消息，按时间正序（时间格式 yyyy-MM-dd HH:mm:ss 可直接按字符串比较） */
const items = computed<TimelineItem[]>(() => {
  const list: TimelineItem[] = []
  const { triggers, decisions, tickets, messages } = props.detail
  for (const trigger of triggers) {
    const handbacks = trigger.handbacks.length ? `\n${t('business.todo.handback')}：${trigger.handbacks.join('；')}` : ''
    list.push({
      key: `trigger-${trigger.id}`,
      time: trigger.eventTime,
      title: `${triggerKindLabel(trigger.kind)} #${trigger.id}`,
      content: trigger.content + handbacks,
      type: 'primary',
      triggerId: trigger.id
    })
  }
  for (const decision of decisions) {
    const scope = decision.scope ? `${t('business.todo.scope')}：${decision.scope}` : ''
    const unavailable = decision.kind === 'unavailable'
    list.push({
      key: `decision-${decision.id}`,
      time: decision.eventTime,
      title: `${t('business.todo.decisionTitle')}：${decisionLabel(decision.kind)} · ${strengthText(decision.strength)}`,
      content: [reasonDisplay(decision.reasonCode, decision.reasonText), scope].filter(Boolean).join('\n'),
      type: decision.kind === 'accept' ? 'success' : unavailable ? 'danger' : 'info',
      decisionId: unavailable && !decision.dismissed ? decision.id : undefined
    })
  }
  for (const ticket of tickets) {
    for (const assignment of ticket.assignments) {
      if (assignment.reportTime && assignment.reportState) {
        list.push({
          key: `report-${assignment.id}`,
          time: assignment.reportTime,
          title: `${t('business.todo.assignment', [assignment.id])} · ${reportLabel(assignment.reportState)}`,
          content: assignment.reportSummary || '',
          type: ['blocked', 'failed'].includes(assignment.reportState) ? 'danger' : 'primary'
        })
      }
      if (assignment.endedTime) {
        list.push({
          key: `ended-${assignment.id}`,
          time: assignment.endedTime,
          title: `${t('business.todo.assignment', [assignment.id])} · ${assignmentStateLabel(assignment.state)}`,
          content: endedReasonLabel(assignment.endedReason),
          type: 'info'
        })
      }
    }
  }
  for (const message of messages) {
    list.push({
      key: `message-${message.id}`,
      time: message.addTime,
      title: purposeLabel(message.purpose),
      content: message.content,
      type: message.purpose === 'attention' ? 'warning' : 'success'
    })
  }
  return list.sort((a, b) => (a.time === b.time ? 0 : a.time < b.time ? -1 : 1))
})

/** 强度文案 */
function strengthText(strength: string): string {
  return strength === 'inferred' ? t('business.todo.strength.inferred') : t('business.todo.strength.explicit')
}
</script>

<style scoped lang="scss">
.todo-timeline__section {
  margin: 12px 0 8px;
  font-weight: 600;
}
.todo-timeline__empty,
.todo-timeline__muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.todo-timeline__ticket {
  margin-bottom: 8px;
}
.todo-timeline__ticket-head,
.todo-timeline__item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.todo-timeline__ticket-title {
  font-weight: 500;
}
.todo-timeline__spacer {
  flex: 1;
}
.todo-timeline__text {
  margin-top: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.todo-timeline__question {
  margin-top: 4px;
  color: var(--el-color-warning);
  font-size: 13px;
}
</style>
