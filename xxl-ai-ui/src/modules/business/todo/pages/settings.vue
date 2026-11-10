<!--
  BusinessTodoSettings（超级助理设置）
  按块保存：模型（空间 → Router/Worker 模型）、执行（MCP、Skill、并发、暂停）、接单、提示词；每块一个保存按钮 + 历史
-->
<template>
  <div class="app-container">
    <div class="content-inner">
      <el-page-header :content="t('business.todo.set.title')" @back="router.push('/todo')" class="mb8" />

      <!-- 模型 -->
      <el-card shadow="never" class="todo-block">
        <template #header>
          <div class="todo-block__head">
            <span>{{ t('business.todo.set.modelBlock') }}</span>
            <span class="todo-muted">{{ versionText(model.version) }}</span>
            <span class="todo-spacer" />
            <el-button link type="primary" @click="openHistory('model')">{{ t('business.todo.set.history') }}</el-button>
            <el-button type="primary" size="small" :loading="saving === 'model'" @click="saveModel">{{ t('common.save') }}</el-button>
          </div>
        </template>
        <el-form label-width="140px">
          <el-form-item :label="t('business.todo.set.space')">
            <el-select v-model="model.content.spaceId" :placeholder="t('common.selectPlaceholder')" style="width: 320px" @change="handleSpaceChange">
              <el-option v-for="item in spaceOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <div class="todo-tip">{{ t('business.todo.set.spaceTip') }}</div>
          </el-form-item>
          <el-form-item :label="t('business.todo.set.routerModel')">
            <el-cascader v-model="routerModel" :options="modelOptions.suppliers || []" :props="cascaderProps" clearable style="width: 320px" />
          </el-form-item>
          <el-form-item :label="t('business.todo.set.workerModel')">
            <el-cascader v-model="workerModel" :options="modelOptions.suppliers || []" :props="cascaderProps" clearable style="width: 320px" />
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 执行 -->
      <el-card shadow="never" class="todo-block">
        <template #header>
          <div class="todo-block__head">
            <span>{{ t('business.todo.set.workerBlock') }}</span>
            <span class="todo-muted">{{ versionText(worker.version) }}</span>
            <span class="todo-spacer" />
            <el-button link type="primary" @click="openHistory('worker')">{{ t('business.todo.set.history') }}</el-button>
            <el-button type="primary" size="small" :loading="saving === 'worker'" @click="saveWorker">{{ t('common.save') }}</el-button>
          </div>
        </template>
        <el-form label-width="140px">
          <el-form-item v-if="!savedSpaceId">
            <span class="todo-tip">{{ t('business.todo.set.saveModelFirst') }}</span>
          </el-form-item>
          <el-form-item :label="t('business.todo.set.mcp')">
            <el-select v-model="worker.content.mcpIds" multiple clearable :disabled="!savedSpaceId" style="width: 480px">
              <el-option v-for="item in toolOptions.mcps || []" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('business.todo.set.skill')">
            <el-select v-model="worker.content.skillIds" multiple clearable :disabled="!savedSpaceId" style="width: 480px">
              <el-option v-for="item in toolOptions.skills || []" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <div class="todo-tip">{{ t('business.todo.set.toolTip') }}</div>
          </el-form-item>
          <el-form-item :label="t('business.todo.set.maxWorkers')">
            <el-input-number v-model="worker.content.maxWorkers" :min="1" :max="10" />
          </el-form-item>
          <el-form-item :label="t('business.todo.set.paused')">
            <el-switch v-model="worker.content.paused" />
            <div class="todo-tip">{{ t('business.todo.set.pausedTip') }}</div>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 接单 -->
      <el-card shadow="never" class="todo-block">
        <template #header>
          <div class="todo-block__head">
            <span>{{ t('business.todo.set.policyBlock') }}</span>
            <span class="todo-muted">{{ versionText(policy.version) }}</span>
            <span class="todo-spacer" />
            <el-button link type="primary" @click="openHistory('policy')">{{ t('business.todo.set.history') }}</el-button>
            <el-button type="primary" size="small" :loading="saving === 'policy'" @click="savePolicy">{{ t('common.save') }}</el-button>
          </div>
        </template>
        <el-form label-width="140px">
          <el-form-item :label="t('business.todo.set.mode')">
            <el-radio-group v-model="policy.content.mode">
              <el-radio value="off">{{ t('business.todo.set.modeOff') }}</el-radio>
              <el-radio value="observe">{{ t('business.todo.set.modeObserve') }}</el-radio>
              <el-radio value="auto">{{ t('business.todo.set.modeAuto') }}</el-radio>
            </el-radio-group>
            <div class="todo-tip">{{ t('business.todo.set.modeTip') }}</div>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 提示词 -->
      <el-card shadow="never" class="todo-block">
        <template #header>
          <div class="todo-block__head">
            <span>{{ t('business.todo.set.promptsBlock') }}</span>
            <span class="todo-muted">{{ versionText(prompts.version) }}</span>
            <span class="todo-spacer" />
            <el-button link type="primary" @click="openHistory('prompts')">{{ t('business.todo.set.history') }}</el-button>
            <el-button type="primary" size="small" :loading="saving === 'prompts'" @click="savePrompts">{{ t('common.save') }}</el-button>
          </div>
        </template>
        <el-form label-width="140px">
          <el-form-item :label="t('business.todo.set.routerPrompt')">
            <el-input v-model="prompts.content.router" type="textarea" :rows="6" maxlength="8000" show-word-limit />
            <el-button link type="primary" @click="prompts.content.router = prompts.defaults?.router">{{ t('business.todo.set.useDefault') }}</el-button>
          </el-form-item>
          <el-form-item :label="t('business.todo.set.workerPrompt')">
            <el-input v-model="prompts.content.worker" type="textarea" :rows="8" maxlength="8000" show-word-limit />
            <el-button link type="primary" @click="prompts.content.worker = prompts.defaults?.worker">{{ t('business.todo.set.useDefault') }}</el-button>
          </el-form-item>
          <el-form-item>
            <span class="todo-tip">{{ t('business.todo.set.promptTip') }}</span>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- 历史 -->
    <el-dialog v-model="history.visible" :title="t('business.todo.set.history')" width="720px" append-to-body>
      <el-table :data="history.list" max-height="480">
        <el-table-column :label="t('business.todo.set.versionLabel')" prop="version" width="80" align="center" />
        <el-table-column :label="t('common.addTime')" prop="addTime" width="170" align="center" />
        <el-table-column :label="t('common.content')">
          <template #default="scope">
            <pre class="todo-pre">{{ JSON.stringify(scope.row.content, null, 2) }}</pre>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'BusinessTodoSettings' })
import { t } from '@/i18n'
import { computed, ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import type { CascaderValue } from 'element-plus'
import modal from '@/utils/modal'
import { getSettings, getSettingsOptions, listSettingsHistory, saveSettings } from '../api'
import type {
  TodoModelSettings,
  TodoOption,
  TodoOptions,
  TodoPolicySettings,
  TodoPromptsSettings,
  TodoSettings,
  TodoSettingsBlock,
  TodoWorkerSettings
} from '../types'

/** 配置块编辑状态：版本号用于保存时防覆盖 */
interface BlockState<T> {
  version: number
  content: T
  defaults?: T
}

const router = useRouter()

// --------------------------------- ref data ---------------------------------
const model = ref<BlockState<TodoModelSettings>>({
  version: 0,
  content: { spaceId: 0, routerSupplierId: 0, routerModelId: 0, workerSupplierId: 0, workerModelId: 0 }
})
const worker = ref<BlockState<TodoWorkerSettings>>({ version: 0, content: { mcpIds: [], skillIds: [], maxWorkers: 3, paused: false } })
const policy = ref<BlockState<TodoPolicySettings>>({ version: 0, content: { mode: 'observe' } })
const prompts = ref<BlockState<TodoPromptsSettings>>({ version: 0, content: {} })
const spaceOptions = ref<TodoOption[]>([]) /* 超级助理空间 */
const modelOptions = ref<TodoOptions>({ spaces: [] }) /* 编辑中空间的模型 */
const toolOptions = ref<TodoOptions>({ spaces: [] }) /* 已保存空间的 MCP、Skill */
const savedSpaceId = ref(0) /* 已保存的超级助理空间（执行块工具只能从这里选） */
const saving = ref<TodoSettingsBlock>()
const history = ref<{ visible: boolean; list: TodoSettings[] }>({ visible: false, list: [] })

/** 级联：供应商 → 对话模型 */
const cascaderProps = { value: 'id', label: 'name', children: 'models' }

/** Router 模型：[供应商ID, 模型ID] */
const routerModel = computed({
  get: () => pair(model.value.content.routerSupplierId, model.value.content.routerModelId),
  set: (value: CascaderValue | null | undefined) => {
    const [supplierId, modelId] = unpair(value)
    model.value.content.routerSupplierId = supplierId
    model.value.content.routerModelId = modelId
  }
})
/** Worker 模型：[供应商ID, 模型ID] */
const workerModel = computed({
  get: () => pair(model.value.content.workerSupplierId, model.value.content.workerModelId),
  set: (value: CascaderValue | null | undefined) => {
    const [supplierId, modelId] = unpair(value)
    model.value.content.workerSupplierId = supplierId
    model.value.content.workerModelId = modelId
  }
})

// --------------------------------- fun ---------------------------------
function pair(supplierId: number, modelId: number): number[] {
  return supplierId > 0 && modelId > 0 ? [supplierId, modelId] : []
}
function unpair(value: CascaderValue | null | undefined): [number, number] {
  const list = Array.isArray(value) ? (value as number[]) : []
  return [Number(list[0] || 0), Number(list[1] || 0)]
}
function versionText(version: number): string {
  return version > 0 ? t('business.todo.set.version', [version]) : t('business.todo.set.neverSaved')
}
/** 读取一个配置块 */
function loadBlock<T>(block: TodoSettingsBlock, target: Ref<BlockState<T>>) {
  return getSettings<T>(block).then((res) => {
    target.value = { version: res.data.version, content: res.data.content, defaults: res.data.defaults }
  })
}
function loadModel() {
  return loadBlock('model', model).then(() => {
    savedSpaceId.value = model.value.content.spaceId
    loadModelOptions(model.value.content.spaceId)
    loadToolOptions(savedSpaceId.value)
  })
}
function loadModelOptions(spaceId: number) {
  if (!spaceId) {
    modelOptions.value = { spaces: [] }
    return
  }
  getSettingsOptions(spaceId).then((res) => {
    modelOptions.value = res.data
  })
}
function loadToolOptions(spaceId: number) {
  if (!spaceId) {
    toolOptions.value = { spaces: [] }
    return
  }
  getSettingsOptions(spaceId).then((res) => {
    toolOptions.value = res.data
  })
}
/** 换空间：模型须重新选择 */
function handleSpaceChange(spaceId: number) {
  routerModel.value = []
  workerModel.value = []
  loadModelOptions(spaceId)
}
/** 模板中的 ref 会自动解包，故在此包装，保证 save 收到的是 Ref 本身 */
function saveModel() {
  save('model', model)
}
function saveWorker() {
  save('worker', worker)
}
function savePolicy() {
  save('policy', policy)
}
function savePrompts() {
  save('prompts', prompts)
}
function save<T>(block: TodoSettingsBlock, target: Ref<BlockState<T>>) {
  saving.value = block
  saveSettings({ block, baseVersion: target.value.version, content: target.value.content })
    .then(() => {
      modal.msgSuccess(t('common.saveSuccess'))
      return block === 'model' ? loadModel() : loadBlock(block, target)
    })
    .catch(() => {})
    .finally(() => {
      saving.value = undefined
    })
}
function openHistory(block: TodoSettingsBlock) {
  listSettingsHistory(block).then((res) => {
    history.value = { visible: true, list: res.data }
  })
}

// --------------------------------- page init ---------------------------------
getSettingsOptions().then((res) => {
  spaceOptions.value = res.data.spaces
})
loadModel()
loadBlock('worker', worker)
loadBlock('policy', policy)
loadBlock('prompts', prompts)
</script>

<style scoped lang="scss">
.todo-block {
  margin-bottom: 16px;
}
.todo-block__head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.todo-spacer {
  flex: 1;
}
.todo-muted,
.todo-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.todo-tip {
  width: 100%;
  line-height: 1.6;
}
.todo-pre {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
}
</style>
