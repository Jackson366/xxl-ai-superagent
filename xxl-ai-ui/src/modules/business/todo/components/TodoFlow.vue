<!-- 真实请求关系图：节点可键盘选择，连线由业务 ID 推导；@author Codex 2026-11-11 -->
<template>
  <section class="todo-flow">
    <div class="todo-flow__heading">
      <h3>{{ t('business.todo.observe.flow') }}</h3>
      <span>{{ t('business.todo.observe.flowHint') }}</span>
    </div>
    <div class="todo-flow__scroll">
      <div ref="canvas" class="todo-flow__canvas">
        <svg class="todo-flow__edges" :width="size.width" :height="size.height" aria-hidden="true">
          <defs>
            <marker :id="arrowId" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
            </marker>
          </defs>
          <path
            v-for="(edge, index) in paths"
            :key="index"
            :d="edge.path"
            fill="none"
            :stroke-dasharray="edge.direct ? '4 4' : undefined"
            :class="{ 'todo-flow__edge--selected': edge.selected }"
            :marker-end="`url(#${arrowId})`"
          />
        </svg>
        <div v-for="(column, index) in columns" :key="index" class="todo-flow__column">
          <h4>
            {{ headings[index] }} <span>{{ column.length }}</span>
          </h4>
          <div v-if="!column.length" class="todo-flow__placeholder">{{ t('business.todo.observe.notReached') }}</div>
          <button
            v-for="node in column"
            :key="node.key"
            :data-node="node.key"
            type="button"
            class="todo-flow__node"
            :class="{ 'todo-flow__node--selected': selected === node.key }"
            :aria-pressed="selected === node.key"
            @click="select(node)"
          >
            <span class="todo-flow__node-head"
              ><el-icon><component :is="eventIcon(node.status)" /></el-icon><span>{{ node.label }}</span></span
            >
            <strong>{{ node.title }}</strong>
            <span class="todo-flow__node-status"
              ><el-tag :type="eventType(node.status)" size="small">{{ node.statusLabel }}</el-tag></span
            >
          </button>
        </div>
      </div>
    </div>
    <p class="todo-flow__legend">{{ t('business.todo.observe.edgeHint') }}</p>
    <div v-if="selectedNode" class="todo-flow__selection">
      <div>
        <strong>{{ selectedNode.label }}</strong
        ><el-button link @click="clear">{{ t('business.todo.observe.clearSelection') }}</el-button>
      </div>
      <p>{{ selectedNode.description }}</p>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount, useId } from 'vue'
import { t } from '@/i18n'
import { decisionLabel, stageLabel, assignmentStateLabel, reasonDisplay, triggerKindLabel } from '../composables/useTodoLabel'
import { eventType, eventIcon, statusLabel } from '../composables/useObservationLabel'
import type { TodoEvent, TodoThreadDetail } from '../types'

export interface FlowSelection {
  type: 'trigger' | 'eval' | 'ticket' | 'assignment'
  id: string
}
interface FlowNode extends FlowSelection {
  key: string
  label: string
  title: string
  description: string
  status: string
  statusLabel: string
}
const props = defineProps<{ detail: TodoThreadDetail; events: TodoEvent[] }>()
const emit = defineEmits<{ select: [selection: FlowSelection | undefined] }>()
const arrowId = `flow-${useId().replaceAll(':', '')}`
// --------------------------------- ref data ---------------------------------
const canvas = ref<HTMLElement>()
const selected = ref('')
const size = ref({ width: 800, height: 320 })
const paths = ref<{ path: string; selected: boolean; direct?: boolean }[]>([])
let observer: ResizeObserver | undefined
const headings = computed(() => [
  t('business.todo.observe.requests'),
  t('business.todo.observe.routing'),
  t('business.todo.observe.tickets'),
  t('business.todo.observe.assignments')
])
const columns = computed<FlowNode[][]>(() => {
  const { triggers, decisions, tickets } = props.detail
  const nodes: FlowNode[][] = [[], [], [], []]
  for (const item of triggers)
    nodes[0]!.push({
      key: `trigger-${item.id}`,
      type: 'trigger',
      id: String(item.id),
      label: t('business.todo.observe.requestId', [item.id]),
      title: item.content,
      description: item.content,
      status: 'success',
      statusLabel: triggerKindLabel(item.kind)
    })
  for (const item of decisions) {
    const model = item.routerDecision ? decisionLabel(item.routerDecision) : t('business.todo.observe.noModelSuggestion')
    nodes[1]!.push({
      key: `eval-${item.evalId}`,
      type: 'eval',
      id: item.evalId,
      label: t('business.todo.observe.decisionId', [item.id]),
      title: t('business.todo.observe.modelSuggestion', [model]),
      description: [
        t('business.todo.observe.finalDecision', [decisionLabel(item.kind)]),
        reasonDisplay(item.reasonCode, item.reasonText),
        item.scope || item.routerScope,
        t('business.todo.observe.policy', [item.policyVersion])
      ]
        .filter(Boolean)
        .join('\n'),
      status: item.kind,
      statusLabel: decisionLabel(item.kind)
    })
  }
  const evals = new Set(decisions.map((item) => item.evalId))
  for (const event of props.events) {
    if (event.evalId && !evals.has(event.evalId)) {
      evals.add(event.evalId)
      const latest = props.events.filter((item) => item.evalId === event.evalId).at(-1)!
      nodes[1]!.push({
        key: `eval-${event.evalId}`,
        type: 'eval',
        id: event.evalId,
        label: t('business.todo.observe.router'),
        title: event.evalId,
        description: latest.detail || t('business.todo.observe.routingHint'),
        status: latest.status,
        statusLabel: statusLabel(latest.status)
      })
    }
  }
  for (const ticket of tickets) {
    nodes[2]!.push({
      key: `ticket-${ticket.id}`,
      type: 'ticket',
      id: String(ticket.id),
      label: t('business.todo.observe.ticketId', [ticket.id]),
      title: ticket.title,
      description: ticket.scope || ticket.title,
      status: ['failed', 'blocked'].includes(ticket.stage) ? ticket.stage : ticket.stage === 'ended' ? 'success' : 'running',
      statusLabel: stageLabel(ticket.stage)
    })
    for (const assignment of ticket.assignments)
      nodes[3]!.push({
        key: `assignment-${assignment.id}`,
        type: 'assignment',
        id: String(assignment.id),
        label: t('business.todo.observe.workerId', [assignment.id]),
        title: assignment.reportSummary || t('business.todo.observe.noReport'),
        description: assignment.reportSummary || t('business.todo.observe.noReport'),
        status: assignment.state === 'ended' ? 'ended' : assignment.state === 'pending' ? 'queued' : assignment.reportState || 'running',
        statusLabel: assignmentStateLabel(assignment.state)
      })
  }
  return nodes
})
const selectedNode = computed(() => columns.value.flat().find((node) => node.key === selected.value))
const edges = computed(() => {
  const list: { from: string; to: string; direct?: boolean }[] = []
  for (const trigger of props.detail.triggers) {
    for (const ticketId of trigger.fanout) list.push({ from: `trigger-${trigger.id}`, to: `ticket-${ticketId}`, direct: true })
  }
  for (const decision of props.detail.decisions) {
    for (const id of decision.triggerIds) list.push({ from: `trigger-${id}`, to: `eval-${decision.evalId}` })
    if (decision.ticketId) list.push({ from: `eval-${decision.evalId}`, to: `ticket-${decision.ticketId}` })
  }
  for (const event of props.events.filter((item) => item.kind === 'router.queued' && item.evalId)) {
    try {
      const ids = (JSON.parse(event.detail || '{}') as { triggerIds?: number[] }).triggerIds || []
      for (const id of ids)
        if (!list.some((edge) => edge.from === `trigger-${id}` && edge.to === `eval-${event.evalId}`))
          list.push({ from: `trigger-${id}`, to: `eval-${event.evalId}` })
    } catch {
      /* 历史事件可能没有结构化详情 */
    }
  }
  for (const ticket of props.detail.tickets)
    for (const assignment of ticket.assignments) list.push({ from: `ticket-${ticket.id}`, to: `assignment-${assignment.id}` })
  return list
})
// --------------------------------- fun ---------------------------------
function select(node: FlowNode) {
  selected.value = node.key
  emit('select', { type: node.type, id: node.id })
}
function clear() {
  selected.value = ''
  emit('select', undefined)
}
async function draw() {
  await nextTick()
  const element = canvas.value
  if (!element) return
  const rect = element.getBoundingClientRect()
  size.value = { width: element.clientWidth, height: element.clientHeight }
  const positions = new Map(
    Array.from(element.querySelectorAll<HTMLElement>('[data-node]')).map((node) => [node.dataset.node, node.getBoundingClientRect()])
  )
  paths.value = edges.value.flatMap((edge) => {
    const from = positions.get(edge.from),
      to = positions.get(edge.to)
    if (!from || !to) return []
    if (element.clientWidth < 600) {
      const x1 = from.right - rect.left,
        y1 = from.top + from.height / 2 - rect.top
      const x2 = to.right - rect.left,
        y2 = to.top + to.height / 2 - rect.top
      const side = element.clientWidth - 4
      return [
        {
          path: `M${x1},${y1} C${side},${y1} ${side},${y2} ${x2},${y2}`,
          selected: edge.from === selected.value || edge.to === selected.value,
          direct: edge.direct
        }
      ]
    }
    const x1 = from.right - rect.left,
      y1 = from.top + from.height / 2 - rect.top
    const x2 = to.left - rect.left,
      y2 = to.top + to.height / 2 - rect.top
    const middle = (x1 + x2) / 2
    return [
      {
        path: `M${x1},${y1} C${middle},${y1} ${middle},${y2} ${x2},${y2}`,
        selected: edge.from === selected.value || edge.to === selected.value,
        direct: edge.direct
      }
    ]
  })
}
// --------------------------------- page init ---------------------------------
watch([columns, edges, selected], draw, { deep: true })
onMounted(() => {
  observer = new ResizeObserver(draw)
  if (canvas.value) observer.observe(canvas.value)
  void draw()
})
onBeforeUnmount(() => observer?.disconnect())
</script>
<style scoped lang="scss">
.todo-flow__heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 16px;
  h3 {
    font-size: 15px;
    margin: 0;
  }
  span {
    font-size: 12px;
    color: var(--el-text-color-regular);
  }
}
.todo-flow__scroll {
  overflow-x: auto;
  margin-top: 16px;
  padding: 4px;
}
.todo-flow__canvas {
  min-width: 800px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 40px;
  position: relative;
  padding: 0 0 8px;
}
.todo-flow__edges {
  position: absolute;
  inset: 0;
  pointer-events: none;
  color: var(--el-text-color-placeholder);
  path {
    stroke: currentColor;
    stroke-width: 1.5;
  }
  .todo-flow__edge--selected {
    stroke: var(--el-color-primary);
    stroke-width: 2.5;
  }
}
.todo-flow__column {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  h4 {
    font-size: 12px;
    font-weight: 400;
    color: var(--el-text-color-regular);
    margin: 0 0 8px;
    span {
      margin-left: 8px;
    }
  }
}
.todo-flow__node {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
  font: inherit;
  cursor: pointer;
  min-height: 128px;
  strong {
    font-size: 13px;
    line-height: 1.6;
    font-weight: 400;
    overflow-wrap: anywhere;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &:hover,
  &:focus-visible,
  &--selected {
    border-color: var(--el-color-primary);
    outline: 2px solid var(--el-color-primary-light-8);
    outline-offset: 1px;
  }
}
.todo-flow__node-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.todo-flow__node-status {
  margin-top: auto;
}
.todo-flow__placeholder {
  font-size: 12px;
  color: var(--el-text-color-regular);
  padding: 24px 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
}
.todo-flow__legend {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.todo-flow__selection {
  margin-top: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 16px;
  font-size: 13px;
  > div {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  p {
    white-space: pre-wrap;
    line-height: 1.7;
    overflow-wrap: anywhere;
    margin-bottom: 0;
  }
}
@media (max-width: 600px) {
  .todo-flow__canvas {
    min-width: 0;
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .todo-flow__column {
    padding-right: 24px;
  }
  .todo-flow__node {
    min-height: 104px;
  }
}
</style>
