/**
 * Electron Preload Script
 * 安全地暴露受限 API 给渲染进程 (contextIsolation: true)
 */

const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('electronAPI', {
  /** 获取后端 JVM 端口 */
  getBackendPort: () => ipcRenderer.invoke('get-backend-port'),

  /** 获取应用版本 */
  getAppVersion: () => ipcRenderer.invoke('app-version'),

  /** 获取 Electron / Chrome / Node 版本信息 */
  getVersions: () => ipcRenderer.invoke('app-versions'),

  /** 获取应用路径 */
  getAppPath: () => ipcRenderer.invoke('get-app-path'),

  /** 监听后端就绪事件 (返回 unsubscribe 函数) */
  onBackendReady: (callback) => {
    const handler = (_event, data) => callback(data)
    ipcRenderer.on('backend-ready', handler)
    return () => ipcRenderer.removeListener('backend-ready', handler)
  },

  /** 监听后端错误事件 (返回 unsubscribe 函数) */
  onBackendError: (callback) => {
    const handler = (_event, data) => callback(data)
    ipcRenderer.on('backend-error', handler)
    return () => ipcRenderer.removeListener('backend-error', handler)
  },

  // ============================================================
  // 自动更新 API
  // ============================================================

  /** 手动检查更新 */
  checkForUpdate: () => ipcRenderer.invoke('check-for-update'),

  /** 下载更新 */
  downloadUpdate: () => ipcRenderer.invoke('download-update'),

  /** 安装更新并重启 */
  quitAndInstall: () => ipcRenderer.invoke('quit-and-install'),

  /** 监听更新可用 (返回 unsubscribe 函数) */
  onUpdateAvailable: (callback) => {
    const handler = (_event, info) => callback(info)
    ipcRenderer.on('update-available', handler)
    return () => ipcRenderer.removeListener('update-available', handler)
  },

  /** 监听下载进度 (返回 unsubscribe 函数) */
  onDownloadProgress: (callback) => {
    const handler = (_event, progress) => callback(progress)
    ipcRenderer.on('download-progress', handler)
    return () => ipcRenderer.removeListener('download-progress', handler)
  },

  /** 监听更新已下载 (返回 unsubscribe 函数) */
  onUpdateDownloaded: (callback) => {
    const handler = (_event, info) => callback(info)
    ipcRenderer.on('update-downloaded', handler)
    return () => ipcRenderer.removeListener('update-downloaded', handler)
  },

  /** 监听更新错误 (返回 unsubscribe 函数) */
  onUpdateError: (callback) => {
    const handler = (_event, err) => callback(err)
    ipcRenderer.on('update-error', handler)
    return () => ipcRenderer.removeListener('update-error', handler)
  },

  /** 移除更新事件监听 */
  removeUpdateListeners: () => {
    ipcRenderer.removeAllListeners('update-available')
    ipcRenderer.removeAllListeners('download-progress')
    ipcRenderer.removeAllListeners('update-downloaded')
    ipcRenderer.removeAllListeners('update-error')
  },
})
