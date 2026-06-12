<template>
  <div class="config-container">
    <h2>配置中心</h2>
    <p class="description">模型配置、提示词模板管理、应用全局设置</p>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- ========== Tab 1-4: 模型配置 ========== -->
      <el-tab-pane v-for="tab in modelTabs" :key="tab.type" :label="tab.label" :name="tab.type">
        <div class="tab-toolbar">
          <el-button type="primary" @click="openModelDialog(tab.type)">
            新增配置
          </el-button>
        </div>

        <div v-if="modelLoading[tab.type]" class="loading-center">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <el-empty v-else-if="modelConfigs[tab.type].length === 0" description="暂无配置" :image-size="60" />
        <div v-else class="model-cards">
          <el-card v-for="cfg in modelConfigs[tab.type]" :key="cfg.id" class="model-card" shadow="hover">
            <div class="model-card-body">
              <div class="model-info">
                <div class="model-name">{{ cfg.name }}</div>
                <div class="model-meta">
                  <el-tag size="small">{{ getProviderLabel(cfg.provider) }}</el-tag>
                  <span class="model-model-name">{{ cfg.modelName }}</span>
                </div>
              </div>
              <div class="model-actions">
                <el-switch v-model="cfg.isActive" @change="toggleModelActive(cfg)" />
                <el-button size="small" @click="openModelDialog(tab.type, cfg)">编辑</el-button>
                <el-button size="small" type="success" plain :loading="testingId === cfg.id" @click="testConnection(cfg)">
                  测试
                </el-button>
                <el-button size="small" type="danger" plain @click="deleteModelConfig(cfg)">删除</el-button>
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- ========== Tab 5: 提示词模板 ========== -->
      <el-tab-pane label="提示词模板" name="prompt">
        <div class="tab-toolbar">
          <el-select v-model="promptCategoryFilter" placeholder="分类筛选" clearable style="width: 180px; margin-right: 12px;">
            <el-option v-for="cat in templateCategories" :key="cat" :label="getCategoryLabel(cat)" :value="cat" />
          </el-select>
          <el-button type="primary" @click="openPromptDialog()">新增模板</el-button>
        </div>

        <div v-if="promptLoading" class="loading-center">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <el-table v-else :data="filteredTemplates" stripe style="width: 100%">
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="category" label="分类" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ getCategoryLabel(row.category) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isDefault" label="默认" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault" type="success" size="small">是</el-tag>
              <span v-else>否</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" @click="openPromptDialog(row)">编辑</el-button>
              <el-button size="small" type="warning" plain @click="openRenderDialog(row)">预览渲染</el-button>
              <el-button size="small" type="danger" plain @click="deletePromptTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ========== Tab 6: 应用设置 ========== -->
      <el-tab-pane label="应用设置" name="app">
        <!-- 应用信息 -->
        <el-card class="config-card" shadow="hover">
          <template #header>
            <span>应用信息</span>
          </template>
          <el-descriptions :column="2" border size="default">
            <el-descriptions-item label="应用名称">AI漫剧</el-descriptions-item>
            <el-descriptions-item label="当前版本">v{{ appVersion }}</el-descriptions-item>
            <el-descriptions-item label="平台">{{ platformName }}</el-descriptions-item>
            <el-descriptions-item label="Electron">{{ electronVersion }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 应用配置 -->
        <el-card class="config-card" shadow="hover">
          <template #header>
            <span>应用配置</span>
          </template>
          <el-form label-width="120px">
            <el-form-item label="存储路径">
              <el-input :model-value="appSettings.storagePath" readonly>
                <template #append>
                  <el-button @click="selectStoragePath">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="主题">
              <el-select v-model="appSettings.theme" @change="handleThemeChange">
                <el-option label="浅色" value="light" />
                <el-option label="深色" value="dark" />
              </el-select>
            </el-form-item>
            <el-form-item label="自动备份">
              <el-switch v-model="appSettings.autoBackup" @change="handleAppSettingChange" />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 更新检测 -->
        <el-card class="config-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>软件更新</span>
              <el-tag v-if="updateStatusTag" :type="updateStatusType" size="small">
                {{ updateStatusTag }}
              </el-tag>
            </div>
          </template>

          <div class="update-section">
            <div v-if="updateState === 'idle'" class="update-state-block">
              <p class="update-hint">点击下方按钮检测是否有新版本可用</p>
              <el-button type="primary" :icon="Search" @click="checkUpdate" :loading="checking">
                {{ checking ? '检测中...' : '检测更新' }}
              </el-button>
            </div>

            <div v-else-if="updateState === 'checking'" class="update-state-block">
              <el-icon class="is-loading" :size="24"><Loading /></el-icon>
              <p>正在检查更新...</p>
            </div>

            <div v-else-if="updateState === 'up-to-date'" class="update-state-block">
              <el-icon :size="24" color="#67c23a"><CircleCheckFilled /></el-icon>
              <p>已是最新版本 v{{ appVersion }}</p>
              <el-button @click="checkUpdate">重新检测</el-button>
            </div>

            <div v-else-if="updateState === 'available'" class="update-state-block">
              <el-icon :size="24" color="#e6a23c"><WarningFilled /></el-icon>
              <div class="update-info">
                <p class="update-version">新版本 v{{ newVersion }}</p>
                <p class="update-size" v-if="newSize">大小：{{ formatSize(newSize) }}</p>
              </div>
              <div class="update-actions">
                <el-button type="warning" @click="downloadUpdate" :loading="downloading">
                  {{ downloading ? '下载中...' : '立即更新' }}
                </el-button>
                <el-button @click="dismissUpdate">暂不更新</el-button>
              </div>
            </div>

            <div v-else-if="updateState === 'downloading'" class="update-state-block">
              <el-progress
                :percentage="downloadPercent"
                :stroke-width="16"
                :text-inside="true"
                style="width: 100%"
              />
              <p class="download-text">正在下载更新... {{ downloadPercent }}%</p>
            </div>

            <div v-else-if="updateState === 'downloaded'" class="update-state-block">
              <el-icon :size="24" color="#67c23a"><CircleCheckFilled /></el-icon>
              <p>更新已下载完成，重启应用即可生效</p>
              <el-button type="success" @click="installUpdate">立即重启安装</el-button>
            </div>

            <div v-else-if="updateState === 'error'" class="update-state-block">
              <el-icon :size="24" color="#f56c6c"><CircleCloseFilled /></el-icon>
              <p>{{ errorMessage }}</p>
              <el-button @click="retryUpdate">重试</el-button>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ========== 模型配置编辑对话框 ========== -->
    <el-dialog
      v-model="modelDialogVisible"
      :title="editingModelId ? '编辑模型配置' : '新增模型配置'"
      width="600px"
      destroy-on-close
    >
      <el-form :model="modelForm" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="modelForm.name" placeholder="配置名称" />
        </el-form-item>
        <el-form-item label="供应商" required>
          <el-select v-model="modelForm.provider" placeholder="选择供应商">
            <el-option
              v-for="p in currentProviders"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input v-model="modelForm.apiKey" :type="showApiKey ? 'text' : 'password'" placeholder="输入API Key">
            <template #suffix>
              <el-icon class="cursor-pointer" @click="showApiKey = !showApiKey">
                <View v-if="!showApiKey" />
                <Hide v-else />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="API URL" required>
          <el-input v-model="modelForm.apiUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="模型名" required>
          <el-input v-model="modelForm.modelName" placeholder="gpt-4o" />
        </el-form-item>
        <el-form-item label="最大Tokens">
          <el-input-number v-model="modelForm.maxTokens" :min="1" :max="1000000" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="modelForm.isActive" />
        </el-form-item>
        <el-form-item label="扣子工作流">
          <el-switch v-model="modelForm.isCozeWorkflow" />
        </el-form-item>
        <template v-if="modelForm.isCozeWorkflow">
          <el-form-item label="Workflow ID">
            <el-input v-model="modelForm.workflowId" placeholder="扣子工作流ID" />
          </el-form-item>
          <el-form-item label="Bot ID">
            <el-input v-model="modelForm.botId" placeholder="扣子Bot ID" />
          </el-form-item>
          <el-form-item label="App ID">
            <el-input v-model="modelForm.appId" placeholder="扣子App ID" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="modelSaving" @click="saveModelConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 提示词模板编辑对话框 ========== -->
    <el-dialog
      v-model="promptDialogVisible"
      :title="editingPromptId ? '编辑提示词模板' : '新增提示词模板'"
      width="650px"
      destroy-on-close
    >
      <el-form :model="promptForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="promptForm.name" placeholder="模板名称" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="promptForm.category" placeholder="选择分类">
            <el-option v-for="cat in templateCategories" :key="cat" :label="getCategoryLabel(cat)" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="promptForm.content" type="textarea" :rows="8" placeholder="模板内容，使用 {{变量名}} 作为占位符" />
        </el-form-item>
        <el-form-item label="变量定义">
          <el-input v-model="promptForm.variables" type="textarea" :rows="3" placeholder='JSON数组，如 ["角色名","场景描述"]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="promptSaving" @click="savePromptTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 渲染预览对话框 ========== -->
    <el-dialog v-model="renderDialogVisible" title="预览渲染" width="650px" destroy-on-close>
      <div v-if="renderVariables.length > 0">
        <p class="render-hint">请输入变量值：</p>
        <el-form label-width="120px">
          <el-form-item v-for="v in renderVariables" :key="v" :label="v">
            <el-input v-model="renderInput[v]" :placeholder="`输入 ${v} 的值`" />
          </el-form-item>
        </el-form>
      </div>
      <el-empty v-else description="此模板无变量" :image-size="60" />
      <el-divider />
      <div v-if="renderedResult" class="render-result">
        <p class="render-label">渲染结果：</p>
        <pre class="render-content">{{ renderedResult }}</pre>
      </div>
      <template #footer>
        <el-button @click="renderDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="rendering" @click="doRender">渲染</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { Search, Loading, CircleCheckFilled, WarningFilled, CircleCloseFilled, View, Hide } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import http from '@/utils/http'
import { useNotificationStore } from '@/stores/notification'
import type { ModelConfig, ModelProvider, ModelType, PromptTemplate, TemplateCategory } from '@/types'

const notify = useNotificationStore()

// Type declarations for electronAPI
declare global {
  interface Window {
    electronAPI?: {
      getAppVersion: () => Promise<string>
      getVersions: () => Promise<{ electron: string; chrome: string; node: string; platform: string }>
      checkForUpdate: () => Promise<{ available: boolean; version?: string; error?: string }>
      downloadUpdate: () => Promise<{ success: boolean; error?: string }>
      quitAndInstall: () => void
      onUpdateAvailable: (cb: (info: { version: string }) => void) => void
      onDownloadProgress: (cb: (progress: { percent: number; bytesPerSecond: number; total: number }) => void) => void
      onUpdateDownloaded: (cb: (info: { version: string }) => void) => void
      onUpdateError: (cb: (err: { message: string }) => void) => void
      removeUpdateListeners: () => void
      selectDirectory: () => Promise<string | null>
    }
  }
}

// ============================================================
// 通用
// ============================================================
const activeTab = ref('TEXT')

const modelTabs: { type: ModelType; label: string }[] = [
  { type: 'TEXT', label: '文本模型' },
  { type: 'IMAGE', label: '图像模型' },
  { type: 'VIDEO', label: '视频模型' },
  { type: 'AUDIO', label: '音频模型' },
]

// ============================================================
// Tab 1-4: 模型配置
// ============================================================
const modelConfigs = reactive<Record<ModelType, ModelConfig[]>>({
  TEXT: [],
  IMAGE: [],
  VIDEO: [],
  AUDIO: [],
})
const modelLoading = reactive<Record<ModelType, boolean>>({ TEXT: false, IMAGE: false, VIDEO: false, AUDIO: false })
const testingId = ref<number | null>(null)

// 供应商选项
const providerOptions: Record<ModelType, { value: ModelProvider; label: string }[]> = {
  TEXT: [
    { value: 'OPENAI', label: 'OpenAI' },
    { value: 'ANTHROPIC', label: 'Anthropic' },
    { value: 'QWEN', label: '通义千问' },
    { value: 'ERNIE', label: '文心一言' },
    { value: 'COZE', label: '扣子' },
  ],
  IMAGE: [
    { value: 'DALL_E', label: 'DALL-E' },
    { value: 'MIDJOURNEY', label: 'Midjourney' },
    { value: 'SD', label: 'Stable Diffusion' },
    { value: 'COZE', label: '扣子' },
  ],
  VIDEO: [
    { value: 'RUNWAY', label: 'Runway' },
    { value: 'PIKA', label: 'Pika' },
    { value: 'COZE', label: '扣子' },
  ],
  AUDIO: [
    { value: 'TTS_OPENAI', label: 'OpenAI TTS' },
    { value: 'TTS_QWEN', label: '通义TTS' },
    { value: 'VOLCENGINE', label: '火山引擎' },
    { value: 'COZE', label: '扣子' },
  ],
}

function getProviderLabel(provider: ModelProvider): string {
  for (const options of Object.values(providerOptions)) {
    const found = options.find(o => o.value === provider)
    if (found) return found.label
  }
  return provider
}

const currentProviders = computed(() => providerOptions[modelFormType.value] || [])

// 模型对话框
const modelDialogVisible = ref(false)
const modelSaving = ref(false)
const editingModelId = ref<number | null>(null)
const modelFormType = ref<ModelType>('TEXT')
const showApiKey = ref(false)

const modelForm = reactive({
  name: '',
  provider: '' as ModelProvider | '',
  apiKey: '',
  apiUrl: '',
  modelName: '',
  maxTokens: 4096,
  isActive: true,
  isCozeWorkflow: false,
  workflowId: '',
  botId: '',
  appId: '',
})

function resetModelForm() {
  modelForm.name = ''
  modelForm.provider = ''
  modelForm.apiKey = ''
  modelForm.apiUrl = ''
  modelForm.modelName = ''
  modelForm.maxTokens = 4096
  modelForm.isActive = true
  modelForm.isCozeWorkflow = false
  modelForm.workflowId = ''
  modelForm.botId = ''
  modelForm.appId = ''
  showApiKey.value = false
}

function openModelDialog(type: ModelType, cfg?: ModelConfig) {
  modelFormType.value = type
  if (cfg?.id) {
    editingModelId.value = cfg.id
    modelForm.name = cfg.name
    modelForm.provider = cfg.provider
    modelForm.apiKey = cfg.apiKey || ''
    modelForm.apiUrl = cfg.apiUrl
    modelForm.modelName = cfg.modelName
    modelForm.maxTokens = cfg.maxTokens || 4096
    modelForm.isActive = cfg.isActive
    modelForm.isCozeWorkflow = cfg.provider === 'COZE'
    modelForm.workflowId = ''
    modelForm.botId = ''
    modelForm.appId = ''
  } else {
    editingModelId.value = null
    resetModelForm()
  }
  modelDialogVisible.value = true
}

async function loadModelConfigs(type: ModelType) {
  modelLoading[type] = true
  try {
    const res = await http.get('/v1/model-configs', { params: { type } })
    modelConfigs[type] = res.data?.data ?? res.data ?? []
  } catch (e: any) {
    notify.error('加载失败', e.message || '获取模型配置失败')
  } finally {
    modelLoading[type] = false
  }
}

async function saveModelConfig() {
  if (!modelForm.name || !modelForm.provider || !modelForm.apiUrl || !modelForm.modelName) {
    ElMessage.warning('请填写必填项')
    return
  }
  modelSaving.value = true
  try {
    const payload: Record<string, any> = {
      name: modelForm.name,
      provider: modelForm.provider,
      type: modelFormType.value,
      apiUrl: modelForm.apiUrl,
      apiKey: modelForm.apiKey,
      modelName: modelForm.modelName,
      maxTokens: modelForm.maxTokens,
      isActive: modelForm.isActive,
    }
    if (modelForm.isCozeWorkflow && modelForm.provider === 'COZE') {
      payload.workflowId = modelForm.workflowId
      payload.botId = modelForm.botId
      payload.appId = modelForm.appId
    }
    if (editingModelId.value) {
      await http.put(`/v1/model-configs/${editingModelId.value}`, payload)
      notify.success('更新成功', '模型配置已更新')
    } else {
      await http.post('/v1/model-configs', payload)
      notify.success('创建成功', '模型配置已添加')
    }
    modelDialogVisible.value = false
    await loadModelConfigs(modelFormType.value)
  } catch (e: any) {
    notify.error('保存失败', e.message || '保存模型配置失败')
  } finally {
    modelSaving.value = false
  }
}

async function deleteModelConfig(cfg: ModelConfig) {
  if (!cfg.id) return
  try {
    await ElMessageBox.confirm(`确定要删除模型配置「${cfg.name}」吗？`, '确认删除', { type: 'warning' })
    await http.delete(`/v1/model-configs/${cfg.id}`)
    notify.success('已删除', '模型配置已删除')
    await loadModelConfigs(cfg.type)
  } catch { /* cancelled */ }
}

async function toggleModelActive(cfg: ModelConfig) {
  if (!cfg.id) return
  try {
    await http.put(`/v1/model-configs/${cfg.id}`, { isActive: cfg.isActive })
    notify.success(cfg.isActive ? '已启用' : '已禁用', cfg.name)
  } catch (e: any) {
    cfg.isActive = !cfg.isActive
    notify.error('操作失败', e.message || '切换状态失败')
  }
}

async function testConnection(cfg: ModelConfig) {
  if (!cfg.id) return
  testingId.value = cfg.id
  try {
    const res = await http.post(`/v1/model-configs/${cfg.id}/test-connection`)
    const success = res.data?.data?.success ?? res.data?.success ?? false
    if (success) {
      notify.success('连接成功', `${cfg.name} 连接正常`)
    } else {
      notify.warning('连接失败', res.data?.data?.message || res.data?.message || '连接测试未通过')
    }
  } catch (e: any) {
    notify.error('连接失败', e.message || '测试连接异常')
  } finally {
    testingId.value = null
  }
}

// 切换Tab时按需加载
watch(activeTab, (tab) => {
  if (tab === 'prompt') {
    loadPromptTemplates()
  } else if (['TEXT', 'IMAGE', 'VIDEO', 'AUDIO'].includes(tab)) {
    loadModelConfigs(tab as ModelType)
  }
})

// ============================================================
// Tab 5: 提示词模板
// ============================================================
const promptTemplates = ref<PromptTemplate[]>([])
const promptLoading = ref(false)
const promptCategoryFilter = ref<TemplateCategory | ''>('')
const templateCategories: TemplateCategory[] = ['SCRIPT', 'CHARACTER', 'SCENE', 'STORYBOARD', 'SYSTEM']

function getCategoryLabel(cat: TemplateCategory): string {
  const map: Record<TemplateCategory, string> = {
    SCRIPT: '剧本',
    CHARACTER: '角色',
    SCENE: '场景',
    STORYBOARD: '分镜',
    SYSTEM: '系统',
  }
  return map[cat] || cat
}

const filteredTemplates = computed(() => {
  if (!promptCategoryFilter.value) return promptTemplates.value
  return promptTemplates.value.filter(t => t.category === promptCategoryFilter.value)
})

// 提示词对话框
const promptDialogVisible = ref(false)
const promptSaving = ref(false)
const editingPromptId = ref<number | null>(null)
const promptForm = reactive({
  name: '',
  category: '' as TemplateCategory | '',
  content: '',
  variables: '',
})

function resetPromptForm() {
  promptForm.name = ''
  promptForm.category = ''
  promptForm.content = ''
  promptForm.variables = ''
}

function openPromptDialog(tpl?: PromptTemplate) {
  if (tpl?.id) {
    editingPromptId.value = tpl.id
    promptForm.name = tpl.name
    promptForm.category = tpl.category
    promptForm.content = tpl.content
    promptForm.variables = tpl.variables || ''
  } else {
    editingPromptId.value = null
    resetPromptForm()
  }
  promptDialogVisible.value = true
}

async function loadPromptTemplates(category?: string) {
  promptLoading.value = true
  try {
    const params: Record<string, string> = {}
    if (category) params.category = category
    const res = await http.get('/v1/prompt-templates', { params })
    promptTemplates.value = res.data?.data ?? res.data ?? []
  } catch (e: any) {
    notify.error('加载失败', e.message || '获取提示词模板失败')
  } finally {
    promptLoading.value = false
  }
}

async function savePromptTemplate() {
  if (!promptForm.name || !promptForm.category || !promptForm.content) {
    ElMessage.warning('请填写必填项')
    return
  }
  promptSaving.value = true
  try {
    const payload = {
      name: promptForm.name,
      category: promptForm.category,
      content: promptForm.content,
      variables: promptForm.variables,
    }
    if (editingPromptId.value) {
      await http.put(`/v1/prompt-templates/${editingPromptId.value}`, payload)
      notify.success('更新成功', '提示词模板已更新')
    } else {
      await http.post('/v1/prompt-templates', payload)
      notify.success('创建成功', '提示词模板已添加')
    }
    promptDialogVisible.value = false
    await loadPromptTemplates()
  } catch (e: any) {
    notify.error('保存失败', e.message || '保存提示词模板失败')
  } finally {
    promptSaving.value = false
  }
}

async function deletePromptTemplate(tpl: PromptTemplate) {
  if (!tpl.id) return
  try {
    await ElMessageBox.confirm(`确定要删除模板「${tpl.name}」吗？`, '确认删除', { type: 'warning' })
    await http.delete(`/v1/prompt-templates/${tpl.id}`)
    notify.success('已删除', '提示词模板已删除')
    await loadPromptTemplates()
  } catch { /* cancelled */ }
}

// 渲染预览
const renderDialogVisible = ref(false)
const renderTemplateId = ref<number | null>(null)
const renderVariables = ref<string[]>([])
const renderInput = reactive<Record<string, string>>({})
const renderedResult = ref('')
const rendering = ref(false)

function openRenderDialog(tpl: PromptTemplate) {
  if (!tpl.id) return
  renderTemplateId.value = tpl.id
  renderedResult.value = ''
  // 解析变量
  try {
    const vars = JSON.parse(tpl.variables || '[]')
    renderVariables.value = Array.isArray(vars) ? vars : []
  } catch {
    // 尝试从内容提取 {{变量名}}
    const matches = tpl.content.match(/\{\{(\w+)\}\}/g)
    renderVariables.value = matches ? [...new Set(matches.map(m => m.replace(/\{\{|\}\}/g, '')))] : []
  }
  // 初始化输入
  for (const v of renderVariables.value) {
    renderInput[v] = ''
  }
  renderDialogVisible.value = true
}

async function doRender() {
  if (!renderTemplateId.value) return
  rendering.value = true
  renderedResult.value = ''
  try {
    const res = await http.post(`/v1/prompt-templates/${renderTemplateId.value}/render`, {
      variables: { ...renderInput },
    })
    renderedResult.value = res.data?.data?.content ?? res.data?.data ?? res.data?.content ?? ''
  } catch (e: any) {
    notify.error('渲染失败', e.message || '模板渲染失败')
  } finally {
    rendering.value = false
  }
}

// ============================================================
// Tab 6: 应用设置
// ============================================================
const appVersion = ref('...')
const electronVersion = ref('...')
const platformName = ref(navigator.platform)

const appSettings = reactive({
  storagePath: '',
  theme: 'light' as 'light' | 'dark',
  autoBackup: false,
})

async function loadAppConfig() {
  try {
    const res = await http.get('/v1/app-config')
    // 后端返回 Map<String, String>，前端解包后得到 Record<string, string>
    const map: Record<string, string> = res.data?.data ?? res.data ?? {}
    if (map.storagePath) appSettings.storagePath = map.storagePath
    if (map.theme) appSettings.theme = map.theme as 'light' | 'dark'
    if (map.autoBackup) appSettings.autoBackup = map.autoBackup === 'true'
  } catch { /* ignore */ }
}

async function handleAppSettingChange() {
  try {
    // 后端 PUT /api/v1/app-config 期望 Map<String, String> 格式
    await http.put('/v1/app-config', {
      autoBackup: String(appSettings.autoBackup),
    })
    notify.success('设置已保存')
  } catch (e: any) {
    notify.error('保存失败', e.message)
  }
}

async function handleThemeChange(val: string) {
  try {
    // 后端 PUT /api/v1/app-config 期望 Map<String, String> 格式
    await http.put('/v1/app-config', {
      theme: val,
    })
    // 实际主题切换由 CSS class 控制
    document.documentElement.classList.toggle('dark', val === 'dark')
    notify.success('主题已切换')
  } catch (e: any) {
    notify.error('保存失败', e.message)
  }
}

async function selectStoragePath() {
  const path = await window.electronAPI?.selectDirectory?.()
  if (path) {
    appSettings.storagePath = path
    try {
      // 后端 PUT /api/v1/app-config 期望 Map<String, String> 格式
      await http.put('/v1/app-config', {
        storagePath: path,
      })
      notify.success('存储路径已更新')
    } catch (e: any) {
      notify.error('保存失败', e.message)
    }
  }
}

// ============================================================
// 更新检测 (从原 ConfigView 迁移)
// ============================================================
type UpdateState = 'idle' | 'checking' | 'up-to-date' | 'available' | 'downloading' | 'downloaded' | 'error'
const updateState = ref<UpdateState>('idle')
const checking = ref(false)
const downloading = ref(false)
const downloadPercent = ref(0)
const newVersion = ref('')
const newSize = ref(0)
const errorMessage = ref('')

const updateStatusTag = computed(() => {
  const map: Record<UpdateState, string> = {
    idle: '',
    checking: '检测中',
    'up-to-date': '已是最新',
    available: '有新版本',
    downloading: '下载中',
    downloaded: '待重启',
    error: '检测失败',
  }
  return map[updateState.value]
})

const updateStatusType = computed(() => {
  const map: Record<UpdateState, string> = {
    idle: 'info',
    checking: 'warning',
    'up-to-date': 'success',
    available: 'warning',
    downloading: 'warning',
    downloaded: 'success',
    error: 'danger',
  }
  return map[updateState.value] as 'info' | 'warning' | 'success' | 'danger'
})

function formatSize(bytes: number): string {
  if (!bytes) return '未知'
  const mb = bytes / (1024 * 1024)
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${(bytes / 1024).toFixed(1)} KB`
}

async function checkUpdate() {
  const api = window.electronAPI
  if (!api) return
  updateState.value = 'checking'
  checking.value = true
  try {
    const result = await api.checkForUpdate()
    if (result.error) {
      errorMessage.value = result.error
      updateState.value = 'error'
    } else if (result.available && result.version !== appVersion.value) {
      newVersion.value = result.version || ''
      updateState.value = 'available'
    } else {
      updateState.value = 'up-to-date'
    }
  } catch (e: any) {
    errorMessage.value = e?.message || '检测失败'
    updateState.value = 'error'
  }
  checking.value = false
}

async function downloadUpdate() {
  const api = window.electronAPI
  if (!api) return
  updateState.value = 'downloading'
  downloading.value = true
  try {
    const result = await api.downloadUpdate()
    if (!result.success) {
      errorMessage.value = result.error || '下载失败'
      updateState.value = 'error'
    }
  } catch (e: any) {
    errorMessage.value = e?.message || '下载失败'
    updateState.value = 'error'
  }
  downloading.value = false
}

function installUpdate() {
  window.electronAPI?.quitAndInstall()
}

function dismissUpdate() {
  updateState.value = 'idle'
}

function retryUpdate() {
  updateState.value = 'idle'
  checkUpdate()
}

function setupListeners() {
  const api = window.electronAPI
  if (!api) return
  api.onUpdateAvailable((info) => {
    newVersion.value = info.version
    updateState.value = 'available'
  })
  api.onDownloadProgress((progress) => {
    downloadPercent.value = Math.round(progress.percent)
    newSize.value = progress.total
    if (updateState.value !== 'downloading') {
      updateState.value = 'downloading'
    }
  })
  api.onUpdateDownloaded((info) => {
    newVersion.value = info.version
    updateState.value = 'downloaded'
  })
  api.onUpdateError((err) => {
    errorMessage.value = err.message
    updateState.value = 'error'
  })
}

// ============================================================
// 生命周期
// ============================================================
onMounted(async () => {
  // 加载当前Tab数据
  await loadModelConfigs('TEXT')

  // 加载应用配置
  loadAppConfig()

  // 应用版本信息
  const api = window.electronAPI
  try {
    const [ver, vers] = await Promise.all([
      api?.getAppVersion(),
      api?.getVersions(),
    ])
    appVersion.value = ver || '0.0.0'
    electronVersion.value = vers?.electron || '未知'
    platformName.value = vers?.platform || navigator.platform
  } catch {
    appVersion.value = '开发模式'
    electronVersion.value = '未知'
    platformName.value = navigator.platform
  }

  setupListeners()
})

onUnmounted(() => {
  window.electronAPI?.removeUpdateListeners()
})
</script>

<style scoped>
.config-container {
  max-width: 960px;
  margin: 0 auto;
}

.config-container h2 {
  margin: 0 0 8px;
}

.description {
  color: #666;
  margin-bottom: 24px;
}

/* Tab 工具栏 */
.tab-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

/* 加载状态 */
.loading-center {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 120px;
}

/* 模型配置卡片 */
.model-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.model-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.model-card-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.model-info {
  flex: 1;
  min-width: 0;
}

.model-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.model-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-model-name {
  color: #909399;
  font-size: 13px;
}

.model-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 应用设置区 */
.config-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 更新状态 */
.update-section {
  min-height: 80px;
}

.update-state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  text-align: center;
}

.update-hint {
  color: #909399;
  margin: 0;
}

.update-info {
  text-align: center;
}

.update-version {
  font-size: 16px;
  font-weight: 600;
  color: #e6a23c;
  margin: 0;
}

.update-size {
  font-size: 13px;
  color: #909399;
  margin: 4px 0 0;
}

.update-actions {
  display: flex;
  gap: 8px;
}

.download-text {
  color: #909399;
  font-size: 13px;
  margin: 4px 0 0;
}

/* 渲染预览 */
.render-hint {
  color: #909399;
  font-size: 13px;
  margin: 0 0 12px;
}

.render-result {
  margin-top: 12px;
}

.render-label {
  font-weight: 600;
  margin: 0 0 8px;
}

.render-content {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 300px;
  overflow-y: auto;
}

/* 工具类 */
.cursor-pointer {
  cursor: pointer;
}
</style>
