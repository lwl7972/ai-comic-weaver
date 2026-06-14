<template>
  <div class="view-container">
    <h2>{{ t('project.title') }}</h2>
    <p class="description">{{ t('project.description') }}</p>

    <div class="toolbar">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        {{ t('project.createProject') }}
      </el-button>
      <el-button @click="openTemplateDialog">
        <el-icon><DocumentCopy /></el-icon>
        {{ t('project.createFromTemplate') }}
      </el-button>
    </div>

    <el-table :data="projects" style="width: 100%">
      <el-table-column prop="name" label="项目名称" min-width="180" />
      <el-table-column prop="style" label="风格类型" width="120" />
      <el-table-column prop="pipelineStage" label="当前阶段" width="120">
        <template #default="{ row }">
          <el-tag :type="getStageType(row.pipelineStage)">
            {{ row.pipelineStage }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="handleOpen(row)">打开</el-button>
          <el-button size="small" text type="warning" @click="handleBackup(row)">备份</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create Project Dialog -->
    <el-dialog v-model="showCreateDialog" title="{{ t('project.createProject') }}" width="480px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="项目名称">
          <el-input v-model="createForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="简要描述项目内容" />
        </el-form-item>
        <el-form-item label="风格类型">
          <el-select v-model="createForm.style">
            <el-option label="短剧" value="SHORT_DRAMA" />
            <el-option label="漫画" value="COMIC" />
            <el-option label="预告片" value="TRAILER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- Create From Template Dialog -->
    <el-dialog v-model="showTemplateDialog" title="{{ t('project.createFromTemplate') }}项目" width="720px" top="5vh">
      <!-- Step 1: Select Template -->
      <div v-if="templateStep === 1">
        <div v-if="templateStore.loading" style="text-align: center; padding: 40px 0;">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <p>加载模板中...</p>
        </div>
        <el-row v-else :gutter="16">
          <el-col v-for="tpl in templateStore.templateList" :key="tpl.id" :span="8" style="margin-bottom: 16px;">
            <el-card
              shadow="hover"
              :class="{ 'template-card': true, 'template-card--selected': selectedTemplateId === tpl.id }"
              @click="selectedTemplateId = tpl.id!"
            >
              <template #header>
                <span class="template-card__name">{{ tpl.name }}</span>
              </template>
              <p class="template-card__desc">{{ tpl.description || '暂无描述' }}</p>
              <div class="template-card__meta">
                <el-tag size="small" :type="getStyleTagType(tpl.style ?? '')">{{ getStyleLabel(tpl.style ?? '') }}</el-tag>
                <span class="template-card__count">使用 {{ tpl.useCount }} 次</span>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-if="!templateStore.loading && templateStore.templateList.length === 0" description="暂无可用模板" />
      </div>

      <!-- Step 2: Fill Project Info -->
      <div v-if="templateStep === 2">
        <el-descriptions :column="1" border style="margin-bottom: 20px;">
          <el-descriptions-item label="选中模板">{{ selectedTemplateName }}</el-descriptions-item>
        </el-descriptions>
        <el-form :model="templateProjectForm" label-width="80px">
          <el-form-item label="项目名称">
            <el-input v-model="templateProjectForm.name" placeholder="请输入项目名称" />
          </el-form-item>
          <el-form-item label="项目描述">
            <el-input v-model="templateProjectForm.description" type="textarea" :rows="3" placeholder="简要描述项目内容" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button v-if="templateStep === 2" @click="templateStep = 1">上一步</el-button>
        <el-button @click="showTemplateDialog = false">取消</el-button>
        <el-button
          v-if="templateStep === 1"
          type="primary"
          :disabled="!selectedTemplateId"
          @click="templateStep = 2"
        >
          下一步
        </el-button>
        <el-button
          v-if="templateStep === 2"
          type="primary"
          :loading="creatingFromTemplate"
          @click="handleCreateFromTemplate"
        >
          创建项目
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, DocumentCopy, Loading } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useTemplateStore } from '@/stores/template'
import { useNotificationStore } from '@/stores/notification'
import http from '@/utils/http'

interface ProjectItem {
  id: number
  name: string
  style: string
  pipelineStage: string
  updatedAt: string
}

const router = useRouter()
const projectStore = useProjectStore()
const templateStore = useTemplateStore()
const notification = useNotificationStore()
const projects = ref<ProjectItem[]>([])

const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = ref({
  name: '',
  description: '',
  style: 'SHORT_DRAMA',
})

// Template dialog state
const showTemplateDialog = ref(false)
const templateStep = ref(1)
const selectedTemplateId = ref<number | null>(null)
const creatingFromTemplate = ref(false)
const templateProjectForm = ref({
  name: '',
  description: '',
})

const selectedTemplateName = computed(() => {
  if (!selectedTemplateId.value) return ''
  const tpl = templateStore.templateList.find(t => t.id === selectedTemplateId.value)
  return tpl?.name || ''
})

onMounted(async () => {
  await projectStore.fetchProjects()
  projects.value = projectStore.projectList as unknown as ProjectItem[]
})

async function handleCreate() {
  if (!createForm.value.name.trim()) {
    notification.error('请输入项目名称')
    return
  }
  creating.value = true
  try {
    await http.post('/v1/projects', createForm.value)
    notification.success('项目创建成功')
    showCreateDialog.value = false
    createForm.value = { name: '', description: '', style: 'SHORT_DRAMA' }
    await projectStore.fetchProjects()
    projects.value = projectStore.projectList as unknown as ProjectItem[]
  } catch (err: any) {
    notification.error('创建项目失败', err.message)
  } finally {
    creating.value = false
  }
}

async function handleOpen(row: ProjectItem) {
  await projectStore.selectProject(row.id)
  router.push('/script')
}

function handleBackup(_row: ProjectItem) {
  notification.info('备份功能开发中')
}

async function handleDelete(row: ProjectItem) {
  try {
    await ElMessageBox.confirm(`确定要删除项目「${row.name}」吗？此操作不可恢复。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await http.delete(`/v1/projects/${row.id}`)
    notification.success('项目已删除')
    await projectStore.fetchProjects()
    projects.value = projectStore.projectList as unknown as ProjectItem[]
  } catch {
    // User cancelled
  }
}

function getStageType(stage: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, any> = {
    SCRIPT: '', CHARACTER: 'success', SCENE: '',
    STORYBOARD: 'warning', DIRECTOR: '', OUTPUT: 'danger',
  }
  return map[stage] || 'info'
}

// Template dialog functions
async function openTemplateDialog() {
  templateStep.value = 1
  selectedTemplateId.value = null
  templateProjectForm.value = { name: '', description: '' }
  showTemplateDialog.value = true
  await templateStore.fetchTemplates()
}

async function handleCreateFromTemplate() {
  if (!selectedTemplateId.value) {
    notification.error('请选择一个模板')
    return
  }
  if (!templateProjectForm.value.name.trim()) {
    notification.error('请输入项目名称')
    return
  }
  creatingFromTemplate.value = true
  try {
    const result = await templateStore.createProjectFromTemplate(
      selectedTemplateId.value,
      templateProjectForm.value.name,
      templateProjectForm.value.description,
    )
    notification.success(`${t('project.createFromTemplate')}项目成功`)
    showTemplateDialog.value = false
    await projectStore.fetchProjects()
    projects.value = projectStore.projectList as unknown as ProjectItem[]
    // Navigate to the newly created project
    if (result?.id) {
      await projectStore.selectProject(result.id)
      router.push('/script')
    }
  } catch {
    // Error already handled in store
  } finally {
    creatingFromTemplate.value = false
  }
}

function getStyleLabel(style: string): string {
  const map: Record<string, string> = {
    SHORT_DRAMA: '短剧',
    COMIC: '漫画',
    TRAILER: '预告片',
    CUSTOM: '自定义',
  }
  return map[style] || style
}

function getStyleTagType(style: string): '' | 'success' | 'warning' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'info'> = {
    SHORT_DRAMA: '',
    COMIC: 'success',
    TRAILER: 'warning',
    CUSTOM: 'info',
  }
  return map[style] || 'info'
}
</script>

<style scoped>
.view-container h2 {
  margin: 0 0 8px;
}
.description {
  color: #666;
  margin-bottom: 20px;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}
.template-card {
  cursor: pointer;
  transition: border-color 0.2s;
  height: 100%;
}
.template-card--selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary);
}
.template-card__name {
  font-weight: 600;
  font-size: 14px;
}
.template-card__desc {
  font-size: 12px;
  color: #999;
  margin: 0 0 8px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.template-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.template-card__count {
  font-size: 12px;
  color: #999;
}
</style>
