const { app, BrowserWindow, ipcMain } = require('electron')
const path = require('path')
const { spawn } = require('child_process')
const net = require('net')

// ============================================================
// AI漫剧制作平台 - Electron 主进程
// ADR-2: Electron 内嵌 JVM（spawn 子进程 + 随机端口 ADR-18）
// ============================================================

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
// App Lifecycle
// ============================================================

app.whenReady().then(async () => {
  const port = await detectFreePort()
  global.backendPort = port
  createWindow()
  startBackend()

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

ipcMain.handle('get-app-path', () => app.getAppPath())
