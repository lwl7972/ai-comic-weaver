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

  /** 监听后端就绪事件 */
  onBackendReady: (callback) =>
    ipcRenderer.on('backend-ready', (_event, data) => callback(data)),

  // ============================================================
  // 自动更新 API
  // ============================================================

  /** 手动检查更新 */
  checkForUpdate: () => ipcRenderer.invoke('check-for-update'),

  /** 下载更新 */
  downloadUpdate: () => ipcRenderer.invoke('download-update'),

  /** 安装更新并重启 */
  quitAndInstall: () => ipcRenderer.invoke('quit-and-install'),

  /** 监听更新可用 */
  onUpdateAvailable: (callback) =>
    ipcRenderer.on('update-available', (_event, info) => callback(info)),

  /** 监听下载进度 */
  onDownloadProgress: (callback) =>
    ipcRenderer.on('download-progress', (_event, progress) => callback(progress)),

  /** 监听更新已下载 */
  onUpdateDownloaded: (callback) =>
    ipcRenderer.on('update-downloaded', (_event, info) => callback(info)),

  /** 监听更新错误 */
  onUpdateError: (callback) =>
    ipcRenderer.on('update-error', (_event, err) => callback(err)),

  /** 移除更新事件监听 */
  removeUpdateListeners: () => {
    ipcRenderer.removeAllListeners('update-available')
    ipcRenderer.removeAllListeners('download-progress')
    ipcRenderer.removeAllListeners('update-downloaded')
    ipcRenderer.removeAllListeners('update-error')
  },
})
