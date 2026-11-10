<!-- 运行观测：实时事件、关联筛选和只读回放；@author Codex 2026-11-11 -->
<template>
  <section class="observation">
    <div class="observation__toolbar">
      <div class="observation__connection" role="status">
        <el-icon><component :is="connection === 'live' ? 'Connection' : 'Warning'" /></el-icon><span>{{ connectionLabel }}</span>
      </div>
      <el-button v-if="connection === 'reconnecting'" link @click="reconnect">{{ t('common.refresh') }}</el-button>
      <span class="observation__spacer" />
      <el-radio-group v-model="mode" size="small" :aria-label="t('business.todo.observe.viewMode')">
        <el-radio-button value="live">{{ t('business.todo.observe.live') }}</el-radio-button>
        <el-radio-button value="replay">{{ t('business.todo.observe.replay') }}</el-radio-button>
      </el-radio-group>
    </div>
    <template v-if="mode === 'live'">
      <TodoFlow :detail="detail" :events="events" @select="selection = $event" />
      <div v-if="activeCalls.length" class="observation__running" role="status">
        <el-icon><Loading /></el-icon>
        <div>
          <strong>{{ t('business.todo.observe.executingTools', [activeCalls.length]) }}</strong>
          <p v-for="call in activeCalls" :key="call.id">
            {{ call.summary }} · {{ elapsed(now - eventTimestamp(call)) }}
            <span v-if="now - eventTimestamp(call) > 60000">{{ t('business.todo.observe.noNewActivity') }}</span>
          </p>
        </div>
      </div>
    </template>
    <div v-else class="observation__replay">
      <p>
        <el-icon><VideoCamera /></el-icon>{{ t('business.todo.observe.replayHint') }}
      </p>
      <div class="observation__replay-controls">
        <el-button :icon="playing ? 'VideoPause' : 'VideoPlay'" @click="playing = !playing">{{
          playing ? t('business.todo.observe.pause') : t('business.todo.observe.play')
        }}</el-button>
        <el-slider
          v-model="position"
          :min="0"
          :max="Math.max(1, replayEnd)"
          :step="1"
          :aria-label="t('business.todo.observe.replayPosition')"
          :format-tooltip="(value) => `${Math.min(value, replayEnd)} / ${replayEnd}`"
        />
        <span>{{ Math.min(position, replayEnd) }} / {{ replayEnd }}</span>
      </div>
    </div>
    <div class="observation__event-heading">
      <h3>
        {{ t('business.todo.observe.executionEvents') }} <span>{{ filtered.length }}</span>
      </h3>
      <el-select
        v-model="assignmentFilter"
        clearable
        :placeholder="t('business.todo.observe.allWorkers')"
        :aria-label="t('business.todo.observe.allWorkers')"
        class="observation__worker-select"
      >
        <el-option v-for="id in assignmentIds" :key="id" :value="id" :label="t('business.todo.observe.workerId', [id])" />
      </el-select>
    </div>
    <div v-if="selection" class="observation__filter">
      <el-tag closable @close="selection = undefined">{{ t('business.todo.observe.nodeFilter') }}</el-tag
      ><span>{{ t('business.todo.observe.relatedOnly') }}</span>
    </div>
    <div v-if="!filtered.length" class="observation__empty">
      <el-icon><Document /></el-icon
      ><strong>{{ connection === 'connecting' ? t('business.todo.observe.loading') : t('business.todo.observe.noEvents') }}</strong>
      <p>{{ t('business.todo.observe.historyHint') }}</p>
    </div>
    <template v-else>
      <el-button v-if="filtered.length > visibleCount" class="observation__more" @click="visibleCount += 100">{{
        t('business.todo.observe.moreEvents', [filtered.length - visibleCount])
      }}</el-button>
      <ol class="observation__events">
        <li v-for="event in visibleEvents" :key="event.id" class="observation__event">
          <div class="observation__rail">
            <el-icon :class="`observation__icon--${eventType(event.status)}`"><component :is="eventIcon(event.status)" /></el-icon>
          </div>
          <div class="observation__event-body">
            <div class="observation__event-top">
              <strong>{{ eventLabel(event.kind) }}</strong
              ><el-tag :type="eventType(event.status)" size="small">{{ statusLabel(event.status) }}</el-tag
              ><time>{{ event.eventTime }}</time>
            </div>
            <p v-if="visibleSummary(event)" class="observation__summary">{{ visibleSummary(event) }}</p>
            <div class="observation__meta">
              <span v-if="event.assignmentId">{{ t('business.todo.observe.workerId', [event.assignmentId]) }}</span
              ><span v-if="event.durationMs != null">{{ elapsed(event.durationMs) }}</span
              ><span v-if="event.roundId" :title="event.roundId"
                >{{ t('business.todo.observe.round') }} {{ event.roundId.slice(0, 8) }}</span
              ><span>#{{ event.id }}</span>
            </div>
            <details v-if="event.detail" class="observation__details">
              <summary>{{ t('business.todo.observe.eventDetail') }}</summary>
              <pre>{{ formatDetail(event.detail) }}</pre>
            </details>
          </div>
        </li>
      </ol>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue'
import { t, type MessageKey } from '@/i18n'
import { Loading, VideoCamera, Document } from '@element-plus/icons-vue'
import TodoFlow, { type FlowSelection } from './TodoFlow.vue'
import { useTodoObservation } from '../composables/useTodoObservation'
import { elapsed, eventLabel, statusLabel, eventType, eventIcon, visibleSummary } from '../composables/useObservationLabel'
import type { TodoEvent, TodoThreadDetail } from '../types'
const props = defineProps<{ threadId: number; detail: TodoThreadDetail }>()
const { events, connection, reconnect } = useTodoObservation(toRef(props, 'threadId'))
// --------------------------------- ref data ---------------------------------
const selection = ref<FlowSelection>()
const assignmentFilter = ref<number>()
const mode = ref('live')
const position = ref(0)
const replayEnd = ref(0)
const playing = ref(false)
const visibleCount = ref(100)
const now = ref(Date.now())
const clock = setInterval(() => {
  now.value = Date.now()
  if (playing.value && mode.value === 'replay') {
    if (position.value < replayEnd.value) position.value++
    else playing.value = false
  }
}, 1000)
const connectionLabel = computed(() => t(`business.todo.observe.connection.${connection.value}` as MessageKey))
const assignmentIds = computed(() => props.detail.tickets.flatMap((ticket) => ticket.assignments.map((assignment) => assignment.id)))
const activeCalls = computed(() => {
  const ended = new Set(events.value.filter((event) => ['tool.finished', 'tool.failed'].includes(event.kind)).map((event) => event.callId))
  const endedRounds = new Set(events.value.filter((event) => event.kind === 'round.ended').map((event) => event.roundId))
  const endedAssignments = new Set(events.value.filter((event) => event.kind === 'assignment.ended').map((event) => event.assignmentId))
  return events.value.filter(
    (event) =>
      event.kind === 'tool.started' &&
      !ended.has(event.callId) &&
      !endedRounds.has(event.roundId) &&
      !endedAssignments.has(event.assignmentId)
  )
})
const filtered = computed(() => {
  const source = mode.value === 'replay' ? events.value.slice(0, Math.min(position.value, replayEnd.value)) : events.value
  return source.filter((event) => {
    if (assignmentFilter.value && event.assignmentId !== assignmentFilter.value) return false
    const scope = selection.value
    if (!scope) return true
    if (scope.type === 'assignment') return String(event.assignmentId) === scope.id
    if (scope.type === 'ticket') return String(event.ticketId) === scope.id
    if (scope.type === 'eval') return event.evalId === scope.id
    const triggerId = Number(scope.id)
    const trigger = props.detail.triggers.find((item) => item.id === triggerId)
    const evalIds = props.detail.decisions.filter((item) => item.triggerIds.includes(triggerId)).map((item) => item.evalId)
    if (trigger?.evalId) evalIds.push(trigger.evalId)
    if (event.evalId && evalIds.includes(event.evalId)) return true
    if (event.ticketId != null && trigger?.fanout.includes(event.ticketId)) return true
    if (event.kind.startsWith('input.') && event.summary === scope.id) return true
    if (event.kind === 'trigger.received') {
      try {
        return (JSON.parse(event.detail || '{}') as { triggerId?: number }).triggerId === triggerId
      } catch {
        return false
      }
    }
    return false
  })
})
const visibleEvents = computed(() => filtered.value.slice(-visibleCount.value))
// --------------------------------- fun ---------------------------------
function eventTimestamp(event: TodoEvent) {
  return Date.parse(event.eventTime.replace(' ', 'T') + '+08:00')
}
function formatDetail(detail: string) {
  try {
    return JSON.stringify(JSON.parse(detail), null, 2)
  } catch {
    return detail
  }
}
// --------------------------------- page init ---------------------------------
watch(mode, (value) => {
  playing.value = false
  if (value === 'replay') {
    replayEnd.value = events.value.length
    position.value = 0
    selection.value = undefined
  }
})
watch(
  () => props.threadId,
  () => {
    mode.value = 'live'
    selection.value = undefined
    assignmentFilter.value = undefined
    visibleCount.value = 100
  }
)
onBeforeUnmount(() => clearInterval(clock))
</script>
<style scoped lang="scss">
.observation__toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
}
.observation__spacer {
  flex: 1;
}
.observation__connection {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}
.observation__event-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin: 32px 0 16px;
  h3 {
    font-size: 15px;
    margin: 0;
    span {
      font-weight: 400;
      color: var(--el-text-color-regular);
      margin-left: 8px;
    }
  }
}
.observation__worker-select {
  width: 200px;
}
.observation__running {
  display: flex;
  gap: 12px;
  padding: 16px;
  margin-top: 24px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  p {
    margin: 8px 0 0;
    overflow-wrap: anywhere;
  }
  span {
    color: var(--el-text-color-regular);
  }
}
.observation__events {
  list-style: none;
  margin: 0;
  padding: 0;
}
.observation__event {
  display: flex;
  gap: 16px;
}
.observation__rail {
  position: relative;
  width: 24px;
  flex: 0 0 24px;
  padding-top: 3px;
  text-align: center;
  &::after {
    content: '';
    width: 1px;
    background: var(--el-border-color);
    position: absolute;
    top: 28px;
    bottom: 0;
    left: 12px;
  }
}
.observation__event:last-child .observation__rail::after {
  display: none;
}
.observation__event-body {
  flex: 1;
  min-width: 0;
  padding-bottom: 24px;
}
.observation__event-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  strong {
    font-size: 13px;
  }
  time {
    font-size: 12px;
    color: var(--el-text-color-regular);
    margin-left: auto;
  }
}
.observation__summary {
  font-size: 13px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
  margin: 8px 0;
}
.observation__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-top: 8px;
}
.observation__details {
  margin-top: 8px;
  font-size: 12px;
  summary {
    cursor: pointer;
    color: var(--el-color-primary);
    padding: 8px 0;
    width: fit-content;
  }
  pre {
    margin: 0;
    padding: 16px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    font-family: monospace;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.6;
    max-height: 360px;
    overflow-y: auto;
  }
}
.observation__icon--danger {
  color: var(--el-color-danger);
}
.observation__icon--warning {
  color: var(--el-color-warning-dark-2);
}
.observation__icon--success {
  color: var(--el-color-success-dark-2);
}
.observation__icon--primary {
  color: var(--el-color-primary);
}
.observation__empty {
  padding: 32px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--el-text-color-regular);
  text-align: center;
  font-size: 13px;
  p {
    margin: 0;
    line-height: 1.7;
    max-width: 440px;
  }
}
.observation__more {
  margin: 0 0 16px 40px;
}
.observation__filter {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  margin-bottom: 16px;
  color: var(--el-text-color-regular);
}
.observation__replay {
  background: var(--el-fill-color-light);
  padding: 16px;
  border-radius: 8px;
  p {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 13px;
    margin: 0 0 16px;
  }
}
.observation__replay-controls {
  display: flex;
  align-items: center;
  gap: 24px;
  .el-slider {
    flex: 1;
  }
  > span {
    min-width: 72px;
    font-size: 12px;
    text-align: right;
  }
}
@media (max-width: 600px) {
  .observation__toolbar {
    gap: 8px;
  }
  .observation__connection {
    max-width: 45%;
  }
  .observation__event-top time {
    width: 100%;
    margin-left: 0;
  }
  .observation__replay-controls {
    gap: 12px;
  }
}
</style>
