<template>
  <div class="asset-view view-container">
    <!-- Header -->
    <div class="view-header">
      <div class="header-left">
        <h2>{{ t('asset.title') }}</h2>
        <p class="description">{{ t('asset.description') }}</p>
      </div>
      <div class="header-right">
        <el-select v-model="filterType" placeholder="素材类型" clearable style="width: 140px" @change="handleFilter">
          <el-option label="全部" value="" />
          <el-option label="图片" value="IMAGE" />
          <el-option label="视频" value="VIDEO" />
          <el-option label="音频" value="AUDIO" />
          <el-option label="文档" value="DOCUMENT" />
        </el-select>
        <el-input
          v-model="filterTags"
          placeholder="标签搜索"
          clearable
          style="width: 180px"
          @clear="handleFilter"
          @keyup.enter="handleFilter"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon> 上传素材
        </el-button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="assetStore.loading" class="loading-center">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <!-- Empty -->
    <div v-else-if="assetStore.assetList.length === 0" class="empty-hint">
      <el-empty description="暂无素材，点击上传按钮添加" :image-size="80" />
    </div>

    <!-- Asset Grid -->
    <div v-else class="asset-grid">
      <el-row :gutter="16">
        <el-col
          v-for="asset in pagedAssets"
          :key="asset.id"
          :xs="24" :sm="12" :md="8" :lg="6"
        >
          <el-card class="asset-card" shadow="hover">
            <!-- Thumbnail / Icon -->
            <div class="asset-preview">
              <template v-if="asset.type === 'IMAGE'">
                <img :src="getImageUrl(asset)" :alt="asset.name" class="preview-img" />
              </template>
              <template v-else>
                <div class="preview-icon" :class="typeIconClass(asset.type)">
                  <el-icon :size="40"><component :is="typeIcon(asset.type)" /></el-icon>
                </div>
              </template>
            </div>

            <!-- Info -->
            <div class="asset-info">
              <div class="asset-name" :title="asset.name">{{ asset.name }}</div>
              <div class="asset-meta">
                <el-tag size="small" :type="typeTagType(asset.type)">{{ typeLabel(asset.type) }}</el-tag>
                <span class="asset-size">{{ formatSize(asset.fileSize) }}</span>
              </div>
              <div v-if="parseTags(asset.tags).length > 0" class="asset-tags">
                <el-tag
                  v-for="tag in parseTags(asset.tags)"
                  :key="tag"
                  size="small"
                  type="info"
                  effect="plain"
                  class="tag-item"
                >
                  {{ tag }}
                </el-tag>
              </div>
              <div v-if="asset.refCharacterId || asset.refSceneId" class="asset-links">
                <el-tag v-if="asset.refCharacterId" size="small" type="warning" effect="plain">
                  已关联角色 #{{ asset.refCharacterId }}
                </el-tag>
                <el-tag v-if="asset.refSceneId" size="small" type="success" effect="plain">
                  已关联场景 #{{ asset.refSceneId }}
                </el-tag>
              </div>
            </div>

            <!-- Actions -->
            <div class="asset-actions">
              <el-button size="small" @click="handleLinkCharacter(asset)">关联角色</el-button>
              <el-button size="small" @click="handleLinkScene(asset)">关联场景</el-button>
              <el-button size="small" type="danger" plain @click="handleDelete(asset.id!)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- Pagination -->
    <div v-if="assetStore.assetList.length > pageSize" class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="assetStore.assetList.length"
        layout="prev, pager, next"
        background
      />
    </div>

    <!-- Upload Dialog -->
    <el-dialog v-model="showUploadDialog" title="上传素材" width="520px">
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="() => ElMessage.warning('每次只能上传一个文件')"
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">支持图片、视频、音频、文档等常见格式</div>
        </template>
      </el-upload>
      <el-form label-width="80px" style="margin-top: 16px">
        <el-form-item label="标签">
          <el-input
            v-model="uploadTags"
            placeholder="多个标签用逗号分隔，如：背景,室外"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- Link Character Dialog -->
    <el-dialog v-model="showLinkCharDialog" title="关联角色" width="440px">
      <el-form label-width="80px">
        <el-form-item label="选择角色">
          <el-select v-model="linkCharacterId" placeholder="请选择角色" filterable style="width: 100%">
            <el-option
              v-for="c in characters"
              :key="c.id"
              :label="c.name"
              :value="c.id!"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showLinkCharDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmLinkCharacter">确认关联</el-button>
      </template>
    </el-dialog>

    <!-- Link Scene Dialog -->
    <el-dialog v-model="showLinkSceneDialog" title="关联场景" width="440px">
      <el-form label-width="80px">
        <el-form-item label="选择场景">
          <el-select v-model="linkSceneId" placeholder="请选择场景" filterable style="width: 100%">
            <el-option
              v-for="s in scenes"
              :key="s.id"
              :label="s.name"
              :value="s.id!"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showLinkSceneDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmLinkScene">确认关联</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref, computed, onMounted } from 'vue'
import { Upload, Search, Loading, PictureFilled, Headset, VideoCamera, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useAssetStore } from '@/stores/asset'
import { useCharacterStore } from '@/stores/character'
import { useSceneStore } from '@/stores/scene'
import type { AssetItem, AssetType } from '@/types'

const projectStore = useProjectStore()
const assetStore = useAssetStore()
const characterStore = useCharacterStore()
const sceneStore = useSceneStore()

// Filter state
const filterType = ref<AssetType | ''>('')
const filterTags = ref('')

// Pagination
const currentPage = ref(1)
const pageSize = 12

// Upload
const showUploadDialog = ref(false)
const uploading = ref(false)
const uploadTags = ref('')
const uploadFile = ref<File | null>(null)
const uploadRef = ref<UploadInstance>()

// Link dialogs
const showLinkCharDialog = ref(false)
const showLinkSceneDialog = ref(false)
const linkCharacterId = ref<number | undefined>(undefined)
const linkSceneId = ref<number | undefined>(undefined)
const linkingAsset = ref<AssetItem | null>(null)

// Derived data
const projectId = computed(() => projectStore.currentProject?.id)
const characters = computed(() => characterStore.characters)
const scenes = computed(() => sceneStore.scenes)

const pagedAssets = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return assetStore.assetList.slice(start, start + pageSize)
})

onMounted(async () => {
  if (projectId.value) {
    await assetStore.fetchAssets(projectId.value)
    await characterStore.fetchCharacters(projectId.value)
    await sceneStore.fetchScenes(projectId.value)
  }
})

async function handleFilter() {
  currentPage.value = 1
  if (projectId.value) {
    await assetStore.fetchAssets(
      projectId.value,
      filterType.value || undefined,
      filterTags.value || undefined,
    )
  }
}

function handleFileChange(file: UploadFile) {
  if (file.raw) {
    uploadFile.value = file.raw
  }
}

async function handleUpload() {
  if (!projectId.value || !uploadFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  uploading.value = true
  try {
    await assetStore.uploadAsset(projectId.value, uploadFile.value, uploadTags.value || undefined)
    showUploadDialog.value = false
    uploadFile.value = null
    uploadTags.value = ''
    uploadRef.value?.clearFiles()
  } catch {
    // error handled in store
  } finally {
    uploading.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除该素材吗？删除后不可恢复。', '确认删除', { type: 'warning' })
    await assetStore.deleteAsset(id)
  } catch (err: any) {
    if (err !== 'cancel' && err?.message !== 'cancel') {
      console.error('[AssetView] 删除素材失败:', err?.message)
    }
  }
}

function handleLinkCharacter(asset: AssetItem) {
  linkingAsset.value = asset
  linkCharacterId.value = asset.refCharacterId ?? undefined
  showLinkCharDialog.value = true
}

async function confirmLinkCharacter() {
  if (!linkingAsset.value?.id || !linkCharacterId.value) {
    ElMessage.warning('请选择要关联的角色')
    return
  }
  try {
    await assetStore.linkToCharacter(linkingAsset.value.id, linkCharacterId.value)
    showLinkCharDialog.value = false
  } catch {
    // error handled in store
  }
}

function handleLinkScene(asset: AssetItem) {
  linkingAsset.value = asset
  linkSceneId.value = asset.refSceneId ?? undefined
  showLinkSceneDialog.value = true
}

async function confirmLinkScene() {
  if (!linkingAsset.value?.id || !linkSceneId.value) {
    ElMessage.warning('请选择要关联的场景')
    return
  }
  try {
    await assetStore.linkToScene(linkingAsset.value.id, linkSceneId.value)
    showLinkSceneDialog.value = false
  } catch {
    // error handled in store
  }
}

// Helpers
function getImageUrl(asset: AssetItem): string {
  return asset.filePath
}

function typeIcon(type: AssetType) {
  const map: Record<AssetType, typeof VideoCamera> = {
    IMAGE: PictureFilled,
    VIDEO: VideoCamera,
    AUDIO: Headset,
    DOCUMENT: Document,
  }
  return map[type] || Document
}

function typeIconClass(type: AssetType) {
  const map: Record<AssetType, string> = {
    IMAGE: 'icon-image',
    VIDEO: 'icon-video',
    AUDIO: 'icon-audio',
    DOCUMENT: 'icon-document',
  }
  return map[type] || 'icon-document'
}

function typeLabel(type: AssetType) {
  const map: Record<AssetType, string> = {
    IMAGE: '图片',
    VIDEO: '视频',
    AUDIO: '音频',
    DOCUMENT: '文档',
  }
  return map[type] || type
}

function typeTagType(type: AssetType) {
  const map: Record<AssetType, string> = {
    IMAGE: 'success',
    VIDEO: 'primary',
    AUDIO: 'warning',
    DOCUMENT: 'info',
  }
  return map[type] || 'info'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function parseTags(tags: string): string[] {
  if (!tags) return []
  try {
    const parsed = JSON.parse(tags)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return tags.split(',').map(t => t.trim()).filter(Boolean)
  }
}
</script>

<style scoped>
.view-container {
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

/* Asset Grid */
.asset-grid {
  margin-bottom: 16px;
}

.asset-card {
  margin-bottom: 16px;
}

.asset-preview {
  width: 100%;
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1a1a2e;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 12px;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a0a0b8;
}

.icon-image { color: #67c23a; }
.icon-video { color: #409eff; }
.icon-audio { color: #e6a23c; }
.icon-document { color: #909399; }

.asset-info {
  margin-bottom: 8px;
}

.asset-name {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.asset-size {
  font-size: 12px;
  color: #909399;
}

.asset-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}

.tag-item {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.asset-links {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.asset-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

/* Pagination */
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}

/* Upload area */
.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
}
</style>
