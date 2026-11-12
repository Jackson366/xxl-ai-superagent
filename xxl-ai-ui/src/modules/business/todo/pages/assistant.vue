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
        <div v-if="!timeline.length" class="assistant-welcome">
          <span class="assistant-mark"><el-icon><ChatDotRound /></el-icon></span>
          <h2>{{ t('business.todo.assistant.heading') }}</h2>
          <p>{{ t('business.todo.assistant.intro') }}</p>
        </div>
        <div v-else ref="messageLog" class="assistant-log" role="log" :aria-label="t('business.todo.assistant.messages')">
          <div v-if="hasEarlier" class="assistant-earlier">
            <el-button text :loading="loadingEarlier" @click="loadEarlier">{{ t('business.todo.assistant.earlier') }}</el-button>
          </div>
          <template v-for="entry in timeline" :key="entry.key">
            <article v-if="entry.turn" :id="'assistant-turn-' + entry.turn.id" class="assistant-turn">
              <div class="assistant-user">
                <div>{{ entry.turn.content }}</div>
                <small v-if="entry.turn.referenceTitle">{{ t('business.todo.assistant.referenceTitle', [entry.turn.referenceTitle]) }}</small>
              </div>
              <div class="assistant-response">
                <span class="assistant-avatar" aria-hidden="true"><el-icon><ChatDotRound /></el-icon></span>
                <div class="assistant-response-body">
                  <p v-if="entry.turn.state === 'pending'" class="assistant-muted">{{ t('business.todo.assistant.thinking') }}</p>
                  <template v-if="entry.turn.state === 'failed'">
                    <p>{{ reasonLabel(entry.turn.errorCode) || t('business.todo.assistant.failed') }}</p>
                    <div class="assistant-actions">
                      <el-button size="small" :loading="workingTurn === entry.turn.id" @click="retryTurn(entry.turn.id)">{{ t('business.todo.retry') }}</el-button>
                      <el-button v-if="entry.turn.errorCode?.startsWith('model')" size="small" text @click="router.push('/todo/settings')">
                        {{ t('business.todo.goSettings') }}
                      </el-button>
                    </div>
                  </template>
                  <MarkdownView v-if="entry.turn.reply" :content="entry.turn.reply" />
                  <div v-if="entry.turn.proposal" class="assistant-card">
                    <strong>{{ t(entry.turn.attentionId ? 'business.todo.assistant.answerScope' : 'business.todo.assistant.scope') }}</strong>
                    <p class="assistant-scope">{{ entry.turn.proposal }}</p>
                    <div class="assistant-actions">
                      <el-button
                        v-if="entry.turn.state === 'proposed'"
                        type="primary"
                        size="small"
                        :loading="workingTurn === entry.turn.id"
                        @click="confirmTurn(entry.turn.id)"
                      >{{ t(entry.turn.attentionId ? 'business.todo.assistant.confirmAnswer' : 'business.todo.assistant.confirm') }}</el-button>
                      <el-button v-if="entry.turn.state === 'proposed' && !entry.turn.attentionId" size="small" @click="adjustScope(entry.turn)">
                        {{ t('business.todo.assistant.adjust') }}
                      </el-button>
                      <span v-if="entry.turn.state === 'replaced'" class="assistant-muted">{{ t('business.todo.assistant.replaced') }}</span>
                      <el-button v-if="entry.turn.threadId" size="small" @click="openTask(entry.turn.threadId)">
                        {{ t('business.todo.assistant.openTask') }}
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </article>
            <article v-else class="assistant-turn" :class="{ 'assistant-related-notice': entry.notice.related }">
              <div class="assistant-response">
                <span v-if="!entry.notice.related" class="assistant-avatar" aria-hidden="true"><el-icon><ChatDotRound /></el-icon></span>
                <div class="assistant-response-body">
                  <AssistantNotice
                    :notice="entry.notice.data"
                    :status="entry.notice.status"
                    :reply-disabled="sending || !!replyTarget"
                    :retrying="retryingNotices.has(entry.notice.data.id)"
                    @reply="startReply(entry.notice)"
                    @retry="retryNotice(entry.notice)"
                  />
                </div>
              </div>
              <div v-if="entry.notice.answer" class="assistant-user">{{ entry.notice.answer }}</div>
            </article>
          </template>
        </div>

        <div class="assistant-dock">
          <div v-if="pendingProposal || trackedActivity" class="assistant-activity">
            <template v-if="pendingProposal">
              <span>{{ t('business.todo.assistant.pendingConfirmation') }}</span>
              <el-button size="small" text @click="jumpToTurn(pendingProposal.id)">{{ t('business.todo.assistant.review') }}</el-button>
            </template>
            <template v-else-if="trackedActivity">
              <span class="assistant-task-title">{{ trackedActivity.thread.title }}</span>
              <el-tag size="small" :type="trackedTicket?.endedOutcome === 'stopped' ? 'warning' : stageType(trackedActivity.thread.stage)">
                {{ trackedTicket?.endedOutcome ? outcomeLabel(trackedTicket.endedOutcome) : stageLabel(trackedActivity.thread.stage) }}
              </el-tag>
              <div class="assistant-actions">
                <el-button size="small" text @click="openTask(trackedActivity.thread.id)">
                  {{ t(trackedActivity.thread.stage === 'ended' ? 'business.todo.assistant.result' : 'business.todo.assistant.openTask') }}
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

          <div v-if="!timeline.length && !replyTarget" class="assistant-examples">
            <el-button v-for="example in examples" :key="example.label" size="small" text @click="fillExample(t(example.prompt))">
              {{ t(example.label) }}
            </el-button>
          </div>
          <form class="assistant-composer" @submit.prevent="send">
            <div v-if="replyTarget" class="assistant-reply-context">
              <label for="todo-assistant-message">
                {{ t('business.todo.assistant.noticeReplyTo', [replyTarget.data.title]) }}
              </label>
              <el-button text size="small" :disabled="sending" @click="cancelReply">
                {{ t('business.todo.assistant.noticeCancelReply') }}
              </el-button>
            </div>
            <label v-else for="todo-assistant-message">{{ t('business.todo.assistant.message') }}</label>
            <p v-if="replyTarget?.status === 'resolved'" class="assistant-muted" role="status">
              {{ t('business.todo.assistant.noticeResolved') }}
            </p>
            <el-input
              id="todo-assistant-message"
              ref="input"
              v-model="composerText"
              type="textarea"
              :disabled="!!replyTarget && sending"
              :autosize="{ minRows: 2, maxRows: 6 }"
              maxlength="4000"
              :placeholder="t(replyTarget ? 'business.todo.assistant.noticeReplyPlaceholder' : 'business.todo.assistant.placeholder')"
              @compositionstart="composing = true"
              @compositionend="composing = false"
              @keydown="handleKey"
            />
            <div class="assistant-composer-actions">
              <div v-if="!replyTarget" class="assistant-actions">
                <el-button text size="small" icon="Link" @click="openReferences">{{ t('business.todo.assistant.reference') }}</el-button>
                <el-tag v-if="reference" closable @close="clearReference">{{ reference.title }}</el-tag>
                <el-tag v-if="editingTurn" type="info" closable @close="editingTurn = undefined">
                  {{ t('business.todo.assistant.editing') }}
                </el-tag>
              </div>
              <el-button native-type="submit" type="primary" icon="Promotion" :loading="sending" :disabled="!canSend">
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
import AssistantNotice from '../components/AssistantNotice.vue'
import {
  answerAssistantNotice, confirmAssistantTurn, getTodo, listAssistantNotices, listAssistantTurns, listTodo,
  restartAssistantTicket, retryAssistantTurn, retryTicket, sendAssistantTurn, stopTicket
} from '../api'
import { newRequestId } from '../composables/useSubmit'
import { outcomeLabel, reasonLabel, stageLabel, stageType } from '../composables/useTodoLabel'
import type { TodoAssistantNotice, TodoAssistantTurn, TodoThread, TodoThreadDetail, TodoTicket } from '../types'

interface NoticeEntry {
  data: TodoAssistantNotice
  afterTurnId: number
  related: boolean
  status: 'active' | 'answered' | 'retried' | 'resolved'
  answer?: string
}

type TimelineEntry =
  | { key: string; turn: TodoAssistantTurn; notice?: never }
  | { key: string; notice: NoticeEntry; turn?: never }

// --------------------------------- ref data ---------------------------------
const route = useRoute()
const router = useRouter()
const input = ref<{ focus: () => void }>()
const messageLog = ref<HTMLElement>()
const draft = ref('')
const replyDraft = ref('')
const replyTargetId = ref<string>()
const composing = ref(false)
const notices = ref<NoticeEntry[]>([])
const waitingNotices = ref<TodoAssistantNotice[]>([])
const retryingNotices = ref(new Set<string>())
const replyTarget = computed(() => notices.value.find(notice => notice.data.id === replyTargetId.value))
const composerText = computed({
  get: () => replyTarget.value ? replyDraft.value : draft.value,
  set: (value: string) => {
    if (replyTarget.value) replyDraft.value = value
    else draft.value = value
  }
})
const answerPayload = computed(() => {
  const target = replyTarget.value?.data
  return target?.attentionId ? {
    threadId: target.threadId,
    ticketId: target.ticketId,
    attentionId: target.attentionId,
    content: replyDraft.value.trim()
  } : undefined
})
const lastNoticeAnswer = ref<{ key: string; payload: string }>()
const canSend = computed(() => {
  if (!composerText.value.trim()) return false
  if (!replyTarget.value) return true
  return !!answerPayload.value && (replyTarget.value.status === 'active' ||
    lastNoticeAnswer.value?.payload === JSON.stringify(answerPayload.value))
})
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
const trackedActivity = computed(() => {
  const detail = trackedDetail.value
  return detail && !['answer', 'failed', 'delivery'].includes(detail.thread.stage) ? detail : undefined
})
const trackedTicket = computed(() => trackedActivity.value?.tickets.find(ticket => ticket.id === trackedActivity.value?.thread.ticketId))
const canPublishNotices = computed(() => !draft.value.trim() && !composing.value && !sending.value &&
  !replyTarget.value && !turns.value.some(turn => turn.state === 'pending'))
const timeline = computed<TimelineEntry[]>(() => {
  const byTurn = new Map<number, TimelineEntry[]>()
  for (const notice of notices.value) {
    const group = byTurn.get(notice.afterTurnId) ?? []
    group.push({ key: notice.data.id, notice })
    byTurn.set(notice.afterTurnId, group)
  }
  const entries = [...(byTurn.get(0) ?? [])]
  for (const turn of turns.value) {
    entries.push({ key: 'turn-' + turn.id, turn }, ...(byTurn.get(turn.id) ?? []))
  }
  return entries
})
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
function nearLatest() {
  const log = messageLog.value
  return !log || log.scrollHeight - log.scrollTop - log.clientHeight < 32
}
function publishNotices() {
  if (!canPublishNotices.value || !waitingNotices.value.length) return
  const follow = nearLatest()
  const latest = turns.value.at(-1)
  for (const data of waitingNotices.value) {
    notices.value.push({
      data,
      afterTurnId: latest?.id ?? 0,
      related: data.stage !== 'answer' && latest?.referenceId === data.threadId,
      status: 'active'
    })
  }
  waitingNotices.value = []
  if (follow) void scrollToLatest()
}
function updateNotices(items: TodoAssistantNotice[]) {
  const active = new Map(items.map(notice => [notice.id, notice]))
  for (const entry of notices.value) {
    const current = active.get(entry.data.id)
    if (current) {
      entry.data = current
      if (entry.status === 'resolved') entry.status = 'active'
      active.delete(entry.data.id)
    } else if (entry.status === 'active') {
      entry.status = 'resolved'
    }
  }
  waitingNotices.value = [...active.values()]
}
async function refresh() {
  if (refreshInFlight) return
  refreshInFlight = true
  const version = revision
  try {
    const [messages, pendingNotices] = await Promise.allSettled([listAssistantTurns(), listAssistantNotices()])
    if (version !== revision || !timer) return
    const nearEnd = nearLatest()
    if (messages.status === 'fulfilled') {
      const items = messages.value.data
      if (!turns.value.length) hasEarlier.value = items.length === 30
      merge(items)
      if (pendingNotices.status === 'fulfilled') updateNotices(pendingNotices.value.data)
      publishNotices()
      if (nearEnd) void scrollToLatest()
    }
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
function startReply(entry: NoticeEntry) {
  if (sending.value || replyTarget.value || entry.status !== 'active') return
  replyTargetId.value = entry.data.id
  void nextTick(() => input.value?.focus())
}
function cancelReply() {
  replyTargetId.value = undefined
  replyDraft.value = ''
  void nextTick(() => input.value?.focus())
}
async function sendNoticeReply(entry: NoticeEntry) {
  const answer = answerPayload.value
  if (!answer) return
  const payload = JSON.stringify(answer)
  if (lastNoticeAnswer.value?.payload !== payload) lastNoticeAnswer.value = { key: newRequestId(), payload }
  sending.value = true
  try {
    await answerAssistantNotice({ ...answer, requestId: lastNoticeAnswer.value.key })
    revision++
    entry.status = 'answered'
    entry.answer = answer.content
    lastNoticeAnswer.value = undefined
    cancelReply()
  } catch {
    // 保留回复及请求标识，用户可用原内容重试。
  } finally {
    sending.value = false
  }
}
async function retryNotice(entry: NoticeEntry) {
  const { id, ticketId } = entry.data
  if (entry.status !== 'active' || retryingNotices.value.has(id)) return
  retryingNotices.value.add(id)
  try {
    await retryTicket(ticketId)
    revision++
    entry.status = 'retried'
  } catch {
    // 请求层统一提示，下一次轮询复核工单状态。
  } finally {
    retryingNotices.value.delete(id)
  }
}
async function send() {
  if (!canSend.value || sending.value || composing.value) return
  if (replyTarget.value) {
    await sendNoticeReply(replyTarget.value)
    return
  }
  const content = draft.value.trim()
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
  if (replyTarget.value) return
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
  timer = setInterval(refresh, 3000)
  void refresh()
}
function stopPolling() {
  if (timer) clearInterval(timer)
  timer = undefined
  revision++
}

// --------------------------------- page init ---------------------------------
watch(canPublishNotices, (ready) => {
  if (ready) publishNotices()
})
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
.assistant-turn:has(+ .assistant-related-notice) { margin-bottom: 8px; }
.assistant-related-notice { margin-inline-start: 40px; }
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
.assistant-composer-actions > .el-button:last-child { margin-inline-start: auto; }
.assistant-reply-context { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.assistant-reply-context label { font-weight: 600; color: var(--el-text-color-primary); overflow-wrap: anywhere; }
.assistant-actions :deep(.el-button + .el-button), .assistant-switch :deep(.el-button + .el-button) { margin-left: 0; }
@media (max-width: 600px) {
  .assistant-workspace { padding: 16px 10px; }
  .assistant-welcome { padding-block: 32px; }
  .assistant-composer :deep(textarea) { font-size: 16px; }
  .assistant-composer-actions :deep(.el-button) { min-height: 44px; }
}
</style>
