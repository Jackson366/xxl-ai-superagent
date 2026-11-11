<!--
  超级助理统一入口

  @author xxl-ai 2026-11-12
-->
<template>
  <div class="app-container">
    <div class="content-inner assistant-page">
      <header class="assistant-nav">
        <div class="assistant-switch">
          <el-button type="primary" plain aria-current="page">{{ t('business.todo.assistant.tab') }}</el-button>
          <el-button @click="router.push('/todo')">{{ t('business.todo.title') }}</el-button>
        </div>
        <el-button icon="Setting" text @click="router.push('/todo/settings')">{{ t('business.todo.settings') }}</el-button>
      </header>

      <section class="assistant-workspace">
        <div v-if="!turns.length" class="assistant-welcome">
          <span class="assistant-mark"><el-icon><ChatDotRound /></el-icon></span>
          <h2>{{ t('business.todo.assistant.heading') }}</h2>
          <p>{{ t('business.todo.assistant.intro') }}</p>
        </div>
        <div v-else ref="messageLog" class="assistant-log" role="log" :aria-label="t('business.todo.assistant.messages')">
          <div v-if="hasEarlier" class="assistant-earlier">
            <el-button text :loading="loadingEarlier" @click="loadEarlier">{{ t('business.todo.assistant.earlier') }}</el-button>
          </div>
          <article v-for="turn in turns" :id="'assistant-turn-' + turn.id" :key="turn.id" class="assistant-turn">
            <div class="assistant-user">
              <div>{{ turn.content }}</div>
              <small v-if="turn.referenceTitle">{{ t('business.todo.assistant.referenceTitle', [turn.referenceTitle]) }}</small>
            </div>
            <div class="assistant-response">
              <span class="assistant-avatar" aria-hidden="true"><el-icon><ChatDotRound /></el-icon></span>
              <div class="assistant-response-body">
                <p v-if="turn.state === 'pending'" class="assistant-muted">{{ t('business.todo.assistant.thinking') }}</p>
                <template v-if="turn.state === 'failed'">
                  <p>{{ reasonLabel(turn.errorCode) || t('business.todo.assistant.failed') }}</p>
                  <div class="assistant-actions">
                    <el-button size="small" :loading="workingTurn === turn.id" @click="retryTurn(turn.id)">{{ t('business.todo.retry') }}</el-button>
                    <el-button v-if="turn.errorCode?.startsWith('model')" size="small" text @click="router.push('/todo/settings')">
                      {{ t('business.todo.goSettings') }}
                    </el-button>
                  </div>
                </template>
                <MarkdownView v-if="turn.reply" :content="turn.reply" />
                <div v-if="turn.proposal" class="assistant-card">
                  <strong>{{ t(turn.attentionId ? 'business.todo.assistant.answerScope' : 'business.todo.assistant.scope') }}</strong>
                  <p class="assistant-scope">{{ turn.proposal }}</p>
                  <div class="assistant-actions">
                    <el-button
                      v-if="turn.state === 'proposed'"
                      type="primary"
                      size="small"
                      :loading="workingTurn === turn.id"
                      @click="confirmTurn(turn.id)"
                    >{{ t(turn.attentionId ? 'business.todo.assistant.confirmAnswer' : 'business.todo.assistant.confirm') }}</el-button>
                    <el-button v-if="turn.state === 'proposed' && !turn.attentionId" size="small" @click="adjustScope(turn)">
                      {{ t('business.todo.assistant.adjust') }}
                    </el-button>
                    <span v-if="turn.state === 'replaced'" class="assistant-muted">{{ t('business.todo.assistant.replaced') }}</span>
                    <el-button v-if="turn.threadId" size="small" @click="openTask(turn.threadId)">
                      {{ t('business.todo.assistant.openTask') }}
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>

        <div class="assistant-dock">
          <div v-if="pendingProposal || trackedDetail" class="assistant-activity">
            <template v-if="pendingProposal">
              <span>{{ t('business.todo.assistant.pendingConfirmation') }}</span>
              <el-button size="small" text @click="jumpToTurn(pendingProposal.id)">{{ t('business.todo.assistant.review') }}</el-button>
            </template>
            <template v-else-if="trackedDetail">
              <span class="assistant-task-title">{{ trackedDetail.thread.title }}</span>
              <el-tag size="small" :type="trackedTicket?.endedOutcome === 'stopped' ? 'warning' : stageType(trackedDetail.thread.stage)">
                {{ trackedTicket?.endedOutcome ? outcomeLabel(trackedTicket.endedOutcome) : stageLabel(trackedDetail.thread.stage) }}
              </el-tag>
              <div class="assistant-actions">
                <el-button size="small" text @click="openTask(trackedDetail.thread.id)">
                  {{ t(trackedDetail.thread.stage === 'ended' ? 'business.todo.assistant.result' : 'business.todo.assistant.openTask') }}
                </el-button>
                <el-button v-if="trackedTicket && trackedTicket.stage !== 'ended'" size="small" text @click="manageTicket(trackedTicket, 'stop')">
                  {{ t('business.todo.stop') }}
                </el-button>
                <el-button v-if="trackedTicket?.endedOutcome === 'stopped'" size="small" text @click="manageTicket(trackedTicket, 'restart')">
                  {{ t('business.todo.assistant.restart') }}
                </el-button>
              </div>
            </template>
          </div>

          <div v-if="!turns.length" class="assistant-examples">
            <el-button v-for="example in examples" :key="example.label" size="small" text @click="fillExample(t(example.prompt))">
              {{ t(example.label) }}
            </el-button>
          </div>
          <form class="assistant-composer" @submit.prevent="send">
            <label for="todo-assistant-message">{{ t('business.todo.assistant.message') }}</label>
            <el-input
              id="todo-assistant-message"
              ref="input"
              v-model="draft"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 6 }"
              maxlength="4000"
              :placeholder="t('business.todo.assistant.placeholder')"
              @keydown="handleKey"
            />
            <div class="assistant-composer-actions">
              <div class="assistant-actions">
                <el-button text size="small" icon="Link" @click="openReferences">{{ t('business.todo.assistant.reference') }}</el-button>
                <el-tag v-if="reference" closable @close="clearReference">{{ reference.title }}</el-tag>
                <el-tag v-if="editingTurn" type="info" closable @close="editingTurn = undefined">
                  {{ t('business.todo.assistant.editing') }}
                </el-tag>
              </div>
              <el-button native-type="submit" type="primary" icon="Promotion" :loading="sending" :disabled="!draft.trim()">
                {{ t('business.todo.assistant.send') }}
              </el-button>
            </div>
          </form>
        </div>
      </section>
    </div>

    <el-dialog v-model="referenceDialog" :title="t('business.todo.assistant.reference')" width="min(540px, 92vw)" append-to-body>
      <el-table v-loading="loadingReferences" :data="references" :empty-text="t('business.todo.assistant.noReferences')">
        <el-table-column prop="title" :label="t('business.todo.title')" show-overflow-tooltip />
        <el-table-column :label="t('business.todo.stageLabel')" width="110">
          <template #default="{ row }">{{ stageLabel(row.stage) }}</template>
        </el-table-column>
        <el-table-column width="84" align="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="selectReference({ id: row.id, title: row.title })">{{ t('business.todo.assistant.choose') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="referenceTotal > 10"
        v-model:current-page="referencePage"
        :total="referenceTotal"
        :page-size="10"
        layout="prev, pager, next"
        @current-change="loadReferences"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'BusinessTodoAssistant' })
import { ChatDotRound } from '@element-plus/icons-vue'
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { t } from '@/i18n'
import modal from '@/utils/modal'
import MarkdownView from '@/modules/business/chat/components/MarkdownView.vue'
import {
  confirmAssistantTurn, getTodo, listAssistantTurns, listTodo, restartAssistantTicket, retryAssistantTurn, sendAssistantTurn, stopTicket
} from '../api'
import { newRequestId } from '../composables/useSubmit'
import { outcomeLabel, reasonLabel, stageLabel, stageType } from '../composables/useTodoLabel'
import type { TodoAssistantTurn, TodoThread, TodoThreadDetail, TodoTicket } from '../types'

// --------------------------------- ref data ---------------------------------
const route = useRoute()
const router = useRouter()
const input = ref<{ focus: () => void }>()
const messageLog = ref<HTMLElement>()
const draft = ref('')
const turns = ref<TodoAssistantTurn[]>([])
const reference = ref<Pick<TodoThread, 'id' | 'title'>>()
const editingTurn = ref<number>()
const referenceDialog = ref(false)
const references = ref<TodoThread[]>([])
const referenceTotal = ref(0)
const referencePage = ref(1)
const loadingReferences = ref(false)
const sending = ref(false)
const workingTurn = ref<number>()
const loadingEarlier = ref(false)
const hasEarlier = ref(false)
const trackedDetail = ref<TodoThreadDetail>()
const pendingProposal = computed(() => [...turns.value].reverse().find(turn => turn.state === 'proposed'))
const trackedId = computed(() => [...turns.value].reverse().find(turn => turn.threadId)?.threadId)
const trackedTicket = computed(() => trackedDetail.value?.tickets.find(ticket => ticket.id === trackedDetail.value?.thread.ticketId))
const examples = [
  { label: 'business.todo.assistant.exampleTasks', prompt: 'business.todo.assistant.promptTasks' },
  { label: 'business.todo.assistant.exampleMeeting', prompt: 'business.todo.assistant.promptMeeting' },
  { label: 'business.todo.assistant.exampleReport', prompt: 'business.todo.assistant.promptReport' }
] as const
let timer: ReturnType<typeof setInterval> | undefined
let refreshInFlight = false
let revision = 0
let lastRequest: { key: string; payload: string } | undefined

// --------------------------------- fun ---------------------------------
function merge(items: TodoAssistantTurn[]) {
  const byId = new Map(turns.value.map(turn => [turn.id, turn]))
  for (const item of items) byId.set(item.id, item)
  turns.value = [...byId.values()].sort((left, right) => left.id - right.id)
}
async function scrollToLatest() {
  await nextTick()
  const log = messageLog.value
  if (log) log.scrollTop = log.scrollHeight
}
async function refresh() {
  if (refreshInFlight) return
  refreshInFlight = true
  const version = revision
  try {
    const log = messageLog.value
    const nearEnd = !log || log.scrollHeight - log.scrollTop - log.clientHeight < 96
    const response = await listAssistantTurns()
    if (version !== revision) return
    if (!turns.value.length) hasEarlier.value = response.data.length === 30
    merge(response.data)
    if (nearEnd) void scrollToLatest()
    const id = trackedId.value
    if (id && (trackedDetail.value?.thread.id !== id || !['ended', 'ignored'].includes(trackedDetail.value.thread.stage))) {
      const detail = await getTodo(id)
      if (trackedId.value === id && version === revision) trackedDetail.value = detail.data
    }
  } catch {
    // 请求层统一提示；下一轮刷新继续读取状态
  } finally {
    refreshInFlight = false
  }
}
async function loadEarlier() {
  if (loadingEarlier.value || !turns.value[0]) return
  loadingEarlier.value = true
  const height = messageLog.value?.scrollHeight ?? 0
  try {
    const response = await listAssistantTurns(turns.value[0].id)
    merge(response.data)
    hasEarlier.value = response.data.length === 30
    await nextTick()
    if (messageLog.value) messageLog.value.scrollTop += messageLog.value.scrollHeight - height
  } catch {
    // 请求层统一提示
  } finally {
    loadingEarlier.value = false
  }
}
async function send() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  const referenceId = reference.value?.id
  const revisesId = editingTurn.value
  const payload = JSON.stringify({ content, referenceId, revisesId })
  if (lastRequest?.payload !== payload) lastRequest = { key: newRequestId(), payload }
  sending.value = true
  try {
    const response = await sendAssistantTurn({ content, referenceId, revisesId, requestId: lastRequest.key })
    revision++
    merge([response.data])
    if (revisesId) {
      const previous = turns.value.find(item => item.id === revisesId)
      if (previous?.state === 'proposed') merge([{ ...previous, state: 'replaced' }])
      if (editingTurn.value === revisesId) editingTurn.value = undefined
    }
    if (draft.value.trim() === content && reference.value?.id === referenceId) {
      draft.value = ''
      clearReference()
    }
    lastRequest = undefined
    void scrollToLatest()
  } catch {
    // 保留草稿及请求标识，用户可直接重试
  } finally {
    sending.value = false
  }
}
function handleKey(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent) || event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  void send()
}
async function confirmTurn(id: number) {
  if (workingTurn.value != null) return
  workingTurn.value = id
  try {
    const response = await confirmAssistantTurn(id)
    const turn = turns.value.find(item => item.id === id)
    revision++
    if (turn) merge([{ ...turn, threadId: response.data, state: 'submitted' }])
    trackedDetail.value = undefined
    void refresh()
  } catch {
    // 执行建议仍可查看
  } finally {
    workingTurn.value = undefined
  }
}
async function retryTurn(id: number) {
  if (workingTurn.value != null) return
  workingTurn.value = id
  try {
    const response = await retryAssistantTurn(id)
    revision++
    merge([response.data])
  } catch {
    // 请求层统一提示
  } finally {
    workingTurn.value = undefined
  }
}
async function manageTicket(ticket: TodoTicket, action: 'stop' | 'restart') {
  try {
    await modal.confirm(t(action === 'stop' ? 'business.todo.stopConfirm' : 'business.todo.assistant.restartConfirm', [ticket.title]))
    await (action === 'stop' ? stopTicket(ticket.id) : restartAssistantTicket(ticket.id))
    revision++
    trackedDetail.value = undefined
    void refresh()
  } catch {
    // 取消或由请求层提示失败
  }
}
function openTask(id: number) {
  void router.push({ path: '/todo', query: { thread: id, view: 'timeline' } })
}
function jumpToTurn(id: number) {
  document.getElementById('assistant-turn-' + id)?.scrollIntoView({ block: 'nearest' })
}
function fillExample(text: string) {
  if (draft.value.trim() && !examples.some(example => t(example.prompt) === draft.value.trim())) {
    draft.value = (draft.value.trimEnd() + '\n' + text).slice(0, 4000)
  } else {
    draft.value = text
  }
  input.value?.focus()
}
function adjustScope(turn: TodoAssistantTurn) {
  editingTurn.value = turn.id
  if (!draft.value.trim()) draft.value = t('business.todo.assistant.adjustPrompt') + '\n'
  input.value?.focus()
}
function clearReference() {
  reference.value = undefined
  if (route.query.reference) void router.replace({ path: route.path })
}
async function loadReferences() {
  if (loadingReferences.value) return
  loadingReferences.value = true
  try {
    const response = await listTodo({ offset: (referencePage.value - 1) * 10, pagesize: 10 })
    references.value = response.data.data
    referenceTotal.value = response.data.total
  } catch {
    // 请求层统一提示
  } finally {
    loadingReferences.value = false
  }
}
function openReferences() {
  referenceDialog.value = true
  referencePage.value = 1
  void loadReferences()
}
function selectReference(row: { id?: unknown; title?: unknown }) {
  if (typeof row.id !== 'number' || typeof row.title !== 'string') return
  reference.value = { id: row.id, title: row.title }
  referenceDialog.value = false
  void nextTick(() => input.value?.focus())
}
function startPolling() {
  if (timer) return
  void refresh()
  timer = setInterval(refresh, 3000)
}
function stopPolling() {
  if (timer) clearInterval(timer)
  timer = undefined
}

// --------------------------------- page init ---------------------------------
watch(() => route.query.reference, async (value) => {
  const id = Number(value)
  if (route.path !== '/todo/assistant' || !Number.isSafeInteger(id) || id <= 0) return
  try {
    const response = await getTodo(id)
    if (route.path === '/todo/assistant' && route.query.reference === value) selectReference(response.data.thread)
  } catch {
    // 已移除或没有访问权限的待办不能作为引用
  }
}, { immediate: true })
onMounted(startPolling)
onActivated(startPolling)
onDeactivated(stopPolling)
onBeforeUnmount(stopPolling)
</script>

<style scoped lang="scss">
.assistant-nav, .assistant-switch, .assistant-actions, .assistant-composer-actions, .assistant-activity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.assistant-nav, .assistant-composer-actions { justify-content: space-between; }
.assistant-nav { margin-bottom: 16px; }
.assistant-workspace {
  display: flex;
  flex-direction: column;
  min-height: min(650px, calc(100dvh - 155px));
  padding: 24px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--el-bg-color);
}
.assistant-log, .assistant-dock { width: min(100%, 710px); margin-inline: auto; }
.assistant-welcome {
  flex: 1;
  display: grid;
  place-content: center;
  justify-items: center;
  text-align: center;
  padding: 48px 8px;
}
.assistant-welcome h2 { margin: 16px 0 8px; font-size: clamp(22px, 3vw, 30px); }
.assistant-welcome p, .assistant-muted { margin: 0; color: var(--el-text-color-secondary); }
.assistant-mark, .assistant-avatar {
  display: grid;
  place-items: center;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.assistant-mark { width: 48px; height: 48px; border-radius: 14px; font-size: 24px; }
.assistant-avatar { width: 30px; height: 30px; border-radius: 9px; flex-shrink: 0; }
.assistant-log {
  flex: 1;
  max-height: min(60dvh, 640px);
  min-height: 160px;
  overflow-y: auto;
  padding: 0 4px 16px;
  overflow-wrap: anywhere;
}
.assistant-earlier { text-align: center; }
.assistant-turn { display: grid; gap: 16px; margin: 8px 0 28px; }
.assistant-user {
  justify-self: end;
  max-width: 88%;
  padding: 8px 14px;
  background: var(--el-color-primary-light-9);
  border-radius: 14px 14px 4px 14px;
  white-space: pre-wrap;
}
.assistant-user small { display: block; margin-top: 4px; color: var(--el-text-color-secondary); }
.assistant-response { display: flex; gap: 10px; align-items: flex-start; }
.assistant-response-body { min-width: 0; width: 100%; display: grid; gap: 8px; line-height: 1.55; }
.assistant-response-body p { margin: 0; }
.assistant-card, .assistant-activity {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.assistant-card { display: grid; gap: 8px; }
.assistant-scope { white-space: pre-wrap; }
.assistant-activity { margin-bottom: 8px; justify-content: space-between; }
.assistant-task-title { flex: 1; min-width: 110px; overflow-wrap: anywhere; }
.assistant-examples { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 6px; }
.assistant-composer {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
}
.assistant-composer:focus-within { border-color: var(--el-color-primary); }
.assistant-composer label { font-size: 13px; color: var(--el-text-color-secondary); }
.assistant-actions :deep(.el-button + .el-button), .assistant-switch :deep(.el-button + .el-button) { margin-left: 0; }
@media (max-width: 600px) {
  .assistant-workspace { padding: 16px 10px; }
  .assistant-welcome { padding-block: 32px; }
  .assistant-composer :deep(textarea) { font-size: 16px; }
  .assistant-composer-actions :deep(.el-button) { min-height: 44px; }
}
</style>
