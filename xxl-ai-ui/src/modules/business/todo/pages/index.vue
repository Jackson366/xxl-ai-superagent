<!--
  BusinessTodo（超级助理）
  三段：顶部提交框与状态；中部待办列表（默认只展开“需要我处理”）；右侧详情抽屉（时间线 + 追加/回答/停止/重试）
-->
<template>
  <div class="app-container">
    <div class="content-inner">
      <div class="todo-entry">
        <el-button @click="router.push('/todo/assistant')">{{ t('business.todo.assistant.tab') }}</el-button>
        <el-button type="primary" plain aria-current="page">{{ t('business.todo.title') }}</el-button>
      </div>
      <!-- 健康：只在需要人行动时出现 -->
      <el-alert v-if="overview.readyCode" type="warning" :closable="false" show-icon class="mb8">
        <template #title>
          {{ reasonLabel(overview.readyCode) }}
          <el-link type="primary" class="todo-link" @click="goSettings">{{ t('business.todo.goSettings') }}</el-link>
        </template>
      </el-alert>
      <el-alert v-if="overview.paused" type="info" :title="t('business.todo.pausedTip')" :closable="false" show-icon class="mb8" />
      <el-alert v-for="problem in overview.problems" :key="problem" type="error" :title="problem" :closable="false" class="mb8" />

      <!-- 提交 -->
      <el-input
        v-model="draft"
        type="textarea"
        :rows="3"
        maxlength="4000"
        show-word-limit
        :placeholder="t('business.todo.placeholder')"
        @keydown.ctrl.enter="handleCreate"
      />
      <div class="todo-bar">
        <span class="todo-muted">{{ t('business.todo.slots', [overview.busySlots, overview.maxWorkers]) }}</span>
        <span class="todo-spacer" />
        <el-badge :value="overview.unread" :hidden="!overview.unread">
          <el-button icon="Bell" @click="inboxVisible = true">{{ t('business.todo.inbox') }}</el-button>
        </el-badge>
        <el-button icon="Setting" @click="goSettings">{{ t('business.todo.settings') }}</el-button>
        <el-button type="primary" :loading="creator.loading.value" :disabled="!draft.trim()" @click="handleCreate">
          {{ t('business.todo.submit') }}
        </el-button>
      </div>

      <TodoWorkerBoard :workers="overview.workers || []" @open="openThread" />

      <!-- 待办 -->
      <el-collapse v-model="activePanels" class="todo-panels">
        <el-collapse-item name="needMe" :title="t('business.todo.needMe', [overview.needMe.length])">
          <el-table :data="overview.needMe" :empty-text="t('business.todo.needMeEmpty')" @row-click="openDetail">
            <el-table-column :label="t('business.todo.title')" prop="title" :show-overflow-tooltip="true" />
            <el-table-column :label="t('business.todo.stageLabel')" width="130" align="center">
              <template #default="scope">
                <el-tag :type="stageType(scope.row.stage)" size="small">{{ stageLabel(scope.row.stage) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('business.todo.waiting')" :show-overflow-tooltip="true">
              <template #default="scope">{{ waitingText(scope.row) }}</template>
            </el-table-column>
            <el-table-column :label="t('business.todo.lastTime')" prop="lastTime" width="170" align="center" />
          </el-table>
        </el-collapse-item>
        <el-collapse-item name="all" :title="t('business.todo.all', [table.total])">
          <el-table v-loading="table.loading" :data="table.list" @row-click="openDetail">
            <el-table-column :label="t('business.todo.title')" prop="title" :show-overflow-tooltip="true" />
            <el-table-column :label="t('business.todo.stageLabel')" width="130" align="center">
              <template #default="scope">
                <el-tag :type="stageType(scope.row.stage)" size="small">{{ stageLabel(scope.row.stage) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('business.todo.waiting')" :show-overflow-tooltip="true">
              <template #default="scope">{{ waitingText(scope.row) }}</template>
            </el-table-column>
            <el-table-column :label="t('business.todo.lastTime')" prop="lastTime" width="170" align="center" />
          </el-table>
          <Pagination
            v-show="table.total > 0"
            :total="table.total"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            @pagination="getList()"
          />
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="detail?.thread.title" size="min(1180px, 96vw)" append-to-body @closed="detail = undefined">
      <div v-loading="detailLoading && !detail">
        <el-tabs v-if="detail" v-model="detailTab">
          <el-tab-pane :label="t('business.todo.observe.title')" name="observation">
            <TodoObservation v-if="detailTab === 'observation'" :key="detail.thread.id" :thread-id="detail.thread.id" :detail="detail" />
          </el-tab-pane>
          <el-tab-pane :label="t('business.todo.timeline')" name="timeline">
            <TodoTimeline :detail="detail" @stop="handleStop" @retry="handleRetry" @requeue="handleRequeue" @dismiss="handleDismiss" />
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <div v-if="detail?.thread.question" class="todo-question">{{ t('business.todo.question') }}：{{ detail.thread.question }}</div>
        <el-input
          v-model="reply"
          type="textarea"
          :rows="3"
          maxlength="4000"
          :placeholder="answering ? t('business.todo.answerPlaceholder') : t('business.todo.appendPlaceholder')"
          @keydown.ctrl.enter="handleReply"
        />
        <div class="todo-bar">
          <el-button v-if="detail" @click="continueInAssistant(detail.thread.id)">{{ t('business.todo.assistant.continue') }}</el-button>
          <span class="todo-spacer" />
          <el-button type="primary" :loading="replier.loading.value" :disabled="!reply.trim()" @click="handleReply">
            {{ answering ? t('business.todo.answer') : t('business.todo.append') }}
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 收件箱 -->
    <TodoInbox v-model="inboxVisible" @open="openThread" @read="getOverview" />
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'BusinessTodo' })
import { t } from '@/i18n'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import modal from '@/utils/modal'
import { Pagination } from '@/components'
import { usePageParams } from '@/composables/usePageParams'
import type { TableState } from '@/types'
import TodoTimeline from '../components/TodoTimeline.vue'
import TodoInbox from '../components/TodoInbox.vue'
import TodoWorkerBoard from '../components/TodoWorkerBoard.vue'
import TodoObservation from '../components/TodoObservation.vue'
import { dismissDecision, getOverview as fetchOverview, getTodo, listTodo, requeueTrigger, retryTicket, stopTicket } from '../api'
import { useSubmit } from '../composables/useSubmit'
import { reasonDisplay, reasonLabel, stageLabel, stageType } from '../composables/useTodoLabel'
import type { TodoOverview, TodoQuery, TodoThread, TodoThreadDetail, TodoTicket } from '../types'

/** 轮询间隔（毫秒） */
const POLL_MS = 5000

const router = useRouter()
const route = useRoute()
const creator = useSubmit() /* 新建待办 */
const replier = useSubmit() /* 追加/回答 */

// --------------------------------- ref data ---------------------------------
const draft = ref('') /* 新建内容 */
const reply = ref('') /* 追加/回答内容 */
const activePanels = ref<string[]>(['needMe', 'all']) /* 默认只展开“需要我处理” */
const inboxVisible = ref(false)
const overview = ref<TodoOverview>({ needMe: [], problems: [], paused: false, busySlots: 0, maxWorkers: 0, unread: 0 })
const queryParams = ref<TodoQuery>({ pageNum: 1, pageSize: 10 })
const table = ref<TableState<TodoThread>>({ list: [], total: 0, loading: true, showSearch: false, ids: [], single: true, multiple: true })
const detailTab = ref('observation')
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailId = ref<number>()
const detail = ref<TodoThreadDetail>()
let timer: ReturnType<typeof setInterval> | undefined

/** 有待回答的提问时，提交即回答 */
const answering = computed(() => !!detail.value?.thread.attentionId)

// --------------------------------- fun ---------------------------------
function getOverview() {
  fetchOverview()
    .then((res) => {
      overview.value = res.data
    })
    .catch(() => {})
}
function getList(silent = false) {
  if (!silent) table.value.loading = true
  listTodo(usePageParams(queryParams)())
    .then((res) => {
      table.value.list = res.data.data
      table.value.total = res.data.total
    })
    .finally(() => {
      table.value.loading = false
    })
}
function getDetail() {
  if (detailId.value == null) return
  const id = detailId.value
  detailLoading.value = true
  getTodo(id)
    .then((res) => {
      if (detailId.value === id && detailVisible.value) detail.value = res.data
    })
    .finally(() => {
      detailLoading.value = false
    })
}
/** 刷新：概览、列表、打开中的详情 */
function refresh(silent = true) {
  getOverview()
  getList(silent)
  if (detailVisible.value) getDetail()
}
/** 在等谁、需要什么：提问 > 报告摘要 > 研判原因 */
function waitingText(row: unknown): string {
  const item = row as TodoThread
  return item.question || item.summary || reasonDisplay(item.reasonCode, item.reasonText)
}
function openDetail(row: { id?: unknown }) {
  if (typeof row.id === 'number') openThread(row.id)
}
function openThread(id: number) {
  if (detailId.value !== id) {
    detail.value = undefined
    reply.value = ''
    detailTab.value = 'observation'
  }
  detailId.value = id
  detailVisible.value = true
  getDetail()
}
function continueInAssistant(id: number) {
  detailVisible.value = false
  router.push({ path: '/todo/assistant', query: { reference: id } })
}
function goSettings() {
  router.push('/todo/settings')
}
function handleCreate() {
  const content = draft.value.trim()
  if (!content || creator.loading.value) return
  creator
    .send({ kind: 'create', content })
    .then((threadId) => {
      draft.value = ''
      modal.msgSuccess(t('business.todo.submitted'))
      refresh(false)
      openThread(threadId)
    })
    .catch(() => {})
}
function handleReply() {
  const content = reply.value.trim()
  const current = detail.value
  if (!content || !current || replier.loading.value) return
  const attentionId = current.thread.attentionId
  replier
    .send({ kind: attentionId ? 'answer' : 'append', threadId: current.thread.id, content, attentionId })
    .then(() => {
      reply.value = ''
      refresh()
    })
    .catch(() => {})
}
function handleStop(ticket: TodoTicket) {
  modal
    .confirm(t('business.todo.stopConfirm', [ticket.title]))
    .then(() => stopTicket(ticket.id))
    .then(() => {
      modal.msgSuccess(t('business.todo.stopped'))
      refresh()
    })
    .catch(() => {})
}
function handleRetry(ticketId: number) {
  retryTicket(ticketId)
    .then(() => refresh())
    .catch(() => {})
}
function handleRequeue(triggerId: number) {
  modal
    .confirm(t('business.todo.requeueConfirm'))
    .then(() => requeueTrigger(triggerId))
    .then(() => refresh())
    .catch(() => {})
}
function handleDismiss(decisionId: number) {
  dismissDecision(decisionId)
    .then(() => refresh())
    .catch(() => {})
}
function startPolling() {
  stopPolling()
  timer = setInterval(() => refresh(), POLL_MS)
}
function stopPolling() {
  if (timer) clearInterval(timer)
  timer = undefined
}

// --------------------------------- page init ---------------------------------
watch(() => [route.path, route.query.thread, route.query.view], ([path, thread, view]) => {
  const id = Number(thread)
  if (path === '/todo' && Number.isSafeInteger(id) && id > 0) {
    openThread(id)
    if (view === 'timeline') detailTab.value = 'timeline'
  }
}, { immediate: true })
onMounted(() => {
  refresh(false)
  startPolling()
})
// 页面缓存（keep-alive）切走时暂停轮询，切回立即刷新
onActivated(() => {
  if (!timer) {
    refresh()
    startPolling()
  }
})
onDeactivated(stopPolling)
onBeforeUnmount(stopPolling)
</script>

<style scoped lang="scss">
.todo-entry {
  display: flex;
  margin-bottom: 16px;
}
.todo-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.todo-spacer {
  flex: 1;
}
.todo-muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.todo-link {
  margin-left: 8px;
  vertical-align: baseline;
}
.todo-panels {
  margin-top: 16px;
  :deep(.el-table__row) {
    cursor: pointer;
  }
}
.todo-question {
  margin-bottom: 8px;
  color: var(--el-color-warning);
  font-size: 13px;
  white-space: pre-wrap;
}
</style>
