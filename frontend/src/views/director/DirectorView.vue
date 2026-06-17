<template>
  <div class="director-view">
    <div class="view-header">
      <div class="header-left">
        <h2>{{ t('director.title') }}</h2>
        <p class="description">整集视频生成调度、单镜头回退方案、FFmpeg拼接合成</p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :loading="directorStore.generating"
          :disabled="directorStore.storyboards.length === 0"
          @click="handleGenerateFullVideo"
        >
          <el-icon><VideoCamera /></el-icon> {{ t('director.generateFullVideo') }}
        </el-button>
        <el-button
          :loading="directorStore.generating"
          :disabled="directorStore.storyboards.length === 0"
          @click="handleConcatVideos"
        >
          <el-icon><Connection /></el-icon> FFmpeg 拼接
        </el-button>
        <el-dropdown :disabled="!directorStore.queueStats" trigger="click">
          <el-button :disabled="!directorStore.queueStats">
            队列管理<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handlePauseQueue" :disabled="directorStore.queueStats?.paused">
                <el-icon><VideoPause /></el-icon> 暂停队列
              </el-dropdown-item>
              <el-dropdown-item @click="handleResumeQueue" :disabled="!directorStore.queueStats?.paused">
                <el-icon><VideoPlay /></el-icon> 恢复队列
              </el-dropdown-item>
              <el-divider style="margin: 4px 0" />
              <el-dropdown-item @click="handleViewQueueStats">
                <el-icon><DataAnalysis /></el-icon> 查看统计
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- Video generation progress -->
    <el-card v-if="directorStore.videoStatus" class="progress-card">
      <template #header>
        <span>视频生成进度</span>
      </template>
      <div class="progress-info">
        <el-progress
          :percentage="directorStore.videoStatus.progress"
          :status="directorStore.videoStatus.progress === 100 ? 'success' : undefined"
          :stroke-width="16"
        />
        <div class="progress-stats">
          <el-tag type="success">已完成: {{ directorStore.videoStatus.videoDone }}</el-tag>
          <el-tag type="warning" v-if="directorStore.videoStatus.videoGenerating > 0">生成中: {{ directorStore.videoStatus.videoGenerating }}</el-tag>
          <el-tag type="danger" v-if="directorStore.videoStatus.videoError > 0">失败: {{ directorStore.videoStatus.videoError }}</el-tag>
          <span class="total-label">总计: {{ directorStore.videoStatus.totalShots }} 镜头</span>
        </div>
      </div>
    </el-card>

    <!-- Queue Stats Card -->
    <el-card v-if="directorStore.queueStats" class="queue-stats-card">
      <template #header>
        <div class="card-header">
          <span>队列状态</span>
          <el-tag :type="directorStore.queueStats.paused ? 'warning' : 'success'">
            {{ directorStore.queueStats.paused ? '已暂停' : '运行中' }}
          </el-tag>
        </div>
      </template>
      <div class="queue-stats">
        <el-space :size="16">
          <el-statistic title="等待中" :value="directorStore.queueStats.pendingCount" />
          <el-statistic title="执行中" :value="directorStore.queueStats.runningCount" />
          <el-statistic title="已完成" :value="directorStore.queueStats.completedCount" />
          <el-statistic title="失败" :value="directorStore.queueStats.failedCount" />
          <el-statistic title="已取消" :value="directorStore.queueStats.cancelledCount" />
          <el-divider direction="vertical" />
          <el-statistic title="总任务" :value="directorStore.queueStats.totalCount" />
          <el-statistic title="最大并发" :value="directorStore.queueStats.maxConcurrent" />
        </el-space>
      </div>
    </el-card>

    <!-- Loading / Empty -->
    <div v-if="storyboardStore.loading" class="loading-center">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
    <div v-else-if="directorStore.storyboards.length === 0" class="empty-hint">
      <el-empty description="暂无分镜数据，请先在分镜模块中生成分镜图片" :image-size="80" />
    </div>

    <!-- Storyboard Video List -->
    <div v-else class="shot-list">
      <el-card
        v-for="sb in directorStore.storyboards"
        :key="sb.id"
        :class="['shot-card', videoStatusClass(sb)]"
        shadow="hover"
      >
        <div class="shot-header">
          <span class="shot-label">镜头 #{{ sb.sequence + 1 }}</span>
          <el-tag size="small" :type="getStoryboardStatusTagType(sb.status)">
            {{ getStoryboardStatusLabel(sb.status) }}
          </el-tag>
        </div>

        <div class="shot-content">
          <!-- Reference image -->
          <div class="shot-image" v-if="sb.generatedImageUrl" @click="previewImage(sb.generatedImageUrl)">
            <img :src="sb.generatedImageUrl" alt="分镜参考图" />
          </div>
          <div class="shot-image-placeholder" v-else>
            <el-icon :size="28"><Picture /></el-icon>
            <span>暂无参考图</span>
          </div>

          <!-- Shot info -->
          <div class="shot-info">
            <div v-if="sb.action" class="shot-action">{{ sb.action }}</div>
            <div class="shot-params">
              <el-tag size="small">{{ getShotSizeLabel(sb.shotSize) }}</el-tag>
              <el-tag size="small" type="success">{{ getCameraAngleLabel(sb.cameraAngle) }}</el-tag>
              <el-tag size="small" type="warning">{{ getCameraMovementLabel(sb.cameraMovement) }}</el-tag>
              <!-- 角色/场景引用标识 -->
              <el-tag v-if="sb.involvedCharacterIds" size="small" type="primary">
                角色({{ parseNumberArray(sb.involvedCharacterIds).length }}人)
              </el-tag>
              <el-tag v-if="sb.involvedSceneId" size="small" type="success">
                场景已关联
              </el-tag>
            </div>
            <!-- 参考图缩略图 -->
            <div v-if="sb.involvedSceneId && getSceneUrl(sb)" class="shot-ref-images">
              <img :src="getSceneUrl(sb)" class="shot-ref-thumb" title="场景参考图" />
            </div>
          </div>
        </div>

        <!-- Video result / action -->
        <div class="shot-footer">
          <template v-if="sb.generatedVideoUrl">
            <el-button size="small" type="success" text @click="previewVideo(sb.generatedVideoUrl)">
              <el-icon><VideoPlay /></el-icon> 预览视频
            </el-button>
          </template>
          <el-button
            v-else
            size="small"
            type="primary"
            :loading="directorStore.generating"
            :disabled="!sb.generatedImageUrl"
            @click="handleGenerateShotVideo(sb.id!)"
          >
            生成视频
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- Task List Card -->
    <el-card v-if="directorStore.tasks.length > 0" class="task-list-card">
      <template #header>
        <span>任务列表</span>
      </template>
      <el-table :data="directorStore.tasks" stripe style="width: 100%">
        <el-table-column prop="taskId" label="Task ID" width="200" />
        <el-table-column prop="taskType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.taskType === 'FULL_EPISODE' ? 'primary' : 'success'">
              {{ row.taskType === 'FULL_EPISODE' ? '整集生成' : '单镜头' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getTaskStatusTagType(row.status)">
              {{ getTaskStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80">
          <template #default="{ row }">
            <el-tag :type="getPriorityTagType(row.priority)" size="small">
              {{ row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :status="row.status === 'SUCCESS' ? 'success' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'RUNNING'"
              size="small"
              type="danger"
              @click="handleCancelTask(row.taskId)"
            >
              取消
            </el-button>
            <el-button v-else size="small" disabled>已完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Image Preview Dialog -->
    <el-dialog v-model="showImagePreview" title="分镜参考图" width="640px" append-to-body>
      <div class="image-preview-container">
        <img v-if="previewImageUrl" :src="previewImageUrl" alt="预览" class="preview-img" />
      </div>
    </el-dialog>

    <!-- Video Preview Dialog -->
    <el-dialog v-model="showVideoPreview" title="视频预览" width="720px" append-to-body>
      <div class="video-preview-container">
        <video v-if="previewVideoUrl" :src="previewVideoUrl" controls autoplay class="preview-video" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
import { onMounted, computed, onUnmounted, ref } from 'vue'
import {
  VideoCamera, Connection, Loading, Picture, VideoPlay, ArrowDown, VideoPause, DataAnalysis,
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useDirectorStore } from '@/stores/director'
import { useStoryboardStore } from '@/stores/storyboard'
import { useSceneStore } from '@/stores/scene'
import { useNotificationStore } from '@/stores/notification'
import type { Storyboard } from '@/types'
import {
  getStoryboardStatusLabel, getStoryboardStatusTagType,
  getShotSizeLabel, getCameraAngleLabel, getCameraMovementLabel,
  getTaskStatusLabel, getTaskStatusTagType, getPriorityTagType,
  parseNumberArray,
} from '@/utils/labels'

const projectStore = useProjectStore()
const directorStore = useDirectorStore()
const storyboardStore = useStoryboardStore()
const sceneStore = useSceneStore()

// 预览状态
const showImagePreview = ref(false)
const previewImageUrl = ref('')
const showVideoPreview = ref(false)
const previewVideoUrl = ref('')

function previewImage(url: string) {
  previewImageUrl.value = url
  showImagePreview.value = true
}

function previewVideo(url: string) {
  previewVideoUrl.value = url
  showVideoPreview.value = true
}

function getSceneUrl(sb: Storyboard): string | undefined {
  if (!sb.involvedSceneId) return undefined
  // 前置过滤 undefined ID，避免 find() 做 undefined === undefined 的意外匹配
  const scene = sceneStore.scenes.find(s => s.id != null && s.id === sb.involvedSceneId)
  return scene?.frontViewUrl
}

const episodeId = computed(() => projectStore.currentProject?.currentEpisodeId || 1)
const projectId = computed(() => projectStore.currentProject?.id)

// 智能轮询：生成中 2s 间隔，空闲 10s 间隔
let statusTimer: ReturnType<typeof setInterval> | null = null
const POLL_INTERVAL_ACTIVE = 2000
const POLL_INTERVAL_IDLE = 10000

onMounted(async () => {
  await directorStore.fetchStoryboards(episodeId.value)
  if (projectId.value) {
    await sceneStore.fetchScenes(projectId.value)
  }
  startStatusPolling()
})

onUnmounted(() => {
  stopStatusPolling()
})

function startStatusPolling() {
  stopStatusPolling()
  scheduleNextPoll()
}

function stopStatusPolling() {
  if (statusTimer) {
    clearTimeout(statusTimer)
    statusTimer = null
  }
}

function scheduleNextPoll() {
  // 有生成中的任务时用短间隔，否则用长间隔节省资源
  const hasActiveGeneration = directorStore.videoStatus?.videoGenerating > 0
  const interval = hasActiveGeneration ? POLL_INTERVAL_ACTIVE : POLL_INTERVAL_IDLE
  statusTimer = setTimeout(async () => {
    await directorStore.fetchVideoStatus(episodeId.value)
    scheduleNextPoll()
  }, interval)
}

async function handleGenerateFullVideo() {
  await directorStore.generateFullVideo(episodeId.value)
}

async function handleGenerateShotVideo(storyboardId: number) {
  await directorStore.generateShotVideo(storyboardId)
}

async function handleConcatVideos() {
  await directorStore.concatVideos(episodeId.value)
}

async function handlePauseQueue() {
  await directorStore.pauseQueue()
}

async function handleResumeQueue() {
  await directorStore.resumeQueue()
}

async function handleCancelTask(taskId: string) {
  try {
    await ElMessageBox.confirm('确定要取消此任务吗？', '确认取消', { type: 'warning' })
    await directorStore.cancelTask(taskId)
  } catch (err: any) {
    if (err !== 'cancel' && err?.message !== 'cancel') {
      console.error('[DirectorView] 取消任务失败:', err?.message)
    }
  }
}

async function handleViewQueueStats() {
  await directorStore.fetchQueueStats()
  await directorStore.fetchTasks()
  useNotificationStore().info('队列统计已刷新')
}

function videoStatusClass(sb: Storyboard) {
  if (!sb) return ''
  if (sb.generatedVideoUrl) return 'status-done'
  if (sb.status === 'VIDEO_GENERATING') return 'status-generating'
  if (sb.status === 'ERROR') return 'status-error'
  return ''
}
</script>

<style scoped>
.director-view {
  padding: 0;
}
.view-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.view-header h2 {
  margin: 0 0 4px;
}
.description {
  color: #666;
  margin: 0;
  font-size: 13px;
}
.header-right {
  display: flex;
  gap: 8px;
}
.progress-card {
  margin-bottom: 16px;
}
.progress-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.progress-stats {
  display: flex;
  gap: 8px;
  align-items: center;
}
.total-label {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}
.queue-stats-card {
  margin-bottom: 16px;
}
.queue-stats-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.queue-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.task-list-card {
  margin-bottom: 16px;
}
.shot-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
}
.shot-card {
  position: relative;
}
.shot-card.status-done {
  border-left: 3px solid #67c23a;
}
.shot-card.status-generating {
  border-left: 3px solid #409eff;
}
.shot-card.status-error {
  border-left: 3px solid #f56c6c;
}
.shot-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.shot-label {
  font-weight: 600;
  font-size: 14px;
}
.shot-content {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
}
.shot-image {
  width: 100px;
  height: 75px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  flex-shrink: 0;
  cursor: pointer;
  transition: opacity 0.2s;
}
.shot-image:hover {
  opacity: 0.85;
}
.shot-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.shot-image-placeholder {
  width: 100px;
  height: 75px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 11px;
  gap: 4px;
  flex-shrink: 0;
}
.shot-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.shot-action {
  font-size: 13px;
  color: #333;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.shot-params {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.shot-ref-images {
  display: flex;
  gap: 4px;
  margin-top: 4px;
}
.shot-ref-thumb {
  width: 32px;
  height: 24px;
  border-radius: 3px;
  object-fit: cover;
  border: 1px solid #ebeef5;
}
.shot-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
.loading-center {
  text-align: center;
  padding: 40px;
}
.empty-hint {
  text-align: center;
  padding: 40px;
}
/* 预览对话框 */
.image-preview-container, .video-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
}
.preview-img {
  max-width: 100%;
  max-height: 480px;
  border-radius: 6px;
}
.preview-video {
  max-width: 100%;
  max-height: 480px;
}
</style>
