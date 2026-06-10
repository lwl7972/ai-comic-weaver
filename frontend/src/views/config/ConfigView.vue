<template>
  <div class="config-container">
    <h2>配置中心</h2>
    <p class="description">模型配置、提示词模板管理、应用全局设置</p>

    <!-- 应用信息 -->
    <el-card class="config-card" shadow="hover">
      <template #header>
        <span>应用信息</span>
      </template>
      <el-descriptions :column="2" border size="default">
        <el-descriptions-item label="应用名称">AI漫剧</el-descriptions-item>
        <el-descriptions-item label="当前版本">v{{ appVersion }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ platformName }}</el-descriptions-item>
        <el-descriptions-item label="Electron">{{ electronVersion }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 更新检测 -->
    <el-card class="config-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>软件更新</span>
          <el-tag v-if="updateStatusTag" :type="updateStatusType" size="small">
            {{ updateStatusTag }}
          </el-tag>
        </div>
      </template>

      <div class="update-section">
        <!-- 空闲状态 -->
        <div v-if="updateState === 'idle'" class="update-idle">
          <p class="update-hint">点击下方按钮检测是否有新版本可用</p>
          <el-button type="primary" :icon="Search" @click="checkUpdate" :loading="checking">
            {{ checking ? '检测中...' : '检测更新' }}
          </el-button>
        </div>

        <!-- 检测中 -->
        <div v-else-if="updateState === 'checking'" class="update-checking">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <p>正在检查更新...</p>
        </div>

        <!-- 已是最新 -->
        <div v-else-if="updateState === 'up-to-date'" class="update-uptodate">
          <el-icon :size="24" color="#67c23a"><CircleCheckFilled /></el-icon>
          <p>已是最新版本 v{{ appVersion }}</p>
          <el-button @click="checkUpdate">重新检测</el-button>
        </div>

        <!-- 有新版本 -->
        <div v-else-if="updateState === 'available'" class="update-available">
          <el-icon :size="24" color="#e6a23c"><WarningFilled /></el-icon>
          <div class="update-info">
            <p class="update-version">新版本 v{{ newVersion }}</p>
            <p class="update-size" v-if="newSize">大小：{{ formatSize(newSize) }}</p>
          </div>
          <div class="update-actions">
            <el-button type="warning" @click="downloadUpdate" :loading="downloading">
              {{ downloading ? '下载中...' : '立即更新' }}
            </el-button>
            <el-button @click="dismissUpdate">暂不更新</el-button>
          </div>
        </div>

        <!-- 下载中 -->
        <div v-else-if="updateState === 'downloading'" class="update-downloading">
          <el-progress
            :percentage="downloadPercent"
            :stroke-width="16"
            :text-inside="true"
            style="width: 100%"
          />
          <p class="download-text">正在下载更新... {{ downloadPercent }}%</p>
        </div>

        <!-- 下载完成 -->
        <div v-else-if="updateState === 'downloaded'" class="update-downloaded">
          <el-icon :size="24" color="#67c23a"><CircleCheckFilled /></el-icon>
          <p>更新已下载完成，重启应用即可生效</p>
          <el-button type="success" @click="installUpdate">
            立即重启安装
          </el-button>
        </div>

        <!-- 错误 -->
        <div v-else-if="updateState === 'error'" class="update-error">
          <el-icon :size="24" color="#f56c6c"><CircleCloseFilled /></el-icon>
          <p>{{ errorMessage }}</p>
          <el-button @click="retryUpdate">重试</el-button>
        </div>
      </div>
    </el-card>

    <!-- 占位 -->
    <p class="todo-hint">更多设置项即将上线...</p>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Search, Loading, CircleCheckFilled, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'

// Type declarations for electronAPI
declare global {
  interface Window {
    electronAPI?: {
      getAppVersion: () => Promise<string>
      getVersions: () => Promise<{ electron: string; chrome: string; node: string; platform: string }>
      checkForUpdate: () => Promise<{ available: boolean; version?: string; error?: string }>
      downloadUpdate: () => Promise<{ success: boolean; error?: string }>
      quitAndInstall: () => void
      onUpdateAvailable: (cb: (info: { version: string }) => void) => void
      onDownloadProgress: (cb: (progress: { percent: number; bytesPerSecond: number; total: number }) => void) => void
      onUpdateDownloaded: (cb: (info: { version: string }) => void) => void
      onUpdateError: (cb: (err: { message: string }) => void) => void
      removeUpdateListeners: () => void
    }
  }
}

const appVersion = ref('...')
const electronVersion = ref('...')
const platformName = ref(navigator.platform)

// 更新状态
type UpdateState = 'idle' | 'checking' | 'up-to-date' | 'available' | 'downloading' | 'downloaded' | 'error'
const updateState = ref<UpdateState>('idle')
const checking = ref(false)
const downloading = ref(false)
const downloadPercent = ref(0)
const newVersion = ref('')
const newSize = ref(0)
const errorMessage = ref('')

const updateStatusTag = computed(() => {
  const map: Record<UpdateState, string> = {
    idle: '',
    checking: '检测中',
    'up-to-date': '已是最新',
    available: '有新版本',
    downloading: '下载中',
    downloaded: '待重启',
    error: '检测失败',
  }
  return map[updateState.value]
})

const updateStatusType = computed(() => {
  const map: Record<UpdateState, string> = {
    idle: 'info',
    checking: 'warning',
    'up-to-date': 'success',
    available: 'warning',
    downloading: 'warning',
    downloaded: 'success',
    error: 'danger',
  }
  return map[updateState.value] as 'info' | 'warning' | 'success' | 'danger'
})

function formatSize(bytes: number): string {
  if (!bytes) return '未知'
  const mb = bytes / (1024 * 1024)
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(bytes / 1024).toFixed(1)} KB`
}

async function checkUpdate() {
  const api = window.electronAPI
  if (!api) return

  updateState.value = 'checking'
  checking.value = true

  try {
    const result = await api.checkForUpdate()
    if (result.error) {
      errorMessage.value = result.error
      updateState.value = 'error'
    } else if (result.available && result.version !== appVersion.value) {
      newVersion.value = result.version || ''
      updateState.value = 'available'
    } else {
      updateState.value = 'up-to-date'
    }
  } catch (e: any) {
    errorMessage.value = e?.message || '检测失败'
    updateState.value = 'error'
  }
  checking.value = false
}

async function downloadUpdate() {
  const api = window.electronAPI
  if (!api) return

  updateState.value = 'downloading'
  downloading.value = true

  try {
    const result = await api.downloadUpdate()
    if (!result.success) {
      errorMessage.value = result.error || '下载失败'
      updateState.value = 'error'
    }
  } catch (e: any) {
    errorMessage.value = e?.message || '下载失败'
    updateState.value = 'error'
  }
  downloading.value = false
}

function installUpdate() {
  window.electronAPI?.quitAndInstall()
}

function dismissUpdate() {
  updateState.value = 'idle'
}

function retryUpdate() {
  updateState.value = 'idle'
  checkUpdate()
}

// Setup updater event listeners
function setupListeners() {
  const api = window.electronAPI
  if (!api) return

  api.onUpdateAvailable((info) => {
    newVersion.value = info.version
    updateState.value = 'available'
  })

  api.onDownloadProgress((progress) => {
    downloadPercent.value = Math.round(progress.percent)
    newSize.value = progress.total
    if (updateState.value !== 'downloading') {
      updateState.value = 'downloading'
    }
  })

  api.onUpdateDownloaded((info) => {
    newVersion.value = info.version
    updateState.value = 'downloaded'
  })

  api.onUpdateError((err) => {
    errorMessage.value = err.message
    updateState.value = 'error'
  })
}

onMounted(async () => {
  const api = window.electronAPI
  try {
    const [ver, vers] = await Promise.all([
      api?.getAppVersion(),
      api?.getVersions(),
    ])
    appVersion.value = ver || '0.0.0'
    electronVersion.value = vers?.electron || '未知'
    platformName.value = vers?.platform || navigator.platform
  } catch {
    appVersion.value = '开发模式'
    electronVersion.value = '未知'
    platformName.value = navigator.platform
  }
  setupListeners()
})

onUnmounted(() => {
  window.electronAPI?.removeUpdateListeners()
})
</script>

<style scoped>
.config-container {
  max-width: 720px;
  margin: 0 auto;
}

.config-container h2 {
  margin: 0 0 8px;
}

.description {
  color: #666;
  margin-bottom: 24px;
}

.config-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.update-section {
  min-height: 80px;
}

.update-idle,
.update-checking,
.update-uptodate,
.update-available,
.update-downloading,
.update-downloaded,
.update-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  text-align: center;
}

.update-hint {
  color: #909399;
  margin: 0;
}

.update-info {
  text-align: center;
}

.update-version {
  font-size: 16px;
  font-weight: 600;
  color: #e6a23c;
  margin: 0;
}

.update-size {
  font-size: 13px;
  color: #909399;
  margin: 4px 0 0;
}

.update-actions {
  display: flex;
  gap: 8px;
}

.download-text {
  color: #909399;
  font-size: 13px;
  margin: 4px 0 0;
}

.todo-hint {
  text-align: center;
  color: #c0c4cc;
  margin-top: 32px;
  font-size: 13px;
}
</style>
