<!--
  Skill 上传弹窗，提供文件选择和导入结果入口。
  @author xxl-ai 2026-11-11
-->
<template>
  <el-dialog
    v-model="visible"
    :title="t('business.skill.upload')"
    width="min(520px, 92vw)"
    :show-close="!uploading"
    :close-on-click-modal="!uploading"
    :close-on-press-escape="!uploading"
    append-to-body
    @close="resetUpload"
  >
    <template v-if="imported.length">
      <p class="upload-summary" role="status">{{ t('business.skill.uploadSuccess', [imported.length]) }}</p>
      <ul class="imported-skills">
        <li v-for="skill in imported" :key="skill.id">
          <span>{{ skill.name }}</span>
          <el-button link type="primary" @click="openContent(skill)">{{ t('business.skill.contentManage') }}</el-button>
        </li>
      </ul>
    </template>
    <el-upload
      v-else
      v-model:file-list="files"
      drag
      accept=".md,.zip"
      :auto-upload="false"
      :limit="1"
      :disabled="uploading"
      :on-change="validateFile"
      :on-exceed="handleExceed"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">{{ t('business.skill.uploadChoose') }}</div>
      <template #tip>
        <div class="el-upload__tip">{{ t('business.skill.uploadFormats') }}</div>
      </template>
    </el-upload>
    <template #footer>
      <el-button v-if="imported.length" @click="visible = false">{{ t('common.close') }}</el-button>
      <template v-else>
        <el-button :disabled="uploading" @click="visible = false">{{ t('modal.cancelButton') }}</el-button>
        <el-button type="primary" :disabled="!files.length" :loading="uploading" @click="submitUpload">
          {{ t('business.skill.uploadConfirm') }}
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadUserFile } from 'element-plus'
import { t } from '@/i18n'
import modal from '@/utils/modal'
import { uploadSkill } from '../api'
import type { Skill } from '../types'

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ imported: []; openContent: [skill: Skill] }>()
const maxMarkdownBytes = 2 * 1024 * 1024
const maxZipBytes = 8 * 1024 * 1024

// --------------------------------- ref data ---------------------------------
const files = ref<UploadUserFile[]>([])
const imported = ref<Skill[]>([])
const uploading = ref(false)

// --------------------------------- fun ---------------------------------
function validateFile(file: UploadFile) {
  const limit = /\.md$/i.test(file.name) ? maxMarkdownBytes : maxZipBytes
  if (!file.raw || !/\.(md|zip)$/i.test(file.name) || file.raw.size === 0 || file.raw.size > limit) {
    files.value = []
    modal.msgError(t('business.skill.uploadInvalidFile'))
  }
}

function handleExceed() {
  modal.msgWarning(t('business.skill.uploadSingleFile'))
}

async function submitUpload() {
  const file = files.value[0]?.raw
  if (!file || uploading.value) return

  uploading.value = true
  try {
    const response = await uploadSkill(file)
    imported.value = response.data
    emit('imported')
  } catch {
    /* 请求拦截器已显示错误，保留所选文件供用户重试或替换。 */
  } finally {
    uploading.value = false
  }
}

function openContent(skill: Skill) {
  visible.value = false
  emit('openContent', skill)
}

function resetUpload() {
  files.value = []
  imported.value = []
}
</script>

<style scoped>
.upload-summary {
  margin: 0 0 12px;
  font-weight: 600;
}

.imported-skills {
  max-height: 240px;
  overflow: auto;
  margin: 0;
  padding: 0;
  list-style: none;
}

.imported-skills li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.imported-skills li span {
  min-width: 0;
  overflow-wrap: anywhere;
}
</style>
