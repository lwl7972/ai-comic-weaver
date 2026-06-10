import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface AppNotification {
  id: string
  type: 'success' | 'warning' | 'error' | 'info'
  title: string
  message?: string
  duration?: number
  timestamp: number
}

/**
 * 全局通知 Store - 管理应用内通知和 SSE 推送消息
 */
export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<AppNotification[]>([])
  let idCounter = 0
  const pendingTimers: Map<string, ReturnType<typeof setTimeout>> = new Map()

  function add(notification: Omit<AppNotification, 'id' | 'timestamp'>) {
    const id = `notif-${++idCounter}`
    const entry: AppNotification = {
      ...notification,
      id,
      timestamp: Date.now(),
      duration: notification.duration ?? 5000,
    }
    notifications.value.unshift(entry)

    // Auto-remove after duration
    if (entry.duration && entry.duration > 0) {
      const timer = setTimeout(() => remove(id), entry.duration)
      pendingTimers.set(id, timer)
    }
    return id
  }

  function remove(id: string) {
    const timer = pendingTimers.get(id)
    if (timer) {
      clearTimeout(timer)
      pendingTimers.delete(id)
    }
    notifications.value = notifications.value.filter(n => n.id !== id)
  }

  /** 清理所有定时器（store 销毁时调用） */
  function cleanup() {
    pendingTimers.forEach((timer) => clearTimeout(timer))
    pendingTimers.clear()
    notifications.value = []
  }

  function success(title: string, message?: string) {
    return add({ type: 'success', title, message })
  }

  function warning(title: string, message?: string) {
    return add({ type: 'warning', title, message })
  }

  function error(title: string, message?: string) {
    return add({ type: 'error', title, message, duration: 8000 })
  }

  function info(title: string, message?: string) {
    return add({ type: 'info', title, message })
  }

  return { notifications, add, remove, cleanup, success, warning, error, info }
})
