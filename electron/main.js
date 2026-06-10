const { app, BrowserWindow, ipcMain } = require('electron')
const path = require('path')
const { spawn } = require('child_process')
const net = require('net')
const { autoUpdater } = require('electron-updater')

// ============================================================
// AI漫剧制作平台 - Electron 主进程
// ADR-2: Electron 内嵌 JVM（spawn 子进程 + 随机端口 ADR-18）
// ============================================================

// Configure auto-updater
autoUpdater.autoDownload = false
autoUpdater.autoInstallOnAppQuit = true

let mainWindow = null
let jvmProcess = null
let backendPort = 18081
let backendReady = false
let backendReadyFired = false
let startupTimeout = null
const JVM_STARTUP_TIMEOUT_MS = 60_000  // 60s 启动超时
const MAX_JVM_RESTART_ATTEMPTS = 3
let jvmRestartAttempts = 0

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 700,
    title: 'AI漫剧',
    icon: path.join(__dirname, '../assets/logo.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    show: false, // Show when ready-to-show to avoid white flash
  })

  // In dev, load from Vite dev server; in prod, load built files
  if (process.env.NODE_ENV === 'development' || !app.isPackaged) {
    mainWindow.loadURL('http://localhost:5173')
  } else {
    mainWindow.loadFile(path.join(__dirname, '../frontend/dist/index.html'))
  }

  mainWindow.on('ready-to-show', () => {
    mainWindow.show()
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

// ============================================================
// JVM 后端启动 (ADR-18: spawn 子进程 + 随机端口)
// ============================================================

function startBackend() {
  const jarPath = path.join(
    process.resourcesPath || __dirname,
    app.isPackaged ? 'backend/ai-comic-platform.jar' : '../backend/target/ai-comic-platform-0.1.0.jar'
  )

  const args = [
    '-jar', jarPath,
    `--server.port=${backendPort}`,
  ]

  console.log(`[Electron] Starting JVM backend on port ${backendPort}...`)
  console.log(`[Electron] JAR path: ${jarPath}`)

  jvmProcess = spawn('java', args, {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, RANDOM_PORT: String(backendPort) },
    detached: false,
  })

  let output = ''
  backendReady = false
  backendReadyFired = false

  jvmProcess.stdout.on('data', (data) => {
    const chunk = data.toString()
    output += chunk
    // 限制累积输出长度，避免内存泄漏
    if (output.length > 50000) {
      output = output.slice(-20000)
    }
    // 检测 Spring Boot 启动完成（仅触发一次）
    if (!backendReadyFired && output.includes('Started AiComicPlatformApplication')) {
      backendReady = true
      backendReadyFired = true
      clearStartupTimeout()
      console.log('[Electron] Backend started successfully')
      mainWindow?.webContents.send('backend-ready', { port: backendPort })
    }
  })

  jvmProcess.stderr.on('data', (data) => {
    console.error(`[JVM Error] ${data}`)
  })

  jvmProcess.on('exit', (code) => {
    console.log(`[JVM] Process exited with code ${code}`)
    jvmProcess = null
    clearStartupTimeout()
    // 通知前端后端已断开
    if (backendReady) {
      mainWindow?.webContents.send('backend-error', {
        message: `后端进程已退出 (code: ${code})`,
        canRestart: jvmRestartAttempts < MAX_JVM_RESTART_ATTEMPTS,
      })
    }
    // 自动重启（指数退避）
    if (code !== 0 && jvmRestartAttempts < MAX_JVM_RESTART_ATTEMPTS) {
      jvmRestartAttempts++
      const delay = Math.min(5000 * jvmRestartAttempts, 30000)
      console.log(`[JVM] 将在 ${delay}ms 后重启 (第 ${jvmRestartAttempts} 次重试)...`)
      setTimeout(() => startBackend(), delay)
    }
  })

  jvmProcess.on('error', (err) => {
    console.error(`[JVM] Failed to start: ${err.message}`)
    clearStartupTimeout()
    mainWindow?.webContents.send('backend-error', {
      message: `后端启动失败: ${err.message}`,
      canRestart: false,
    })
  })

  // 启动超时检测
  startupTimeout = setTimeout(() => {
    if (!backendReady) {
      console.error(`[JVM] 启动超时 (${JVM_STARTUP_TIMEOUT_MS}ms)`)
      mainWindow?.webContents.send('backend-error', {
        message: '后端启动超时，请检查 Java 环境',
        canRestart: true,
      })
    }
  }, JVM_STARTUP_TIMEOUT_MS)
}

function clearStartupTimeout() {
  if (startupTimeout) {
    clearTimeout(startupTimeout)
    startupTimeout = null
  }
}

function stopBackend() {
  clearStartupTimeout()
  jvmRestartAttempts = MAX_JVM_RESTART_ATTEMPTS  // 阻止自动重启
  if (jvmProcess) {
    jvmProcess.kill()
    jvmProcess = null
  }
}

// ============================================================
// 工具函数
// ============================================================

/** 异步检测空闲端口（含错误处理和超时） */
async function detectFreePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer()
    const timeout = setTimeout(() => {
      srv.close()
      reject(new Error('端口检测超时'))
    }, 5000)

    srv.on('error', (err) => {
      clearTimeout(timeout)
      reject(err)
    })

    srv.listen(0, '127.0.0.1', () => {
      const port = srv.address().port
      srv.close(() => {
        clearTimeout(timeout)
        resolve(port)
      })
    })
  })
}

// ============================================================
// 自动更新 (electron-updater → GitHub Releases)
// ============================================================

function setupAutoUpdater() {
  // Forward updater events to renderer
  autoUpdater.on('update-available', (info) => {
    console.log('[Updater] Update available:', info.version)
    mainWindow?.webContents.send('update-available', info)
  })

  autoUpdater.on('update-not-available', (info) => {
    console.log('[Updater] Already up to date:', info.version)
    mainWindow?.webContents.send('update-not-available', info)
  })

  autoUpdater.on('download-progress', (progress) => {
    console.log(`[Updater] Download: ${progress.percent.toFixed(1)}%`)
    mainWindow?.webContents.send('download-progress', progress)
  })

  autoUpdater.on('update-downloaded', (info) => {
    console.log('[Updater] Update downloaded:', info.version)
    mainWindow?.webContents.send('update-downloaded', info)
  })

  autoUpdater.on('error', (err) => {
    console.error('[Updater] Error:', err.message)
    mainWindow?.webContents.send('update-error', { message: err.message })
  })

  // Auto-check on startup (3s delay to let UI load)
  setTimeout(() => {
    autoUpdater.checkForUpdates().catch((err) => {
      console.log('[Updater] Startup check skipped:', err.message)
    })
  }, 3000)
}

// ============================================================
// App Lifecycle
// ============================================================

app.whenReady().then(async () => {
  try {
    backendPort = await detectFreePort()
  } catch (err) {
    console.error('[Electron] 端口检测失败，使用默认端口:', err.message)
    backendPort = 18081
  }
  global.backendPort = backendPort
  createWindow()
  startBackend()
  setupAutoUpdater()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
}).catch((err) => {
  console.error('[Electron] App initialization failed:', err)
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    stopBackend()
    app.quit()
  }
})

app.on('before-quit', () => {
  stopBackend()
})

// ============================================================
// IPC Handlers
// ============================================================

ipcMain.handle('get-backend-port', () => global.backendPort)

ipcMain.handle('app-version', () => app.getVersion())

ipcMain.handle('app-versions', () => ({
  electron: process.versions.electron,
  chrome: process.versions.chrome,
  node: process.versions.node,
  platform: process.platform,
}))

ipcMain.handle('get-app-path', () => app.getAppPath())

// ============================================================
// Auto-updater IPC handlers
// ============================================================

ipcMain.handle('check-for-update', async () => {
  try {
    const result = await autoUpdater.checkForUpdates()
    return { available: !!result, version: result?.updateInfo?.version || app.getVersion() }
  } catch (err) {
    return { available: false, error: err.message }
  }
})

ipcMain.handle('download-update', async () => {
  try {
    await autoUpdater.downloadUpdate()
    return { success: true }
  } catch (err) {
    return { success: false, error: err.message }
  }
})

ipcMain.handle('quit-and-install', () => {
  autoUpdater.quitAndInstall()
})
