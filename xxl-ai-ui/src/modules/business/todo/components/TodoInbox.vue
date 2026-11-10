<!--
  TodoInbox（超级助理收件箱）
  展示发给我的确认、提问、结果与通知；点开即标记已读，并可跳到对应待办
-->
<template>
  <el-drawer :model-value="modelValue" :title="t('business.todo.inbox')" size="420px" append-to-body @update:model-value="close">
    <div class="todo-inbox__bar">
      <el-switch v-model="unreadOnly" :active-text="t('business.todo.unreadOnly')" @change="handleFilter" />
      <el-button link type="primary" :disabled="!unreadIds.length" @click="markRead(unreadIds)">
        {{ t('business.todo.markAllRead') }}
      </el-button>
    </div>
    <div v-loading="loading">
      <div v-if="!list.length" class="todo-inbox__empty">{{ t('business.todo.inboxEmpty') }}</div>
      <div v-for="item in list" :key="item.id" class="todo-inbox__item" :class="{ 'is-unread': item.readStatus === 0 }" @click="handleOpen(item)">
        <div class="todo-inbox__head">
          <el-tag size="small" :type="item.purpose === 'attention' ? 'warning' : 'info'">{{ purposeLabel(item.purpose) }}</el-tag>
          <span class="todo-inbox__title">{{ item.ticketTitle }}</span>
          <span class="todo-inbox__time">{{ item.addTime }}</span>
        </div>
        <!-- 纯文本展示（不渲染 HTML） -->
        <div class="todo-inbox__content">{{ item.content }}</div>
      </div>
    </div>
    <Pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </el-drawer>
</template>

<script setup lang="ts">
defineOptions({ name: 'TodoInbox' })
import { t } from '@/i18n'
import { computed, ref, watch } from 'vue'
import { Pagination } from '@/components'
import { usePageParams } from '@/composables/usePageParams'
import { listMessage, readMessage } from '../api'
import { purposeLabel } from '../composables/useTodoLabel'
import type { TodoMessage, TodoMessageQuery } from '../types'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 打开待办 */
  open: [threadId: number]
  /** 已读变化，页面据此刷新未读数 */
  read: []
}>()

const queryParams = ref<TodoMessageQuery>({ pageNum: 1, pageSize: 10, readStatus: -1 })
const unreadOnly = ref(false) /* 只看未读 */
const list = ref<TodoMessage[]>([])
const total = ref(0)
const loading = ref(false)

/** 当前页未读消息ID */
const unreadIds = computed(() => list.value.filter((item) => item.readStatus === 0).map((item) => item.id))

function getList() {
  loading.value = true
  queryParams.value.readStatus = unreadOnly.value ? 0 : -1
  listMessage(usePageParams(queryParams)())
    .then((res) => {
      list.value = res.data.data
      total.value = res.data.total
    })
    .finally(() => {
      loading.value = false
    })
}
function handleFilter() {
  queryParams.value.pageNum = 1
  getList()
}
function markRead(ids: number[]) {
  if (!ids.length) return Promise.resolve()
  return readMessage(ids).then(() => {
    list.value.forEach((item) => {
      if (ids.includes(item.id)) item.readStatus = 1
    })
    emit('read')
  })
}
function handleOpen(item: TodoMessage) {
  const read = item.readStatus === 0 ? markRead([item.id]) : Promise.resolve()
  read.catch(() => {})
  if (item.threadId) {
    emit('open', item.threadId)
    close()
  }
}
function close() {
  emit('update:modelValue', false)
}

// 打开时加载
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) getList()
  }
)
</script>

<style scoped lang="scss">
.todo-inbox__bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.todo-inbox__empty {
  padding: 24px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.todo-inbox__item {
  padding: 10px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  &:hover {
    background: var(--el-fill-color-light);
  }
  &.is-unread .todo-inbox__title {
    font-weight: 600;
  }
}
.todo-inbox__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.todo-inbox__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.todo-inbox__time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.todo-inbox__content {
  margin-top: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
