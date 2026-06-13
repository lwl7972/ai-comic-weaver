<template>
  <div class="storyboard-view">
    <div class="view-header">
      <div class="header-left">
        <h2>{{ t('storyboard.title') }}</h2>
        <p class="description">{{ t('storyboard.description') }}</p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :loading="store.generating"
          @click="handleParse"
        >
          <el-icon><Cpu /></el-icon> 步骤1: 解析剧本
        </el-button>
        <el-button
          type="success"
          :loading="store.generating"
          :disabled="store.storyboards.length === 0"
          @click="handleGenerateImages"
        >
          <el-icon><PictureFilled /></el-icon> 步骤3: 生成分镜图
        </el-button>
      </div>
    </div>

    <!-- Step indicator -->
    <div class="step-indicator">
      <el-steps :active="currentStep" align-center>
        <el-step title="解析" description="AI解析剧本→结构化数据" />
        <el-step title="编辑" description="确认/修改分镜参数" />
        <el-step title="生成" description="批量生成分镜图" />
      </el-steps>
    </div>

    <!-- Loading -->
    <div v-if="store.loading" class="loading-center">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <!-- Empty state -->
    <div v-else-if="store.storyboards.length === 0" class="empty-hint">
      <el-empty description="暂无分镜数据，请先'步骤1：解析剧本'" :image-size="80">
        <el-button type="primary" :loading="store.generating" @click="handleParse">
          开始解析
        </el-button>
      </el-empty>
    </div>

    <!-- Storyboard Timeline -->
    <div v-else class="storyboard-timeline">
      <div
        v-for="(sb, index) in store.storyboards"
        :key="sb.id"
        class="storyboard-item"
        :class="{ 'has-image': sb.generatedImageUrl }"
      >
        <!-- Sequence Badge -->
        <div class="sb-seq-badge">
          <span class="seq-number">#{{ index + 1 }}</span>
          <span v-if="sb.timeRange" class="seq-time">{{ sb.timeRange }}</span>
        </div>

        <!-- Main Content -->
        <div class="sb-content">
          <!-- Left: Image Preview -->
          <div class="sb-image-col">
            <div v-if="sb.generatedImageUrl" class="sb-image">
              <img :src="sb.generatedImageUrl" alt="分镜图" />
              <el-button
                size="small"
                :icon="Refresh"
                circle
                class="regenerate-btn"
                :loading="store.generating"
                @click="handleRegenerateImage(sb.id!)"
              />
            </div>
            <div v-else class="sb-image-placeholder" :class="statusClass(sb.status)">
              <el-icon :size="32" v-if="sb.status === 'IMAGE_GENERATING'">
                <Loading class="is-loading" />
              </el-icon>
              <el-icon :size="32" v-else-if="sb.status === 'ERROR'">
                <WarningFilled />
              </el-icon>
              <el-icon :size="32" v-else>
                <Picture />
              </el-icon>
              <span class="status-text">{{ statusLabel(sb.status) }}</span>
            </div>
          </div>

          <!-- Right: Info -->
          <div class="sb-info-col">
            <!-- Dialogue -->
            <div v-if="sb.dialogue" class="sb-dialogue">
              <el-icon><ChatDotRound /></el-icon>
              {{ sb.dialogue }}
            </div>

            <!-- Action -->
            <div v-if="sb.action" class="sb-action">
              {{ sb.action }}
            </div>

            <!-- Continuity -->
            <div v-if="sb.continuity" class="sb-continuity">
              <span class="label">承接：</span>{{ sb.continuity }}
            </div>

            <!-- Camera Params -->
            <div class="sb-params">
              <el-tag size="small" type="primary">{{ getShotSizeLabel(sb.shotSize) }}</el-tag>
              <el-tag size="small" type="success">{{ getCameraAngleLabel(sb.cameraAngle) }}</el-tag>
              <el-tag size="small" type="warning">{{ getCameraMovementLabel(sb.cameraMovement) }}</el-tag>
              <el-tag size="small" v-if="sb.emotion">{{ sb.emotion }}</el-tag>
            </div>

            <!-- Scene & Characters -->
            <div class="sb-meta">
              <span v-if="sb.involvedSceneName">
                <el-icon><Location /></el-icon> {{ sb.involvedSceneName }}
                <el-tag v-if="sb.involvedSceneId" size="small" type="success" class="ref-badge">
                  <el-icon><Link /></el-icon> 已关联
                </el-tag>
              </span>
              <span v-if="sb.involvedCharacters">
                <el-icon><User /></el-icon> {{ parseCharacterNames(sb.involvedCharacters).join(', ') }}
                <el-tag v-if="sb.involvedCharacterIds" size="small" type="primary" class="ref-badge">
                  <el-icon><Link /></el-icon> 已关联
                </el-tag>
              </span>
              <span v-if="sb.bgSound">
                <el-icon><Headset /></el-icon> {{ sb.bgSound }}
              </span>
            </div>

            <!-- Reference images preview -->
            <div v-if="sb.referenceImageUrls" class="sb-refs">
              <span class="refs-label">参考图：</span>
              <div class="refs-images">
                <img v-for="(url, idx) in parseRefUrls(sb.referenceImageUrls)" :key="idx" 
                     :src="url" class="ref-thumb" alt="参考图" />
              </div>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="sb-actions">
          <el-button size="small" text @click="handleEditStoryboard(sb, index)">
            <el-icon><Edit /></el-icon>
          </el-button>
          <el-button size="small" text type="danger" @click="handleDeleteStoryboard(sb.id!)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- Edit Dialog -->
    <el-dialog v-model="showEditDialog" title="编辑分镜" width="640px">
      <el-form v-if="editForm" :model="editForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="时间范围">
              <el-input v-model="editForm.timeRange" placeholder="如 0-4s" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="情绪">
              <el-input v-model="editForm.emotion" placeholder="如 紧张、温馨" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="对话">
          <el-input v-model="editForm.dialogue" placeholder="[角色名, 情绪]:&quot;台词&quot;" />
        </el-form-item>
        <el-form-item label="动作描述">
          <el-input v-model="editForm.action" type="textarea" :rows="2" placeholder="角色动作描述" />
        </el-form-item>
        <el-form-item label="承接上镜">
          <el-input v-model="editForm.continuity" type="textarea" :rows="2" placeholder="与上一个分镜的衔接描述" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="景别">
              <el-select v-model="editForm.shotSize">
                <el-option label="特写" value="EXTREME_CLOSE_UP" />
                <el-option label="近景" value="CLOSE_UP" />
                <el-option label="中近景" value="MEDIUM_CLOSE_UP" />
                <el-option label="中景" value="MEDIUM" />
                <el-option label="中远景" value="MEDIUM_WIDE" />
                <el-option label="远景" value="WIDE" />
                <el-option label="大远景" value="EXTREME_WIDE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="机位">
              <el-select v-model="editForm.cameraAngle">
                <el-option label="平视" value="EYE_LEVEL" />
                <el-option label="俯视" value="HIGH_ANGLE" />
                <el-option label="仰视" value="LOW_ANGLE" />
                <el-option label="鸟瞰" value="BIRD_EYE" />
                <el-option label="倾斜" value="DUTCH_ANGLE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="运镜">
              <el-select v-model="editForm.cameraMovement">
                <el-option label="静止" value="STATIC" />
                <el-option label="左摇" value="PAN_LEFT" />
                <el-option label="右摇" value="PAN_RIGHT" />
                <el-option label="上摇" value="TILT_UP" />
                <el-option label="下摇" value="TILT_DOWN" />
                <el-option label="推近" value="ZOOM_IN" />
                <el-option label="拉远" value="ZOOM_OUT" />
                <el-option label="跟踪" value="TRACKING" />
                <el-option label="升降" value="CRANE" />
                <el-option label="手持" value="HANDHELD" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="场景名称">
              <el-input v-model="editForm.involvedSceneName" placeholder="所在场景" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="背景音效">
              <el-input v-model="editForm.bgSound" placeholder="音效建议" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 角色引用选择 -->
        <el-form-item label="关联角色">
          <el-select
            v-model="selectedCharacterIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择关联的角色（定妆图引用）"
            style="width: 100%"
          >
            <el-option
              v-for="ch in characterStore.characters"
              :key="ch.id"
              :label="`${ch.name} (${ch.role})`"
              :value="ch.id!"
            >
              <span>{{ ch.name }}</span>
              <el-tag size="small" style="margin-left: 8px">{{ ch.role }}</el-tag>
            </el-option>
          </el-select>
          <div v-if="selectedCharacterIds.length > 0" class="ref-preview">
            <span class="preview-label">角色定妆图将作为参考传入分镜图生成</span>
          </div>
        </el-form-item>

        <!-- 场景引用选择 -->
        <el-form-item label="关联场景">
          <el-select
            v-model="selectedSceneId"
            placeholder="选择关联的场景（场景图引用）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="sc in sceneStore.scenes"
              :key="sc.id"
              :label="`${sc.name}${sc.styleHint ? ` (${sc.styleHint})` : ''}`"
              :value="sc.id!"
            >
              <div style="display: flex; align-items: center; gap: 8px">
                <img v-if="sc.frontViewUrl" :src="sc.frontViewUrl" class="scene-thumb-option" />
                <span>{{ sc.name }}</span>
                <el-tag v-if="sc.timeOfDay" size="small" type="info">{{ sc.timeOfDay }}</el-tag>
              </div>
            </el-option>
          </el-select>
          <div v-if="selectedSceneId" class="ref-preview">
            <img v-if="selectedScene?.frontViewUrl"
                 :src="selectedScene.frontViewUrl"
                 class="ref-thumb-preview" />
            <span class="preview-label">场景正面图将作为参考传入分镜图/视频生成</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, onMounted, computed } from 'vue'
import {
  Cpu, PictureFilled, Loading, Refresh, WarningFilled, Picture,
  ChatDotRound, Location, Headset, Edit, Delete, User, Link,
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useStoryboardStore } from '@/stores/storyboard'
import { useCharacterStore } from '@/stores/character'
import { useSceneStore } from '@/stores/scene'
import type { Storyboard, ShotSize, CameraAngle, CameraMovement, StoryboardStatus, Character, Scene } from '@/types'

const projectStore = useProjectStore()
const store = useStoryboardStore()
const characterStore = useCharacterStore()
const sceneStore = useSceneStore()

const showEditDialog = ref(false)
const editingIndex = ref(-1)
const editForm = ref<Partial<Storyboard> | null>(null)

// 角色和场景选择器
const selectedCharacterIds = ref<number[]>([])
const selectedSceneId = ref<number | null>(null)

// 解析 involvedCharacters/involvedCharacterIds 为 name/ID 数组
function parseCharacterNames(jsonStr: string | undefined): string[] {
  if (!jsonStr) return []
  try { return JSON.parse(jsonStr) } catch { return [] }
}

function parseCharacterIds(jsonStr: string | undefined): number[] {
  if (!jsonStr) return []
  try { return JSON.parse(jsonStr) } catch { return [] }
}

// 获取角色定妆图URL（通过 referenceImageId → AssetItem 的路径暂时简化）
function getCharacterThumbnail(ch: Character): string | undefined {
  // 暂时没有直接的定妆图URL字段，后续集成 AssetItem 后可完善
  return undefined
}

function getSceneThumbnail(scene: Scene): string | undefined {
  return scene.frontViewUrl
}

function parseRefUrls(jsonStr: string | undefined): string[] {
  if (!jsonStr) return []
  try { return JSON.parse(jsonStr) } catch { return [] }
}

const currentStep = computed(() => {
  const sbs = store.storyboards
  if (sbs.length === 0) return 0
  const hasImages = sbs.some(s => s.generatedImageUrl)
  if (hasImages) return 3
  return 1  // step 2: editing
})

// Get the current episode ID from project
const episodeId = computed(() => projectStore.currentProject?.currentEpisodeId || 1)

const projectId = computed(() => projectStore.currentProject?.id)

// 当前选中场景的安全引用（避免 .find() + ! 的空值风险）
const selectedScene = computed(() => {
  if (!selectedSceneId.value) return null
  return sceneStore.scenes.find(s => s.id === selectedSceneId.value) ?? null
})

onMounted(async () => {
  if (episodeId.value) {
    await store.fetchStoryboards(episodeId.value)
  }
  if (projectId.value) {
    await characterStore.fetchCharacters(projectId.value)
    await sceneStore.fetchScenes(projectId.value)
  }
})

async function handleParse() {
  await store.parseScript(episodeId.value)
  // Refresh after parsing
  setTimeout(() => store.fetchStoryboards(episodeId.value), 2000)
}

async function handleGenerateImages() {
  await store.generateImages(episodeId.value)
}

async function handleRegenerateImage(storyboardId: number) {
  await store.regenerateImage(storyboardId)
}

function handleEditStoryboard(sb: Storyboard, index: number) {
  editingIndex.value = index
  editForm.value = { ...sb }
  // 初始化角色/场景选择器
  selectedCharacterIds.value = parseCharacterIds(sb.involvedCharacterIds)
  selectedSceneId.value = sb.involvedSceneId ?? null
  showEditDialog.value = true
}

async function handleSaveEdit() {
  if (!editForm.value?.id) return
  try {
    // 检测角色/场景 ID 是否发生变化，决定是否需要重新解析引用
    const originalCharIds = parseCharacterIds(editForm.value.involvedCharacterIds)
    const originalSceneId = editForm.value.involvedSceneId ?? null
    const charIdsChanged = JSON.stringify(selectedCharacterIds.value) !== JSON.stringify(originalCharIds)
    const sceneIdChanged = selectedSceneId.value !== originalSceneId

    // 写入角色ID和场景ID到 editForm
    editForm.value.involvedCharacterIds = JSON.stringify(selectedCharacterIds.value)
    editForm.value.involvedSceneId = selectedSceneId.value ?? undefined
    // 同步更新角色名列表（如果 ID 改变了）
    if (selectedCharacterIds.value.length > 0) {
      const names = selectedCharacterIds.value.map(id => {
        const ch = characterStore.characters.find(c => c.id === id)
        return ch?.name || ''
      }).filter(Boolean)
      editForm.value.involvedCharacters = JSON.stringify(names)
    }
    // 同步更新场景名
    if (selectedSceneId.value) {
      const scene = sceneStore.scenes.find(s => s.id === selectedSceneId.value)
      if (scene) editForm.value.involvedSceneName = scene.name
    }
    await store.updateStoryboard(editForm.value.id, editForm.value)
    // 仅在角色/场景 ID 实际变化时才触发引用解析（避免无关编辑的冗余请求）
    if (charIdsChanged || sceneIdChanged) {
      await store.resolveReferences(editForm.value.id)
    }
    showEditDialog.value = false
  } catch (err: any) {
    console.error('[StoryboardView] 保存编辑失败:', err.message)
  }
}

async function handleDeleteStoryboard(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除该分镜吗？', '确认删除', { type: 'warning' })
    await store.deleteStoryboard(id)
  } catch (err: any) {
    if (err !== 'cancel' && err?.message !== 'cancel') {
      console.error('[StoryboardView] 删除分镜失败:', err?.message)
    }
  }
}

function statusClass(status: string) {
  const map: Record<string, string> = {
    IMAGE_GENERATING: 'status-generating',
    IMAGE_DONE: 'status-done',
    VIDEO_GENERATING: 'status-generating',
    VIDEO_DONE: 'status-done',
    ERROR: 'status-error',
  }
  return map[status] || ''
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待生成',
    IMAGE_GENERATING: '生成中...',
    IMAGE_DONE: '已生成',
    VIDEO_GENERATING: '视频生成中...',
    VIDEO_DONE: '视频已生成',
    ERROR: '生成失败',
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
.storyboard-view {
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
.step-indicator {
  margin-bottom: 20px;
  padding: 16px 24px;
  background: #fafafa;
  border-radius: 8px;
}
.storyboard-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.storyboard-item {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  transition: all 0.2s;
  background: #fff;
}
.storyboard-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-color: #409eff;
}
.storyboard-item.has-image {
  border-left: 3px solid #67c23a;
}
.sb-seq-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 56px;
}
.seq-number {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
}
.seq-time {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}
.sb-content {
  flex: 1;
  display: flex;
  gap: 14px;
  min-width: 0;
}
.sb-image-col {
  min-width: 120px;
  width: 120px;
  flex-shrink: 0;
}
.sb-image {
  position: relative;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}
.sb-image img {
  width: 100%;
  height: auto;
  display: block;
}
.sb-image .regenerate-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 24px;
  height: 24px;
}
.sb-image-placeholder {
  width: 120px;
  height: 90px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #fafafa;
  gap: 4px;
}
.sb-image-placeholder.status-generating {
  border-color: #409eff;
  color: #409eff;
}
.sb-image-placeholder.status-error {
  border-color: #f56c6c;
  color: #f56c6c;
}
.status-text {
  font-size: 11px;
}
.sb-info-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.sb-dialogue {
  font-size: 13px;
  color: #e6a23c;
  display: flex;
  align-items: flex-start;
  gap: 4px;
  line-height: 1.5;
}
.sb-action {
  font-size: 13px;
  color: #333;
  line-height: 1.5;
}
.sb-continuity {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
.sb-continuity .label {
  color: #c0c4cc;
}
.sb-params {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.sb-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}
.sb-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}
.ref-badge {
  margin-left: 4px;
  font-size: 11px;
}
.sb-refs {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}
.refs-label {
  color: #c0c4cc;
}
.refs-images {
  display: flex;
  gap: 4px;
}
.ref-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  object-fit: cover;
  border: 1px solid #ebeef5;
}
.ref-placeholder {
  background: #f5f7fa;
}
.ref-preview {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-label {
  font-size: 12px;
  color: #909399;
}
.ref-thumb-preview {
  width: 48px;
  height: 36px;
  border-radius: 4px;
  object-fit: cover;
  border: 1px solid #ebeef5;
}
.scene-thumb-option {
  width: 32px;
  height: 24px;
  border-radius: 3px;
  object-fit: cover;
  border: 1px solid #ebeef5;
}
.sb-actions {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
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
