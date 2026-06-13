<template>
  <div class="script-view">
    <!-- Header -->
    <div class="view-header">
      <div class="header-left">
        <h2>{{ t('script.title') }}</h2>
        <p class="description">{{ t('script.description') }}</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> {{ t('script.createScript') }}
        </el-button>
        <el-button @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon> {{ t('script.importNovel') }}
        </el-button>
      </div>
    </div>
      <div class="header-right">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 新建剧本
        </el-button>
        <el-button @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon> 导入小说
        </el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- Left: Script List -->
      <el-col :span="8">
        <el-card class="script-list-card">
          <template #header>
            <span>剧本列表</span>
          </template>
          <div v-if="scriptStore.loading" class="loading-center">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
          <div v-else-if="scriptStore.scripts.length === 0" class="empty-hint">
            <el-empty description="暂无剧本" :image-size="60" />
          </div>
          <div v-else class="script-list">
            <div
              v-for="script in scriptStore.scripts"
              :key="script.id"
              class="script-item"
              :class="{ active: scriptStore.currentScript?.id === script.id }"
              @click="handleSelectScript(script)"
            >
              <div class="script-item-title">{{ script.title }}</div>
              <div class="script-item-meta">
                <el-tag size="small" :type="getStatusType(script.status)">{{ getStatusLabel(script.status) }}</el-tag>
                <span v-if="script.totalEpisodes">{{ script.totalEpisodes }}集</span>
                <span class="step-tag">{{ getStepLabel(script.currentStep) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- Right: Script Detail -->
      <el-col :span="16">
        <template v-if="scriptStore.currentScript">
          <el-card class="detail-card">
            <template #header>
              <div class="detail-header">
                <span>{{ scriptStore.currentScript.title }}</span>
                <div class="detail-actions">
                  <el-button
                    type="primary"
                    :loading="scriptStore.generating"
                    @click="handleGenerateOutline"
                  >
                    生成大纲
                  </el-button>
                  <el-button @click="handleDeleteScript" type="danger" plain size="small">
                    删除
                  </el-button>
                </div>
              </div>
            </template>

            <!-- Outline -->
            <div v-if="scriptStore.currentScript.outline" class="outline-section">
              <h4>大纲</h4>
              <div class="outline-content">{{ scriptStore.currentScript.outline }}</div>
            </div>

            <!-- Episode List -->
            <div class="episodes-section">
              <div class="section-header">
                <h4>剧集列表</h4>
                <el-button size="small" @click="handleAddEpisode">添加剧集</el-button>
              </div>
              <el-table :data="scriptStore.episodes" stripe style="width: 100%" size="small">
                <el-table-column prop="episodeNumber" label="集数" width="60" />
                <el-table-column prop="title" label="标题" min-width="120" />
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="getEpisodeStatusType(row.status)">{{ getEpisodeStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button size="small" @click="handleEditEpisode(row)">编辑</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :loading="scriptStore.generating"
                      @click="handleGenerateEpisodeScript(row)"
                    >
                      AI生成
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-card>

          <!-- Episode Editor Dialog -->
          <el-dialog v-model="showEpisodeDialog" :title="editingEpisode ? `第${editingEpisode.episodeNumber}集 - ${editingEpisode.title}` : ''" width="70%">
            <div v-if="editingEpisode">
              <el-input
                v-model="editingEpisode.scriptContent"
                type="textarea"
                :rows="20"
                placeholder="剧本内容..."
              />
            </div>
            <template #footer>
              <el-button @click="showEpisodeDialog = false">取消</el-button>
              <el-button type="primary" @click="handleSaveEpisode">保存</el-button>
            </template>
          </el-dialog>
        </template>

        <!-- Novel Import Section -->
        <el-card v-if="!scriptStore.currentScript" class="novel-card">
          <template #header>
            <span>小说导入</span>
          </template>
          <div v-if="scriptStore.novels.length === 0" class="empty-hint">
            <p>点击右上角「导入小说」上传TXT文件，AI将自动分章摘要并转换为剧本</p>
          </div>
          <div v-else class="novel-list">
            <div v-for="novel in scriptStore.novels" :key="novel.id" class="novel-item">
              <div class="novel-info">
                <span class="novel-title">{{ novel.title }}</span>
                <el-tag size="small" :type="getNovelStatusType(novel.status)">{{ getNovelStatusLabel(novel.status) }}</el-tag>
                <span v-if="novel.totalChapters">{{ novel.totalChapters }}章</span>
              </div>
              <div class="novel-actions">
                <el-button size="small" @click="handleViewSummaries(novel)">查看摘要</el-button>
                <el-button
                  size="small"
                  type="primary"
                  :disabled="novel.status !== 'SUMMARIZING' && novel.status !== 'COMPLETED'"
                  @click="handleSummarizeNovel(novel)"
                >
                  生成摘要
                </el-button>
                <el-button
                  size="small"
                  type="success"
                  :disabled="novel.status !== 'COMPLETED'"
                  @click="handleConvertNovel(novel)"
                >
                  转换为剧本
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Create Script Dialog -->
    <el-dialog v-model="showCreateDialog" title="新建剧本" width="500px">
      <el-form :model="newScriptForm" label-width="80px">
        <el-form-item label="剧本标题">
          <el-input v-model="newScriptForm.title" placeholder="请输入剧本标题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateScript">创建</el-button>
      </template>
    </el-dialog>

    <!-- Upload Novel Dialog -->
    <el-dialog v-model="showUploadDialog" title="导入小说" width="500px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="小说标题">
          <el-input v-model="uploadForm.title" placeholder="可选，留空使用文件名" />
        </el-form-item>
        <el-form-item label="小说文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".txt,.text"
            :on-change="handleFileChange"
          >
            <el-button>选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 TXT 格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUploadNovel">上传</el-button>
      </template>
    </el-dialog>

    <!-- Chapter Summaries Dialog -->
    <el-dialog v-model="showSummariesDialog" title="章节摘要" width="70%">
      <el-table :data="scriptStore.chapterSummaries" stripe size="small">
        <el-table-column prop="chapterIndex" label="章节" width="60" />
        <el-table-column prop="chapterTitle" label="标题" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'COMPLETED' ? 'success' : row.status === 'ERROR' ? 'danger' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summaryText" label="摘要" min-width="300" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Upload, Loading } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useScriptStore } from '@/stores/script'
import type { Script, Episode, Novel } from '@/types'

const { t } = useI18n()
const projectStore = useProjectStore()
const scriptStore = useScriptStore()

const showCreateDialog = ref(false)
const showUploadDialog = ref(false)
const showEpisodeDialog = ref(false)
const showSummariesDialog = ref(false)
const editingEpisode = ref<Episode | null>(null)
const selectedNovel = ref<Novel | null>(null)

const newScriptForm = ref({ title: '' })
const uploadForm = ref({ title: '' })
const uploadFile = ref<File | null>(null)

const projectId = computed(() => projectStore.currentProject?.id)

onMounted(async () => {
  if (projectId.value) {
    await scriptStore.fetchScripts(projectId.value)
    await scriptStore.fetchNovels(projectId.value)
  }
})

// ==================== Script Actions ====================

function handleSelectScript(script: Script) {
  scriptStore.selectScript(script.id!)
}

async function handleCreateScript() {
  if (!projectId.value || !newScriptForm.value.title) return
  try {
    await scriptStore.createScript(projectId.value, { title: newScriptForm.value.title, projectId: projectId.value })
    showCreateDialog.value = false
    newScriptForm.value.title = ''
  } catch {}
}

async function handleGenerateOutline() {
  if (!scriptStore.currentScript?.id) return
  await scriptStore.generateOutline(scriptStore.currentScript.id)
}

async function handleDeleteScript() {
  if (!scriptStore.currentScript?.id) return
  try {
    await ElMessageBox.confirm('确定要删除该剧本吗？关联的剧集也将一并删除。', '确认删除', {
      type: 'warning',
    })
    await scriptStore.deleteScript(scriptStore.currentScript.id)
  } catch {}
}

// ==================== Episode Actions ====================

function handleAddEpisode() {
  if (!scriptStore.currentScript?.id) return
  const nextNum = scriptStore.episodes.length + 1
  scriptStore.createEpisode(scriptStore.currentScript.id, {
    scriptId: scriptStore.currentScript.id,
    episodeNumber: nextNum,
    title: `第${nextNum}集`,
    status: 'DRAFT',
  })
}

function handleEditEpisode(episode: Episode) {
  editingEpisode.value = { ...episode }
  showEpisodeDialog.value = true
}

async function handleSaveEpisode() {
  if (!editingEpisode.value?.id) return
  await scriptStore.updateEpisode(editingEpisode.value.id, {
    scriptContent: editingEpisode.value.scriptContent,
  })
  showEpisodeDialog.value = false
}

async function handleGenerateEpisodeScript(episode: Episode) {
  if (!episode.id) return
  await scriptStore.generateEpisodeScript(episode.id)
}

// ==================== Novel Actions ====================

function handleFileChange(file: any) {
  uploadFile.value = file.raw
}

async function handleUploadNovel() {
  if (!projectId.value || !uploadFile.value) return
  try {
    await scriptStore.uploadNovel(projectId.value, uploadFile.value, uploadForm.value.title || undefined)
    showUploadDialog.value = false
    uploadForm.value.title = ''
    uploadFile.value = null
  } catch {}
}

async function handleSummarizeNovel(novel: Novel) {
  if (!novel.id) return
  await scriptStore.summarizeNovel(novel.id)
}

async function handleConvertNovel(novel: Novel) {
  if (!novel.id) return
  await scriptStore.convertNovelToScript(novel.id)
}

async function handleViewSummaries(novel: Novel) {
  if (!novel.id) return
  selectedNovel.value = novel
  await scriptStore.fetchChapterSummaries(novel.id)
  showSummariesDialog.value = true
}

// ==================== Labels ====================

function getStatusType(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', ERROR: 'danger' }
  return map[status] || 'info'
}
function getStatusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', IN_PROGRESS: '进行中', COMPLETED: '已完成', ERROR: '错误' }
  return map[status] || status
}
function getStepLabel(step: string) {
  const map: Record<string, string> = { OUTLINE: '大纲', EPISODES: '剧集', DRAFT: '草稿', REFINED: '精修' }
  return map[step] || step
}
function getEpisodeStatusType(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', PARSED: 'warning', READY: 'success', ERROR: 'danger' }
  return map[status] || 'info'
}
function getEpisodeStatusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PARSED: '已解析', READY: '就绪', ERROR: '错误' }
  return map[status] || status
}
function getNovelStatusType(status: string) {
  const map: Record<string, string> = { IMPORTING: 'info', SUMMARIZING: 'warning', CONVERTING: 'warning', COMPLETED: 'success', ERROR: 'danger' }
  return map[status] || 'info'
}
function getNovelStatusLabel(status: string) {
  const map: Record<string, string> = { IMPORTING: '导入中', SUMMARIZING: '摘要中', CONVERTING: '转换中', COMPLETED: '已完成', ERROR: '错误' }
  return map[status] || status
}
</script>

<style scoped>
.script-view {
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
.script-list-card {
  min-height: 400px;
}
.script-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.script-item {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.script-item:hover {
  border-color: #409eff;
}
.script-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.script-item-title {
  font-weight: 500;
  margin-bottom: 4px;
}
.script-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #999;
}
.step-tag {
  color: #909399;
  font-size: 11px;
}
.detail-card {
  min-height: 400px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.detail-actions {
  display: flex;
  gap: 8px;
}
.outline-section {
  margin-bottom: 20px;
}
.outline-section h4 {
  margin: 0 0 8px;
}
.outline-content {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  max-height: 300px;
  overflow-y: auto;
}
.episodes-section {
  margin-top: 16px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-header h4 {
  margin: 0;
}
.novel-card {
  min-height: 400px;
}
.novel-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.novel-item {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.novel-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.novel-title {
  font-weight: 500;
}
.novel-actions {
  display: flex;
  gap: 8px;
}
.loading-center {
  text-align: center;
  padding: 40px;
}
.empty-hint {
  text-align: center;
  padding: 20px;
  color: #999;
}
</style>
