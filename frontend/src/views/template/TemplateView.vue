<template>
  <div class="template-view">
    <div class="view-header">
      <div class="header-left">
        <h2>📝 提示词模板管理</h2>
        <p class="description">管理和维护 AI 生成提示词模板</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 新建模板
        </el-button>
      </div>
    </div>

    <!-- Template List -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>模板列表</span>
          <el-input
            v-model="searchQuery"
            placeholder="搜索模板..."
            style="width: 240px"
            clearable
            prefix-icon="Search"
          />
        </div>
      </template>

      <el-table
        :data="filteredTemplates"
        v-loading="templateStore.loading"
        style="width: 100%"
        border
      >
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="模板名称" min-width="200" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)">
              {{ gettypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="isSystem" label="系统模板" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isSystem ? 'success' : 'info'">
              {{ row.isSystem ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="row.isSystem"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      :title="editingTemplate ? '编辑模板' : '新建模板'"
      width="700px"
    >
      <el-form
        :model="form"
        label-width="100px"
        :rules="rules"
        ref="formRef"
      >
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="模板类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="剧本" value="SCRIPT" />
            <el-option label="角色" value="CHARACTER" />
            <el-option label="场景" value="SCENE" />
            <el-option label="分镜" value="STORYBOARD" />
            <el-option label="视频" value="VIDEO" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板分类" prop="category">
          <el-input v-model="form.category" placeholder="例如：古风、现代、科幻" />
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="请输入模板内容，使用 {{变量名}} 作为占位符"
          />
        </el-form-item>
        <el-form-item label="模板描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="模板用途说明"
          />
        </el-form-item>
        <el-form-item label="变量定义">
          <el-input
            v-model="form.variables"
            type="textarea"
            :rows="3"
            placeholder="JSON 格式：{&#123;&quot;character&quot;: &quot;角色名&quot;, &quot;emotion&quot;: &quot;情绪&quot;&#125;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ editingTemplate ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useTemplateStore } from '@/stores/template'
import type { PromptTemplate } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const templateStore = useTemplateStore()

const searchQuery = ref('')
const showCreateDialog = ref(false)
const editingTemplate = ref<PromptTemplate | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = ref<Partial<PromptTemplate>>({
  name: '',
  type: 'SCRIPT',
  category: 'SCRIPT',
  content: '',
  description: '',
  variables: '',
  isSystem: false,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择模板类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入模板内容', trigger: 'blur' }],
}

const filteredTemplates = computed(() => {
  if (!searchQuery.value) return templateStore.templateList
  const query = searchQuery.value.toLowerCase()
  return templateStore.templateList.filter(template =>
    template.name.toLowerCase().includes(query) ||
    template.description?.toLowerCase().includes(query) ||
    template.category?.toLowerCase().includes(query)
  )
})

onMounted(async () => {
  await templateStore.fetchTemplates()
})

function getTypeTagType(type: string): string {
  const typeMap: Record<string, string> = {
    SCRIPT: '',
    CHARACTER: 'success',
    SCENE: 'warning',
    STORYBOARD: 'primary',
    VIDEO: 'danger',
  }
  return typeMap[type] || ''
}

function gettypeLabel(type: string): string {
  const labelMap: Record<string, string> = {
    SCRIPT: '剧本',
    CHARACTER: '角色',
    SCENE: '场景',
    STORYBOARD: '分镜',
    VIDEO: '视频',
  }
  return labelMap[type] || type
}

function handleCreate() {
  editingTemplate.value = null
  form.value = {
    name: '',
    type: 'SCRIPT',
    category: 'SCRIPT',
    content: '',
    description: '',
    variables: '',
    isSystem: false,
  }
  showCreateDialog.value = true
}

function handleEdit(template: PromptTemplate) {
  editingTemplate.value = template
  form.value = { ...template }
  showCreateDialog.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (editingTemplate.value?.id) {
        await templateStore.updateTemplate(editingTemplate.value.id, form.value)
      } else {
        await templateStore.createTemplate(form.value)
      }
      showCreateDialog.value = false
      await templateStore.fetchTemplates()
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(template: PromptTemplate) {
  if (template.isSystem) {
    ElMessage.warning('系统模板不可删除')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除模板"${template.name}"吗？`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await templateStore.deleteTemplate(template.id!)
  } catch {
    // User cancelled
  }
}
</script>

<style scoped>
.template-view {
  padding: 0;
}
.view-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.header-left h2 {
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
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
