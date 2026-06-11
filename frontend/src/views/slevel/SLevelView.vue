<template>
  <div class="slevel-view">
    <div class="view-header">
      <div class="header-left">
        <h2>⭐ S级模块</h2>
        <p class="description">成片合成、视频导出、水印添加</p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :loading="slevelStore.generating"
          @click="handleCompose"
        >
          <el-icon><Film /></el-icon> 成片合成
        </el-button>
        <el-button
          :loading="slevelStore.generating"
          @click="showExportDialog = true"
        >
          <el-icon><Download /></el-icon> 导出视频
        </el-button>
      </div>
    </div>

    <!-- Compose Card -->
    <el-card class="operation-card">
      <template #header>
        <span>成片合成</span>
      </template>
      <div class="op-body">
        <el-steps :active="slevelStore.composeStep" finish-status="success" align-center>
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
            :disabled="slevelStore.composeStep > 0 && slevelStore.composeStep < 5"
            @click="handleCompose"
          >
            {{ slevelStore.composeStep === 0 ? '开始合成' : slevelStore.composeStep === 5 ? '重新合成' : '合成中...' }}
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
        <el-form :model="watermarkForm" label-width="80px" inline>
          <el-form-item label="水印类型">
            <el-radio-group v-model="watermarkForm.watermarkType">
              <el-radio value="TEXT">文字水印</el-radio>
              <el-radio value="IMAGE">图片水印</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="水印内容" v-if="watermarkForm.watermarkType === 'TEXT'">
            <el-input v-model="watermarkForm.watermarkContent" placeholder="请输入水印文字" style="width: 240px" />
          </el-form-item>
        </el-form>
        <div class="op-action">
          <el-button
            type="primary"
            :loading="slevelStore.generating"
            :disabled="!watermarkForm.watermarkContent"
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
        <el-form-item label="输出格式">
          <el-select v-model="exportForm.format">
            <el-option label="MP4" value="MP4" />
            <el-option label="MOV" value="MOV" />
            <el-option label="AVI" value="AVI" />
          </el-select>
        </el-form-item>
        <el-form-item label="分辨率">
          <el-select v-model="exportForm.resolution">
            <el-option label="720p" :value="720" />
            <el-option label="1080p" :value="1080" />
            <el-option label="2K" :value="1440" />
            <el-option label="4K" :value="2160" />
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
        <el-button type="primary" :loading="slevelStore.generating" @click="handleExport">开始导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Film, Download } from '@element-plus/icons-vue'
import { useProjectStore } from '@/stores/project'
import { useSLevelStore } from '@/stores/slevel'
import type { ExportForm, WatermarkForm } from '@/stores/slevel'

const projectStore = useProjectStore()
const slevelStore = useSLevelStore()

const showExportDialog = ref(false)

const projectId = computed(() => projectStore.currentProject?.id || 1)

const exportForm = ref<ExportForm>({
  format: 'MP4',
  resolution: 1080,
  bitrate: 8000,
  fps: 24,
})

const watermarkForm = ref<WatermarkForm>({
  watermarkType: 'TEXT',
  watermarkContent: '',
})

async function handleCompose() {
  await slevelStore.composeVideo(projectId.value)
  // 进度由 SSE 事件驱动 slevelStore.setComposeStep()，无需模拟
}

async function handleExport() {
  if (!projectId.value) return
  await slevelStore.exportVideo(projectId.value, exportForm.value)
  showExportDialog.value = false
}

async function handleAddWatermark() {
  if (!projectId.value) return
  await slevelStore.addWatermark(projectId.value, watermarkForm.value)
}
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
</style>
