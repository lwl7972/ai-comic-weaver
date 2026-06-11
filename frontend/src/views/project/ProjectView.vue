<template>
  <div class="view-container">
    <h2>项目管理</h2>
    <p class="description">创建和管理您的漫剧项目。每个项目包含完整的六大模块数据。</p>

    <div class="toolbar">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        新建项目
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
    <el-dialog v-model="showCreateDialog" title="新建项目" width="480px">
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
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
const notification = useNotificationStore()
const projects = ref<ProjectItem[]>([])

const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = ref({
  name: '',
  description: '',
  style: 'SHORT_DRAMA',
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
}
</style>
