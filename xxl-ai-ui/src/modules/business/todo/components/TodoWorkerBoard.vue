<!-- 活动派工看板：活动与报告分开呈现；@author Codex 2026-11-11 -->
<template>
  <section class="worker-board" :aria-label="t('business.todo.observe.workers')">
    <div class="worker-board__heading">
      <div>
        <h2>
          {{ t('business.todo.observe.workers') }} <span>{{ workers.length }}</span>
        </h2>
        <p>{{ t('business.todo.observe.workerHint') }}</p>
      </div>
      <span class="worker-board__refresh">{{ t('business.todo.observe.pollHint') }}</span>
    </div>
    <div v-if="!workers.length" class="worker-board__empty">
      <el-icon><CoffeeCup /></el-icon>{{ t('business.todo.observe.noWorkers') }}
    </div>
    <div v-else class="worker-board__grid">
      <button v-for="worker in workers" :key="worker.assignmentId" class="worker-card" type="button" @click="emit('open', worker.threadId)">
        <div class="worker-card__top">
          <span>Worker #{{ worker.assignmentId }}</span
          ><el-tag :type="eventType(worker.lastStatus || 'queued')" size="small">{{ workerActivity(worker) }}</el-tag>
        </div>
        <h3>{{ worker.title }}</h3>
        <div class="worker-card__activity">
          <el-icon><component :is="eventIcon(worker.lastStatus || 'queued')" /></el-icon
          ><span>{{ worker.lastKind?.startsWith('tool.') ? worker.lastSummary : workerActivity(worker) }}</span>
        </div>
        <p>{{ worker.reportSummary || t('business.todo.observe.noReport') }}</p>
        <div class="worker-card__footer">
          <span>{{ t('business.todo.observe.ticketId', [worker.ticketId]) }}</span
          ><span v-if="worker.startedTime">{{
            t('business.todo.observe.sinceStart', [elapsed(now.getTime() - Date.parse(worker.startedTime.replace(' ', 'T') + '+08:00'))])
          }}</span>
          <span>{{ t('business.todo.observe.lastActivity') }} {{ worker.lastEventTime?.slice(11, 19) || '—' }}</span
          ><el-icon><ArrowRight /></el-icon>
        </div>
      </button>
    </div>
  </section>
</template>
<script setup lang="ts">
import { t } from '@/i18n'
import { useNow, useIntervalFn } from '@vueuse/core'
import { ArrowRight, CoffeeCup } from '@element-plus/icons-vue'
import { elapsed, eventType, eventIcon, workerActivity } from '../composables/useObservationLabel'
import type { TodoWorker } from '../types'
defineProps<{ workers: TodoWorker[] }>()
const now = useNow({ scheduler: (update) => useIntervalFn(update, 1000) })
const emit = defineEmits<{ open: [threadId: number] }>()
</script>
<style scoped lang="scss">
.worker-board {
  margin: 24px 0;
}
.worker-board__heading {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 16px;
  h2 {
    font-size: 16px;
    margin: 0;
    span {
      margin-left: 8px;
      color: var(--el-text-color-regular);
      font-weight: 400;
    }
  }
  p {
    margin: 8px 0 0;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }
}
.worker-board__refresh {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.worker-board__empty {
  display: flex;
  gap: 12px;
  padding: 24px;
  align-items: center;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  color: var(--el-text-color-regular);
}
.worker-board__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
  gap: 16px;
}
.worker-card {
  text-align: left;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
  font: inherit;
  cursor: pointer;
  transition: border-color 0.15s;
  &:hover,
  &:focus-visible {
    border-color: var(--el-color-primary);
    outline: 2px solid var(--el-color-primary-light-7);
    outline-offset: 2px;
  }
  h3 {
    font-size: 15px;
    margin: 16px 0;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
  p {
    font-size: 13px;
    line-height: 1.6;
    color: var(--el-text-color-regular);
    margin: 12px 0 16px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}
.worker-card__top,
.worker-card__footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
  font-size: 12px;
}
.worker-card__activity {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.worker-card__footer {
  color: var(--el-text-color-regular);
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}
@media (max-width: 600px) {
  .worker-board__refresh {
    display: none;
  }
}
</style>
