/**
 * SSE 客户端封装 - 连接后端 SseController 的实时事件流
 * 支持自动重连、事件分发、断线检测
 */

type SseEventHandler = (data: any) => void

interface SseOptions {
  /** 重连间隔(ms)，默认 3000 */
  reconnectInterval?: number
  /** 最大重连次数，默认 10 */
  maxReconnectAttempts?: number
}

class SseClient {
  private eventSource: EventSource | null = null
  private handlers: Map<string, SseEventHandler[]> = new Map()
  private reconnectAttempts = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private options: Required<SseOptions>
  private baseUrl: string

  constructor(options: SseOptions = {}) {
    this.options = {
      reconnectInterval: options.reconnectInterval ?? 3000,
      maxReconnectAttempts: options.maxReconnectAttempts ?? 10,
    }

    // Determine SSE base URL (same logic as http.ts)
    if ((window as any).electronAPI?.getBackendPort) {
      this.baseUrl = '' // Will be set on connect
    } else {
      this.baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    }
  }

  /**
   * 连接 SSE 端点
   */
  async connect(): Promise<void> {
    this.disconnect()

    let url: string
    if ((window as any).electronAPI?.getBackendPort) {
      const port = await (window as any).electronAPI.getBackendPort()
      url = `http://localhost:${port}/api/v1/sse/subscribe`
    } else {
      url = `${this.baseUrl}/v1/sse/subscribe`
    }

    this.eventSource = new EventSource(url)

    this.eventSource.onopen = () => {
      this.reconnectAttempts = 0
      console.log('[SSE] Connected')
    }

    this.eventSource.onerror = () => {
      console.warn('[SSE] Connection error, attempting reconnect...')
      this.eventSource?.close()
      this.eventSource = null
      this.tryReconnect()
    }

    // Register handlers for named events
    this.eventSource.addEventListener('task-progress', (e: MessageEvent) => {
      this.dispatch('task-progress', this.parseData(e.data))
    })

    this.eventSource.addEventListener('task-complete', (e: MessageEvent) => {
      this.dispatch('task-complete', this.parseData(e.data))
    })

    this.eventSource.addEventListener('task-failed', (e: MessageEvent) => {
      this.dispatch('task-failed', this.parseData(e.data))
    })

    this.eventSource.addEventListener('pipeline-advanced', (e: MessageEvent) => {
      this.dispatch('pipeline-advanced', this.parseData(e.data))
    })

    this.eventSource.addEventListener('dirty-warning', (e: MessageEvent) => {
      this.dispatch('dirty-warning', this.parseData(e.data))
    })

    // Default message handler
    this.eventSource.onmessage = (e: MessageEvent) => {
      this.dispatch('message', this.parseData(e.data))
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.eventSource) {
      this.eventSource.close()
      this.eventSource = null
    }
  }

  /**
   * 注册事件处理器
   * @returns 取消注册的函数
   */
  on(event: string, handler: SseEventHandler): () => void {
    if (!this.handlers.has(event)) {
      this.handlers.set(event, [])
    }
    this.handlers.get(event)!.push(handler)

    return () => {
      const list = this.handlers.get(event)
      if (list) {
        const idx = list.indexOf(handler)
        if (idx > -1) list.splice(idx, 1)
      }
    }
  }

  private dispatch(event: string, data: any): void {
    const list = this.handlers.get(event)
    if (list) {
      list.forEach(h => h(data))
    }
  }

  private tryReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) {
      console.error('[SSE] Max reconnect attempts reached')
      return
    }
    this.reconnectAttempts++
    console.log(`[SSE] Reconnecting (${this.reconnectAttempts}/${this.options.maxReconnectAttempts})...`)

    this.reconnectTimer = setTimeout(() => {
      this.connect()
    }, this.options.reconnectInterval)
  }

  private parseData(raw: string): any {
    try {
      return JSON.parse(raw)
    } catch {
      return raw
    }
  }
}

// Singleton
export const sseClient = new SseClient()

export default sseClient
