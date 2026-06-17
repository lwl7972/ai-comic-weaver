const { app, BrowserWindow, ipcMain, Menu, shell } = require('electron')
const path = require('path')
const fs = require('fs')
const { spawn } = require('child_process')
const net = require('net')
const { autoUpdater } = require('electron-updater')

// ============================================================
// 本地日志系统
// ============================================================
const LOG_DIR = path.join(app.isPackaged ? path.dirname(app.getPath('exe')) : __dirname, '..', 'logs')
if (!fs.existsSync(LOG_DIR)) fs.mkdirSync(LOG_DIR, { recursive: true })

function logToFile(level, ...args) {
  const line = `[${new Date().toISOString()}] [${level}] ${args.join(' ')}\n`
  const logFile = path.join(LOG_DIR, `app-${new Date().toISOString().slice(0, 10)}.log`)
  try { fs.appendFileSync(logFile, line) } catch {}
  if (level === 'ERROR') console.error(...args)
  else console.log(...args)
}

// ============================================================
// AI 漫剧制作平台 - Electron 主进程
// ADR-2: Electron 内嵌 JVM（spawn 子进程 + 随机端口 ADR-18）
// ============================================================

// 配置自动更新器
autoUpdater.autoDownload = false
autoUpdater.autoInstallOnAppQuit = true
autoUpdater.autoRunAppAfterInstall = true

let mainWindow = null
let jvmProcess = null
let backendPort = 18081
let backendReady = false
let backendReadyFired = false
let startupTimeout = null
const JVM_STARTUP_TIMEOUT_MS = 45_000  // 45s 启动超时（优化后期待更快）
const MAX_JVM_RESTART_ATTEMPTS = 3
let jvmRestartAttempts = 0

function showLoadingScreen(win) {
  const logoPath = path.join(__dirname, '../assets/logo.png')
  let logoBase64 = ''
  try { logoBase64 = fs.readFileSync(logoPath).toString('base64') } catch {}
  const logoSrc = logoBase64 ? `data:image/png;base64,${logoBase64}` : ''

  win.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(`<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>AI 漫剧</title>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:#0f0f1a; color:#e2e8f0; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans SC',sans-serif;
         display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; user-select:none; }
  .logo { width:80px; height:80px; border-radius:16px; margin-bottom:24px; }
  .title { font-size:28px; font-weight:700; letter-spacing:2px; margin-bottom:12px; }
  .subtitle { font-size:14px; color:#94a3b8; margin-bottom:40px; }
  .spinner { width:36px; height:36px; border:3px solid #1e293b; border-top-color:#6366f1; border-radius:50%;
             animation:spin 0.8s linear infinite; margin-bottom:16px; }
  @keyframes spin { to { transform:rotate(360deg); } }
  .status { font-size:13px; color:#64748b; }
  .error { color:#ef4444; font-size:13px; margin-top:12px; display:none; max-width:400px; text-align:center; }
</style></head>
<body>
  ${logoSrc ? `<img class="logo" src="${logoSrc}" />` : '<div class="logo" style="background:#6366f1;border-radius:16px;display:flex;align-items:center;justify-content:center;font-size:32px;">🎬</div>'}
  <div class="title">AI 漫剧</div>
  <div class="subtitle">AI 漫剧制作平台</div>
  <div class="spinner"></div>
  <div class="status">正在启动后端服务...</div>
  <div class="error" id="error"></div>
  <script>
    if (window.electronAPI) {
      window.electronAPI.onBackendError(function(data) {
        var el = document.getElementById('error');
        if (el) { el.style.display='block'; el.textContent = data.message || '后端启动失败'; }
      });
    }
  </script>
</body></html>`)}`)
}

function loadApp(win) {
  if (process.env.NODE_ENV === 'development' || !app.isPackaged) {
    win.loadURL('http://localhost:5173')
  } else {
    win.loadFile(path.join(__dirname, '../frontend/dist/index.html'))
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 700,
    title: 'AI 漫剧',
    icon: path.join(__dirname, '../assets/logo.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    show: false,
  })

  showLoadingScreen(mainWindow)

  mainWindow.on('ready-to-show', () => {
    mainWindow.show()
  })

  // F12 切换开发者工具（所有模式可用）
  mainWindow.webContents.on('before-input-event', (event, input) => {
    if (input.key === 'F12' && input.type === 'keyDown') {
      event.preventDefault()
      mainWindow.webContents.toggleDevTools()
    }
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  // 创建应用菜单栏
  createApplicationMenu()
}

/**
 * 创建应用菜单栏（包含 GitHub 跳转）
 */
function createApplicationMenu() {
  const isMac = process.platform === 'darwin'
  
  const helpMenu = {
    label: '帮助',
    submenu: [
      {
        label: 'GitHub 仓库',
        click: () => {
          shell.openExternal('https://github.com/lwl7972/ai-comic-weaver')
        },
      },
      {
        label: 'Releases',
        click: () => {
          shell.openExternal('https://github.com/lwl7972/ai-comic-weaver/releases')
        },
      },
      { type: 'separator' },
      {
        label: '关于 AI 漫剧',
        click: () => {
          const { dialog } = require('electron')
          dialog.showMessageBox(mainWindow, {
            type: 'info',
            title: '关于 AI 漫剧',
            message: 'AI 漫剧制作平台',
            detail: `版本：${app.getVersion()}\n\n基于 AI 的漫剧创作桌面应用\n支持剧本→角色→场景→分镜→导演→成片完整流水线`,
            buttons: ['确定'],
          })
        },
      },
    ],
  }

  const menuTemplate = [
    ...(isMac ? [{
      label: 'AI 漫剧',
      submenu: [
        { role: 'about' },
        { type: 'separator' },
        { role: 'quit' },
      ],
    }] : []),
    {
      label: '编辑',
      submenu: [
        { role: 'undo' },
        { role: 'redo' },
        { type: 'separator' },
        { role: 'cut' },
        { role: 'copy' },
        { role: 'paste' },
        { role: 'pasteAndMatchStyle' },
        { role: 'delete' },
        { role: 'selectAll' },
      ],
    },
    ...(!isMac ? [{
      label: '帮助',
      submenu: helpMenu.submenu,
    }] : [helpMenu]),
  ]

  const menu = Menu.buildFromTemplate(menuTemplate)
  Menu.setApplicationMenu(menu)
}

// ============================================================
// JVM 后端启动 (ADR-18: spawn 子进程 + 随机端口)
// ============================================================

function getDevJarPath() {
  const targetDir = path.join(__dirname, '..', 'backend', 'target')
  const jars = fs.readdirSync(targetDir).filter(f => f.startsWith('ai-comic-platform-') && f.endsWith('.jar'))
  return jars.length > 0 ? path.join('..', 'backend', 'target', jars[0]) : '../backend/target/ai-comic-platform.jar'
}

function startBackend() {
  const jarPath = path.join(
    process.resourcesPath || __dirname,
    app.isPackaged ? 'backend/ai-comic-platform.jar' : getDevJarPath()
  )

  // 数据目录：打包后放在用户目录下，开发模式用项目根目录
  const dataDir = app.isPackaged
    ? path.join(app.getPath('home'), '.ai-comic', 'data')
    : path.join(__dirname, '..', 'data')

  // ============================================================
  // JVM 启动参数优化（加速启动 30-50%）
  // ============================================================
  const jvmArgs = [
    // --- 内存设置 ---
    '-Xms128m',            // 初始堆 128MB（桌面应用足够）
    '-Xmx512m',            // 最大堆 512MB
    '-XX:MetaspaceSize=64m',
    '-XX:MaxMetaspaceSize=128m',

    // --- GC 优化（G1GC 适合桌面应用的低延迟需求） ---
    '-XX:+UseG1GC',
    '-XX:MaxGCPauseMillis=100',
    '-XX:G1HeapRegionSize=4m',

    // --- JIT 编译优化（限制到 C1，大幅加速启动） ---
    // C2 编译器用于峰值性能，桌面应用用 C1 即可
    '-XX:TieredStopAtLevel=1',

    // --- 类加载优化 ---
    '-XX:+UseStringDeduplication',
    '-XX:+OptimizeStringConcat',

    // --- 杂项 ---
    '-Djava.awt.headless=true',
    '-Dfile.encoding=UTF-8',
    '-Dspring.jmx.enabled=false',
    '-Dspring.main.banner-mode=off',
    '-Dspring.main.lazy-initialization=true',

    // 跳过不必要的字节码验证（JVM 运行中已验证过）
    '-noverify',

    // 快速退出（不等待后台线程）
    '-Dspring.lifecycle.timeout-per-shutdown-phase=5s',
  ]

  const appArgs = [
    '-jar', jarPath,
    `--server.port=${backendPort}`,
    `--data-dir=${dataDir}`,
  ]

  console.log(`[Electron] Starting JVM backend on port ${backendPort}...`)
  console.log(`[Electron] JAR path: ${jarPath}`)
  console.log(`[Electron] Data dir: ${dataDir}`)

  const allArgs = [...jvmArgs, ...appArgs]

  jvmProcess = spawn('java', allArgs, {
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
      backendReadyFired = true
      clearStartupTimeout()
      console.log('[Electron] Backend started successfully, running HTTP health check...')
      waitForBackendReady(backendPort, mainWindow)
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

function waitForBackendReady(port, win, retries = 0) {
  const http = require('http')
  const req = http.get(`http://127.0.0.1:${port}/api/v1/app-config`, { timeout: 1500 }, (res) => {
    if (res.statusCode === 200 || res.statusCode === 401) {
      backendReady = true
      console.log('[Electron] Backend HTTP ready')
      win.webContents.send('backend-ready', { port })
      loadApp(win)
    } else {
      retryOrFail()
    }
  })
  req.on('error', () => retryOrFail())
  req.on('timeout', () => { req.destroy(); retryOrFail() })

  function retryOrFail() {
    if (retries < 15) {
      // 前 5 次快速重试（200ms），后 10 次间隔递增
      const delay = retries < 5 ? 200 : Math.min(300 * (retries - 4), 1000)
      setTimeout(() => waitForBackendReady(port, win, retries + 1), delay)
    } else {
      console.error('[Electron] Backend HTTP check failed after retries')
      win.webContents.send('backend-error', { message: '后端启动超时', canRestart: true })
    }
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

let updateCheckInProgress = false

function setupAutoUpdater() {
  // 设置 GitHub 更新源（显式指定，增强可靠性）
  try {
    autoUpdater.setFeedURL({
      provider: 'github',
      owner: 'lwl7972',
      repo: 'ai-comic-weaver',
      releaseType: 'release'
    })
  } catch (err) {
    logToFile('ERROR', '[Updater] setFeedURL error:', err.message)
  }

  // Forward updater events to renderer
  autoUpdater.on('update-available', (info) => {
    logToFile('INFO', '[Updater] Update available:', info.version)
    mainWindow?.webContents.send('update-available', info)
  })

  autoUpdater.on('update-not-available', (info) => {
    logToFile('INFO', '[Updater] Already up to date:', info.version)
    mainWindow?.webContents.send('update-not-available', info)
  })

  autoUpdater.on('download-progress', (progress) => {
    logToFile('INFO', `[Updater] Download: ${progress.percent.toFixed(1)}%`)
    mainWindow?.webContents.send('download-progress', progress)
  })

  autoUpdater.on('update-downloaded', (info) => {
    logToFile('INFO', '[Updater] Update downloaded:', info.version)
    mainWindow?.webContents.send('update-downloaded', info)
  })

  autoUpdater.on('error', (err) => {
    logToFile('ERROR', '[Updater] Error:', err.message)
    mainWindow?.webContents.send('update-error', { message: err.message })
  })

  // Auto-check on startup (3s delay to let UI load)
  setTimeout(() => {
    if (!updateCheckInProgress) {
      updateCheckInProgress = true
      autoUpdater.checkForUpdates().catch((err) => {
        logToFile('WARN', '[Updater] Startup check skipped:', err.message)
        updateCheckInProgress = false
      })
    }
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

// 前端日志写入主进程日志文件
ipcMain.handle('log-to-file', (_event, level, message) => {
  logToFile(level.toUpperCase(), '[Renderer]', message)
})

// 获取日志目录路径
ipcMain.handle('get-log-dir', () => LOG_DIR)

// ============================================================
// Auto-updater IPC handlers
// ============================================================

ipcMain.handle('check-for-update', async () => {
  if (updateCheckInProgress) {
    return { available: false, error: '更新检查正在进行中' }
  }
  try {
    updateCheckInProgress = true
    logToFile('INFO', '[Updater] Starting update check...')

    // 带超时的更新检查（15秒）
    const result = await Promise.race([
      autoUpdater.checkForUpdates(),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error('更新检查超时，请检查网络后重试')), 15000)
      )
    ])

    updateCheckInProgress = false
    logToFile('INFO', '[Updater] Check result:', JSON.stringify(result?.updateInfo?.version || 'none'))
    return {
      available: !!result?.updateInfo,
      version: result?.updateInfo?.version,
      releaseDate: result?.updateInfo?.releaseDate,
      releaseNotes: result?.updateInfo?.releaseNotes
    }
  } catch (err) {
    updateCheckInProgress = false
    logToFile('ERROR', '[Updater] checkForUpdates error:', err.message)
    return { available: false, error: err.message || '更新检查失败' }
  }
})

ipcMain.handle('download-update', async () => {
  try {
    await autoUpdater.downloadUpdate()
    return { success: true }
  } catch (err) {
    console.error('[Updater] downloadUpdate error:', err.message)
    return { success: false, error: err.message }
  }
})

ipcMain.handle('quit-and-install', () => {
  console.log('[Updater] Quitting and installing update...')
  autoUpdater.quitAndInstall()
})
