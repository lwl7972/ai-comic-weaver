<template>
  <div class="director-view">
    <div class="view-header">
      <div class="header-left">
        <h2>🎥 导演模块</h2>
        <p class="description">整集视频生成调度、单镜头回退方案、FFmpeg拼接合成</p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
        :loading="directorStore.generating"
        :disabled="directorStore.storyboards.length === 0"
        @click="handleGenerateFullVideo"
        >
          <el-icon><VideoCamera /></el-icon> 生成整集视频
        </el-button>
        <el-button
        :loading="directorStore.generating"
        :disabled="directorStore.storyboards.length === 0"
        @click="handleConcatVideos"
        >
          <el-icon><Connection /></el-icon> FFmpeg 拼接
        </el-button>
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
          <el-tag size="small" :type="videoTagType(sb.status)">
            {{ videoStatusLabel(sb.status) }}
          </el-tag>
        </div>

        <div class="shot-content">
          <!-- Reference image -->
          <div class="shot-image" v-if="sb.generatedImageUrl">
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
                角色({{ parseCharacterIds(sb.involvedCharacterIds).length }}人)
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
          <div v-if="sb.generatedVideoUrl" class="video-link">
            <el-icon><VideoPlay /></el-icon>
            <span>{{ sb.generatedVideoUrl }}</span>
          </div>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed, onUnmounted } from 'vue'
import {
  VideoCamera, Connection, Loading, Picture, VideoPlay,
} from '@element-plus/icons-vue'
import { useProjectStore } from '@/stores/project'
import { useDirectorStore } from '@/stores/director'
import { useStoryboardStore } from '@/stores/storyboard'
import { useSceneStore } from '@/stores/scene'
import type { Storyboard } from '@/types'

const projectStore = useProjectStore()
const directorStore = useDirectorStore()
const storyboardStore = useStoryboardStore()
const sceneStore = useSceneStore()

function parseCharacterIds(jsonStr: string | undefined): number[] {
  if (!jsonStr) return []
  try { return JSON.parse(jsonStr) } catch { return [] }
}

function getSceneUrl(sb: Storyboard): string | undefined {
  if (!sb.involvedSceneId) return undefined
  // 前置过滤 undefined ID，避免 find() 做 undefined === undefined 的意外匹配
  const scene = sceneStore.scenes.find(s => s.id != null && s.id === sb.involvedSceneId)
  return scene?.frontViewUrl
}

const episodeId = computed(() => projectStore.currentProject?.currentEpisodeId || 1)
const projectId = computed(() => projectStore.currentProject?.id)

let statusTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await directorStore.fetchStoryboards(episodeId.value)
  if (projectId.value) {
    await sceneStore.fetchScenes(projectId.value)
  }
  startStatusPolling()
})

onUnmounted(() => {
  if (statusTimer) clearInterval(statusTimer)
})

function startStatusPolling() {
  statusTimer = setInterval(() => directorStore.fetchVideoStatus(episodeId.value), 3000)
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

function videoStatusClass(sb: Storyboard) {
  if (sb.generatedVideoUrl) return 'status-done'
  if (sb.status === 'VIDEO_GENERATING') return 'status-generating'
  if (sb.status === 'ERROR') return 'status-error'
  return ''
}

function videoTagType(status: string) {
  const map: Record<string, string> = {
    VIDEO_DONE: 'success',
    VIDEO_GENERATING: 'warning',
    IMAGE_DONE: 'info',
    ERROR: 'danger',
  }
  return map[status] || 'info'
}

function videoStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待生成',
    IMAGE_GENERATING: '图片生成中',
    IMAGE_DONE: '图片已生成',
    VIDEO_GENERATING: '视频生成中',
    VIDEO_DONE: '视频已生成',
    ERROR: '失败',
  }
  return map[status] || status
}

function getShotSizeLabel(ss: string) {
  const map: Record<string, string> = {
    EXTREME_CLOSE_UP: '特写', CLOSE_UP: '近景', MEDIUM_CLOSE_UP: '中近景',
    MEDIUM: '中景', MEDIUM_WIDE: '中远景', WIDE: '远景', EXTREME_WIDE: '大远景',
  }
  return map[ss] || ss
}

function getCameraAngleLabel(ca: string) {
  const map: Record<string, string> = {
    EYE_LEVEL: '平视', HIGH_ANGLE: '俯视', LOW_ANGLE: '仰视',
    BIRD_EYE: '鸟瞰', DUTCH_ANGLE: '倾斜',
  }
  return map[ca] || ca
}

function getCameraMovementLabel(cm: string) {
  const map: Record<string, string> = {
    STATIC: '静止', PAN_LEFT: '左摇', PAN_RIGHT: '右摇',
    TILT_UP: '上摇', TILT_DOWN: '下摇', ZOOM_IN: '推近',
    ZOOM_OUT: '拉远', TRACKING: '跟踪', CRANE: '升降', HANDHELD: '手持',
  }
  return map[cm] || cm
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
.video-link {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #67c23a;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.loading-center {
  text-align: center;
  padding: 40px;
}
.empty-hint {
  text-align: center;
  padding: 40px;
}
</style>
