<template>
  <div class="slevel-view">
    <div class="view-header">
      <div class="header-left">
        <h2>⭐ S级模块</h2>
        <p class="description">{{ t('sLevel.description') }}</p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :loading="slevelStore.generating"
          :disabled="!selectedEpisodeId"
          @click="handleCompose"
        >
          <el-icon><Film /></el-icon> {{ t('sLevel.composite') }}
        </el-button>
        <el-button
          :loading="slevelStore.generating"
          :disabled="!selectedEpisodeId"
          @click="showExportDialog = true"
        >
          <el-icon><Download /></el-icon> 导出视频
        </el-button>
      </div>
    </div>

    <!-- Compose Card -->
    <el-card class="operation-card">
      <template #header>
        <span>{{ t('sLevel.composite') }}</span>
      </template>
      <div class="op-body">
        <el-form :model="composeForm" label-width="100px" style="max-width: 600px">
          <el-form-item label="选择集数">
            <el-select
              v-model="selectedEpisodeId"
              placeholder="请选择集数"
              style="width: 100%"
            >
              <el-option
                v-for="ep in scriptStore.episodes"
                :key="ep.id"
                :label="`第${ep.episodeNumber}集 - ${ep.title}`"
                :value="ep.id!"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="添加字幕">
            <el-switch v-model="composeForm.addSubtitles" />
          </el-form-item>
          <el-form-item label="混合音频">
            <el-switch v-model="composeForm.mixAudio" />
          </el-form-item>
          <el-form-item label="转场类型">
            <el-select v-model="composeForm.transitionType" placeholder="无转场" clearable style="width: 100%">
              <el-option label="淡入淡出 (fade)" value="fade" />
              <el-option label="左滑 (slideleft)" value="slideleft" />
              <el-option label="上滑 (slideup)" value="slideup" />
              <el-option label="缩放 (zoom)" value="zoom" />
            </el-select>
          </el-form-item>
          <el-form-item label="转场时长(秒)" v-if="composeForm.transitionType">
            <el-input-number
              v-model="composeForm.transitionDuration"
              :min="0.1"
              :max="5"
              :step="0.1"
              :precision="1"
            />
          </el-form-item>
        </el-form>
        <el-steps :active="slevelStore.composeStep" finish-status="success" align-center style="margin-top: 16px">
          <el-step title="拼接视频" description="FFmpeg 拼接多分镜视频" />
          <el-step title="叠加字幕" description="SRT/ASS 字幕嵌入" />
          <el-step title="混合音频" description="音频轨混合处理" />
          <el-step title="添加特效" description="转场/水印效果" />
          <el-step title="导出成片" description="最终成片输出" />
        </el-steps>
        <div class="op-action">
          <el-button
            type="primary"
            :loading="slevelStore.generating"
            :disabled="!selectedEpisodeId || (slevelStore.composeStep > 0 && slevelStore.composeStep < 5)"
            @click="handleCompose"
          >
            {{ slevelStore.composeStep === 0 ? '开始合成' : slevelStore.composeStep === 5 ? '重新合成' : '合成中...' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Video Player Card -->
    <el-card class="operation-card">
      <template #header>
        <span>成片预览</span>
      </template>
      <div class="op-body">
        <div v-if="finalVideoUrl" class="video-container">
          <video
            ref="videoPlayer"
            :src="finalVideoUrl"
            controls
            preload="metadata"
            class="video-player"
          >
            您的浏览器不支持视频播放
          </video>
        </div>
        <el-empty v-else description="暂无可播放的视频，请先生成成片" />
        <div class="video-actions">
          <el-button
            type="primary"
            :disabled="!finalVideoUrl"
            @click="downloadVideo"
          >
            <el-icon><Download /></el-icon> 下载视频
          </el-button>
          <el-button
            :disabled="!finalVideoUrl"
            @click="reloadVideo"
          >
            刷新视频
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Export Card -->
    <el-card class="operation-card">
      <template #header>
        <span>视频导出</span>
      </template>
      <div class="op-body">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="输出格式">
            <el-tag>MP4 (H.264)</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分辨率">1080p (1920×1080)</el-descriptions-item>
          <el-descriptions-item label="帧率">24 fps</el-descriptions-item>
          <el-descriptions-item label="码率">8 Mbps</el-descriptions-item>
          <el-descriptions-item label="音频编码">AAC</el-descriptions-item>
          <el-descriptions-item label="容器格式">MP4</el-descriptions-item>
        </el-descriptions>
        <div class="op-action">
          <el-button
            type="primary"
            :loading="slevelStore.generating"
            :disabled="!selectedEpisodeId"
            @click="showExportDialog = true"
          >
            <el-icon><Download /></el-icon> 自定义导出
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Watermark Card -->
    <el-card class="operation-card">
      <template #header>
        <span>水印管理</span>
      </template>
      <div class="op-body">
        <el-form :model="watermarkForm" label-width="100px" style="max-width: 600px">
          <el-form-item label="选择集数">
            <el-select
              v-model="watermarkEpisodeId"
              placeholder="请选择集数"
              style="width: 100%"
            >
              <el-option
                v-for="ep in scriptStore.episodes"
                :key="ep.id"
                :label="`第${ep.episodeNumber}集 - ${ep.title}`"
                :value="ep.id!"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="水印类型">
            <el-radio-group v-model="watermarkForm.watermarkType">
              <el-radio value="TEXT">文字水印</el-radio>
              <el-radio value="IMAGE">图片水印</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="水印内容" v-if="watermarkForm.watermarkType === 'TEXT'">
            <el-input v-model="watermarkForm.watermarkContent" placeholder="请输入水印文字" style="width: 240px" />
          </el-form-item>
          <el-form-item label="图片路径" v-if="watermarkForm.watermarkType === 'IMAGE'">
            <el-input v-model="watermarkForm.imagePath" placeholder="请输入水印图片路径" style="width: 240px" />
          </el-form-item>
          <el-form-item label="水印位置">
            <el-select v-model="watermarkForm.position" placeholder="右下角" clearable style="width: 200px">
              <el-option label="左上角" value="top-left" />
              <el-option label="右上角" value="top-right" />
              <el-option label="左下角" value="bottom-left" />
              <el-option label="右下角" value="bottom-right" />
              <el-option label="居中" value="center" />
            </el-select>
          </el-form-item>
          <el-form-item label="透明度">
            <el-slider
              v-model="watermarkForm.opacity"
              :min="0"
              :max="1"
              :step="0.1"
              :show-tooltip="true"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="字体大小" v-if="watermarkForm.watermarkType === 'TEXT'">
            <el-input-number v-model="watermarkForm.fontSize" :min="8" :max="120" :step="2" />
          </el-form-item>
          <el-form-item label="字体颜色" v-if="watermarkForm.watermarkType === 'TEXT'">
            <el-color-picker v-model="watermarkForm.fontColor" />
          </el-form-item>
        </el-form>
        <div class="op-action">
          <el-button
            type="primary"
            :loading="slevelStore.generating"
            :disabled="!watermarkEpisodeId || (watermarkForm.watermarkType === 'TEXT' && !watermarkForm.watermarkContent) || (watermarkForm.watermarkType === 'IMAGE' && !watermarkForm.imagePath)"
            @click="handleAddWatermark"
          >
            添加水印
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- Export Dialog -->
    <el-dialog v-model="showExportDialog" title="导出视频" width="500px">
      <el-form :model="exportForm" label-width="100px">
        <el-form-item label="选择集数">
          <el-select
            v-model="exportEpisodeId"
            placeholder="请选择集数"
            style="width: 100%"
          >
            <el-option
              v-for="ep in scriptStore.episodes"
              :key="ep.id"
              :label="`第${ep.episodeNumber}集 - ${ep.title}`"
              :value="ep.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="输出格式">
          <el-select v-model="exportForm.format">
            <el-option label="MP4" value="mp4" />
            <el-option label="MOV" value="mov" />
            <el-option label="AVI" value="avi" />
          </el-select>
        </el-form-item>
        <el-form-item label="分辨率">
          <el-select v-model="exportForm.resolution">
            <el-option label="720p" value="720p" />
            <el-option label="1080p" value="1080p" />
            <el-option label="2K" value="2K" />
            <el-option label="4K" value="4K" />
          </el-select>
        </el-form-item>
        <el-form-item label="码率 (kbps)">
          <el-input-number v-model="exportForm.bitrate" :min="1000" :max="50000" :step="1000" />
        </el-form-item>
        <el-form-item label="帧率 (fps)">
          <el-input-number v-model="exportForm.fps" :min="12" :max="60" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showExportDialog = false">取消</el-button>
        <el-button type="primary" :loading="slevelStore.generating" :disabled="!exportEpisodeId" @click="handleExport">开始导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
import { ref, computed, watch } from 'vue'
import { Film, Download } from '@element-plus/icons-vue'
import { useProjectStore } from '@/stores/project'
import { useScriptStore } from '@/stores/script'
import { useSLevelStore } from '@/stores/slevel'
import type { ExportForm, WatermarkForm } from '@/types'

const projectStore = useProjectStore()
const scriptStore = useScriptStore()
const slevelStore = useSLevelStore()

const showExportDialog = ref(false)

const projectId = computed(() => projectStore.currentProject?.id || 0)

// 集数选择 - {{ t('sLevel.composite') }}
const selectedEpisodeId = ref<number | undefined>(undefined)

// 集数选择 - 导出
const exportEpisodeId = ref<number | undefined>(undefined)

// 集数选择 - 水印
const watermarkEpisodeId = ref<number | undefined>(undefined)

const videoPlayer = ref<HTMLVideoElement | null>(null)
const finalVideoUrl = ref<string>('')

// {{ t('sLevel.composite') }}参数
const composeForm = ref({
  addSubtitles: true,
  mixAudio: true,
  transitionType: '' as string,
  transitionDuration: 1.0,
})

// 导出表单
const exportForm = ref<ExportForm>({
  format: 'mp4',
  resolution: '1080p',
  bitrate: 8000,
  fps: 24,
})

// 水印表单
const watermarkForm = ref<WatermarkForm>({
  watermarkType: 'TEXT',
  watermarkContent: '',
  imagePath: '',
  position: 'bottom-right',
  opacity: 0.5,
  fontSize: 24,
  fontColor: '#ffffff',
})

// 当 episodes 加载后自动选中第一个
watch(() => scriptStore.episodes, (eps) => {
  if (eps.length > 0 && !selectedEpisodeId.value) {
    const firstId = eps[0].id
    selectedEpisodeId.value = firstId
    exportEpisodeId.value = firstId
    watermarkEpisodeId.value = firstId
  }
}, { immediate: true })

async function handleCompose() {
  if (!selectedEpisodeId.value) return
  await slevelStore.composeVideo(selectedEpisodeId.value, {
    addSubtitles: composeForm.value.addSubtitles,
    mixAudio: composeForm.value.mixAudio,
    transitionType: composeForm.value.transitionType || undefined,
    transitionDuration: composeForm.value.transitionDuration,
  })
}

async function handleExport() {
  if (!projectId.value || !exportEpisodeId.value) return
  await slevelStore.exportVideo(projectId.value, exportEpisodeId.value, exportForm.value)
  showExportDialog.value = false
}

async function handleAddWatermark() {
  if (!projectId.value || !watermarkEpisodeId.value) return
  await slevelStore.addWatermark(projectId.value, watermarkEpisodeId.value, watermarkForm.value)
}

function downloadVideo() {
  if (!finalVideoUrl.value) return
  const link = document.createElement('a')
  link.href = finalVideoUrl.value
  link.download = `episode-${exportEpisodeId.value}-final.mp4`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function reloadVideo() {
  if (videoPlayer.value) {
    videoPlayer.value.load()
  }
}

watch(() => slevelStore.composeStep, (step) => {
  if (step === 5) {
    finalVideoUrl.value = `/output/projects/${projectId.value}/output/episode-${selectedEpisodeId.value}-final.mp4`
  }
})
</script>

<style scoped>
.slevel-view {
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
.operation-card {
  margin-bottom: 16px;
}
.op-body {
  padding: 8px 0;
}
.op-action {
  text-align: center;
  margin-top: 16px;
}
.video-container {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
}
.video-player {
  width: 100%;
  height: auto;
  border-radius: 4px;
  background: #000;
}
.video-actions {
  text-align: center;
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 8px;
}
</style>
