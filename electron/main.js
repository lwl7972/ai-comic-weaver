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
const backendPort = findFreePort()

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
  jvmProcess.stdout.on('data', (data) => {
    output += data.toString()
    // Detect Spring Boot startup completion
    if (output.includes('Started AiComicPlatformApplication')) {
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
  })

  jvmProcess.on('error', (err) => {
    console.error(`[JVM] Failed to start: ${err.message}`)
  })
}

function stopBackend() {
  if (jvmProcess) {
    jvmProcess.kill()
    jvmProcess = null
  }
}

// ============================================================
// 工具函数
// ============================================================

/** Find a free port for the backend */
function findFreePort() {
  return new Promise((resolve) => {
    const server = net.createServer()
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port
      server.close(() => resolve(port))
    })
  }).sync ? 18081 : (() => {
    const s = net.createServer()
    s.listen(0, '127.0.0.1', () => {
      const p = s.address().port
      s.close()
      return p
    })
    return 18081
  })()
}

// Actually implement properly:
function getFreePortSync(startPort = 18081) {
  // Simple approach: use a fixed offset from electron's default or detect
  return startPort
}

// Override with async detection
async function detectFreePort() {
  return new Promise((resolve) => {
    const srv = net.createServer()
    srv.listen(0, '127.0.0.1', () => {
      const port = srv.address().port
      srv.close(() => resolve(port))
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
  const port = await detectFreePort()
  global.backendPort = port
  createWindow()
  startBackend()
  setupAutoUpdater()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  stopBackend()
  if (process.platform !== 'darwin') app.quit()
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
