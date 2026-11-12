<!--
  对话内的待办提问或失败消息

  @author xxl-ai 2026-11-12
-->
<template>
  <div class="assistant-notice">
    <div class="notice-heading">
      <strong>{{ notice.title }}</strong>
      <el-tag size="small" :type="status === 'active' ? stageType(notice.stage) : 'info'">
        {{ status === 'active' ? stageLabel(notice.stage) : t(statusLabel[status]) }}
      </el-tag>
    </div>
    <p v-if="notice.stage === 'answer'" class="notice-content">
      {{ notice.content || t('business.todo.assistant.noticeQuestionFallback') }}
    </p>
    <p
      v-else
      :id="'notice-reason-' + notice.id"
      class="notice-content"
      :class="{ 'notice-reason-preview': !expanded }"
    >{{ notice.content || t('business.todo.assistant.noticeNoReason') }}</p>
    <div v-if="status === 'active' || (notice.stage !== 'answer' && notice.content)" class="notice-actions">
      <el-button
        v-if="notice.stage === 'answer'"
        text type="primary"
        :disabled="replyDisabled"
        :aria-label="t('business.todo.assistant.noticeReplyTo', [notice.title])"
        @click="emit('reply')"
      >{{ t('business.todo.assistant.noticeReply') }}</el-button>
      <template v-else>
        <el-button
          v-if="notice.content"
          text
          :aria-expanded="expanded"
          :aria-controls="'notice-reason-' + notice.id"
          @click="expanded = !expanded"
        >{{ t(expanded ? 'business.todo.assistant.noticeHideDetails' : 'business.todo.assistant.noticeShowDetails') }}</el-button>
        <el-button
          v-if="status === 'active' && notice.canRetry"
          text type="primary"
          :loading="retrying"
          @click="emit('retry')"
        >{{ t('business.todo.retry') }}</el-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { t } from '@/i18n'
import { stageLabel, stageType } from '../composables/useTodoLabel'
import type { TodoAssistantNotice } from '../types'

defineProps<{
  notice: TodoAssistantNotice
  status: 'active' | 'answered' | 'retried' | 'resolved'
  replyDisabled: boolean
  retrying: boolean
}>()
const emit = defineEmits<{ reply: []; retry: [] }>()
const expanded = ref(false)
const statusLabel = {
  answered: 'business.todo.assistant.noticeAnswered',
  retried: 'business.todo.assistant.noticeRetried',
  resolved: 'business.todo.assistant.noticeResolved'
} as const
</script>

<style scoped lang="scss">
.assistant-notice {
  display: grid;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.notice-heading, .notice-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.notice-heading :deep(.el-tag) { --el-tag-text-color: var(--el-text-color-primary); }
.notice-content { margin: 0; line-height: 1.55; white-space: pre-wrap; }
.notice-reason-preview {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}
.notice-actions .el-button + .el-button { margin-left: 0; }
@media (max-width: 600px) {
  .notice-actions .el-button { min-height: 44px; }
}
</style>
