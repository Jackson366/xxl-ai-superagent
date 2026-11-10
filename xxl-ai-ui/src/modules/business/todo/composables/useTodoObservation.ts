/**
 * 运行事件增量订阅：游标去重、流分帧和断线重连
 *
 * @author Codex 2026-11-11
 */
import { ref, watch, onScopeDispose, onActivated, onDeactivated, type Ref } from 'vue'
import { openTodoEvents } from '../api'
import type { TodoEvent } from '../types'

export function useTodoObservation(threadId: Ref<number>) {
  const events = ref<TodoEvent[]>([])
  const connection = ref<'connecting' | 'live' | 'reconnecting'>('connecting')
  let controller: AbortController | undefined
  let retry: ReturnType<typeof setTimeout> | undefined
  let generation = 0
  let active = true

  /* 每次切任务或离开页面，中止读取和待重连定时器 */
  function stop() {
    generation++
    controller?.abort()
    controller = undefined
    clearTimeout(retry)
  }

  async function connect(version: number) {
    controller = new AbortController()
    const current = controller
    const signal = current.signal
    let lastFrame = Date.now()
    let stalled = false
    const watchdog = setInterval(() => {
      if (Date.now() - lastFrame > 15000) {
        stalled = true
        current.abort()
      }
    }, 5000)
    let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
    try {
      const cursor = events.value.at(-1)?.id ?? 0
      reader = await openTodoEvents(threadId.value, cursor, signal)
      const decoder = new TextDecoder()
      let pending = ''
      while (version === generation && !signal.aborted) {
        const chunk = await reader.read()
        if (chunk.done) break
        pending += decoder.decode(chunk.value, { stream: true }).replace(/\r/g, '')
        let boundary = pending.indexOf('\n\n')
        while (boundary >= 0) {
          lastFrame = Date.now()
          const frame = pending.slice(0, boundary)
          pending = pending.slice(boundary + 2)
          const kind = frame
            .split('\n')
            .find((line) => line.startsWith('event:'))
            ?.slice(6)
            .trim()
          const data = frame
            .split('\n')
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trimStart())
            .join('\n')
          if (kind === 'ping' || kind === 'event') connection.value = 'live'
          if (kind === 'event' && data) {
            const event = JSON.parse(data) as TodoEvent
            if (event.threadId === threadId.value && event.id > (events.value.at(-1)?.id ?? 0)) events.value.push(event)
          }
          boundary = pending.indexOf('\n\n')
        }
      }
    } catch {
      // 历史事件保留，继续从最后成功收到的游标补齐
    } finally {
      clearInterval(watchdog)
      if (reader) {
        await reader.cancel().catch(() => {})
        reader.releaseLock()
      }
      if (version === generation && (!signal.aborted || stalled) && active) {
        connection.value = 'reconnecting'
        retry = setTimeout(() => connect(version), 2000)
      }
    }
  }

  function restart(reset = false) {
    stop()
    if (reset) events.value = []
    if (!active) return
    connection.value = 'connecting'
    void connect(generation)
  }

  watch(threadId, () => restart(true), { immediate: true })
  onActivated(() => {
    if (!active) {
      active = true
      restart()
    }
  })
  onDeactivated(() => {
    active = false
    stop()
  })
  onScopeDispose(() => {
    active = false
    stop()
  })
  return { events, connection, reconnect: () => restart() }
}
