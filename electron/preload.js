/**
 * Electron Preload Script
 * 安全地暴露受限 API 给渲染进程 (contextIsolation: true)
 */

const { contextBridge, ipcRenderer } = require('contextbridge')

contextBridge.exposeInMainWorld('electronAPI', {
  /** 获取后端 JVM 端口 */
  getBackendPort: () => ipcRenderer.invoke('get-backend-port'),

  /** 获取应用版本 */
  getAppVersion: () => ipcRenderer.invoke('app-version'),

  /** 获取应用路径 */
  getAppPath: () => ipcRenderer.invoke('get-app-path'),

  /** 监听后端就绪事件 */
  onBackendReady: (callback) =>
    ipcRenderer.on('backend-ready', (_event, data) => callback(data),
})
