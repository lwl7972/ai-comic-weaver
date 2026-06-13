import { onMounted, onUnmounted } from 'vue'

export interface ShortcutHandler {
  key: string
  handler: (event: KeyboardEvent) => void
  description?: string
}

/**
 * 快捷键管理 Composable
 * 提供全局快捷键注册和注销功能
 */
export function useShortcuts() {
  const handlers = new Map<string, ShortcutHandler>()

  function handleKeyDown(event: KeyboardEvent) {
    const key = formatKey(event)
    const handler = handlers.get(key)
    
    if (handler) {
      event.preventDefault()
      handler.handler(event)
    }
  }

  function formatKey(event: KeyboardEvent): string {
    const parts: string[] = []
    
    if (event.ctrlKey || event.metaKey) {
      parts.push('Ctrl')
    }
    if (event.shiftKey) {
      parts.push('Shift')
    }
    if (event.altKey) {
      parts.push('Alt')
    }
    
    parts.push(event.key.toUpperCase())
    
    return parts.join('+')
  }

  function register(shortcut: ShortcutHandler) {
    handlers.set(shortcut.key, shortcut)
  }

  function unregister(key: string) {
    handlers.delete(key)
  }

  function unregisterAll() {
    handlers.clear()
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKeyDown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeyDown)
    unregisterAll()
  })

  return {
    register,
    unregister,
    unregisterAll,
  }
}

/**
 * 常用快捷键配置
 */
export const CommonShortcuts = {
  SAVE: 'Ctrl+S',
  UNDO: 'Ctrl+Z',
  REDO: 'Ctrl+Shift+Z',
  DELETE: 'Delete',
  PLAY: 'Space',
  ZOOM_IN: 'Ctrl++',
  ZOOM_OUT: 'Ctrl+-',
  RESET_ZOOM: 'Ctrl+0',
} as const
