<template>
  <div class="scene-view">
    <div class="view-header">
      <div class="header-left">
        <h2>🌄 场景模块</h2>
        <p class="description">场景创建、四视图生成与场景风格一致性</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 新建场景
        </el-button>
        <el-button
          :loading="sceneStore.generating"
          @click="handleExtractScenes"
        >
          AI提取场景
        </el-button>
      </div>
    </div>

    <!-- Pending Confirmation Assets -->
    <el-card v-if="sceneStore.extractedAssets.length > 0" class="pending-card">
      <template #header>
        <span>待确认场景 ({{ sceneStore.extractedAssets.length }})</span>
      </template>
      <div class="pending-list">
        <div v-for="asset in sceneStore.extractedAssets" :key="asset.id" class="pending-item">
          <div class="pending-info">
            <span class="pending-name">{{ asset.name }}</span>
            <span class="pending-desc">{{ asset.description }}</span>
          </div>
          <div class="pending-actions">
            <el-button type="primary" size="small" @click="handleConfirmAsset(asset.id!)">确认入库</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Scene Grid -->
    <div v-if="sceneStore.loading" class="loading-center">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
    <div v-else-if="sceneStore.scenes.length === 0" class="empty-hint">
      <el-empty description="暂无场景，可通过AI提取或手动创建" :image-size="80" />
    </div>
    <div v-else class="scene-grid">
      <el-card v-for="scene in sceneStore.scenes" :key="scene.id" class="scene-card" shadow="hover">
        <!-- Scene Header -->
        <div class="scene-header">
          <div class="scene-name">{{ scene.name }}</div>
          <div class="scene-tags">
            <el-tag size="small" type="success" v-if="scene.timeOfDay">{{ getTimeLabel(scene.timeOfDay) }}</el-tag>
            <el-tag size="small" type="warning" v-if="scene.weather">{{ getWeatherLabel(scene.weather) }}</el-tag>
            <span v-if="scene.styleHint" class="scene-style">{{ scene.styleHint }}</span>
          </div>
        </div>

        <!-- Scene Description -->
        <div v-if="scene.description" class="scene-desc">
          {{ scene.description }}
        </div>

        <!-- Quad View Images -->
        <div class="quad-view-section" v-if="hasAnyView(scene)">
          <div class="quad-view-title">四视图</div>
          <div class="quad-grid">
            <div class="quad-item" v-if="scene.frontViewUrl">
              <img :src="scene.frontViewUrl" alt="正面" />
              <span class="quad-label">正面</span>
              <el-button
                size="small"
                :icon="Refresh"
                circle
                class="regenerate-btn"
                :loading="sceneStore.generating"
                @click="handleRegenerateView(scene.id!, 'front')"
              />
            </div>
            <div class="quad-item" v-if="scene.backViewUrl">
              <img :src="scene.backViewUrl" alt="背面" />
              <span class="quad-label">背面</span>
              <el-button
                size="small"
                :icon="Refresh"
                circle
                class="regenerate-btn"
                :loading="sceneStore.generating"
                @click="handleRegenerateView(scene.id!, 'back')"
              />
            </div>
            <div class="quad-item" v-if="scene.leftViewUrl">
              <img :src="scene.leftViewUrl" alt="左侧" />
              <span class="quad-label">左侧</span>
              <el-button
                size="small"
                :icon="Refresh"
                circle
                class="regenerate-btn"
                :loading="sceneStore.generating"
                @click="handleRegenerateView(scene.id!, 'left')"
              />
            </div>
            <div class="quad-item" v-if="scene.rightViewUrl">
              <img :src="scene.rightViewUrl" alt="右侧" />
              <span class="quad-label">右侧</span>
              <el-button
                size="small"
                :icon="Refresh"
                circle
                class="regenerate-btn"
                :loading="sceneStore.generating"
                @click="handleRegenerateView(scene.id!, 'right')"
              />
            </div>
          </div>
        </div>

        <!-- Scene Actions -->
        <div class="scene-actions">
          <el-button size="small" @click="handleEditScene(scene)">编辑</el-button>
          <el-button
            size="small"
            type="primary"
            :loading="sceneStore.generating"
            @click="handleGenerateQuadView(scene.id!)"
          >
            生成四视图
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDeleteScene(scene.id!)">删除</el-button>
        </div>
      </el-card>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="showCreateDialog" :title="editingScene ? '编辑场景' : '新建场景'" width="580px">
      <el-form :model="sceneForm" label-width="80px">
        <el-form-item label="场景名称">
          <el-input v-model="sceneForm.name" placeholder="请输入场景名称" />
        </el-form-item>
        <el-form-item label="场景描述">
          <el-input
            v-model="sceneForm.description"
            type="textarea"
            :rows="3"
            placeholder="地点、环境、氛围等详细描述"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="时间段">
              <el-select v-model="sceneForm.timeOfDay" placeholder="选择时间段">
                <el-option label="清晨" value="MORNING" />
                <el-option label="正午" value="NOON" />
                <el-option label="午后" value="AFTERNOON" />
                <el-option label="傍晚" value="EVENING" />
                <el-option label="夜晚" value="NIGHT" />
                <el-option label="黎明" value="DAWN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="天气">
              <el-select v-model="sceneForm.weather" placeholder="选择天气">
                <el-option label="晴天" value="SUNNY" />
                <el-option label="多云" value="CLOUDY" />
                <el-option label="雨天" value="RAINY" />
                <el-option label="雪天" value="SNOWY" />
                <el-option label="雾天" value="FOGGY" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="风格关键词">
          <el-input v-model="sceneForm.styleHint" placeholder="如：赛博朋克、古风、现代都市" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveScene">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Plus, Loading, Refresh } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useScriptStore } from '@/stores/script'
import { useSceneStore } from '@/stores/scene'
import type { Scene, TimeOfDay, Weather } from '@/types'

const projectStore = useProjectStore()
const scriptStore = useScriptStore()
const sceneStore = useSceneStore()

const showCreateDialog = ref(false)
const editingScene = ref<Scene | null>(null)
const sceneForm = ref<{
  name: string
  description: string
  timeOfDay: TimeOfDay
  weather: Weather
  styleHint: string
}>({
  name: '',
  description: '',
  timeOfDay: 'MORNING' as TimeOfDay,
  weather: 'SUNNY' as Weather,
  styleHint: '',
})

const projectId = computed(() => projectStore.currentProject?.id)

onMounted(async () => {
  if (projectId.value) {
    await sceneStore.fetchScenes(projectId.value)
    await sceneStore.fetchExtractedAssets(projectId.value)
  }
})

function handleEditScene(scene: Scene) {
  editingScene.value = scene
  sceneForm.value = {
    name: scene.name,
    description: scene.description || '',
    timeOfDay: (scene.timeOfDay as TimeOfDay) || 'MORNING',
    weather: (scene.weather as Weather) || 'SUNNY',
    styleHint: scene.styleHint || '',
  }
  showCreateDialog.value = true
}

async function handleSaveScene() {
  if (!projectId.value) return
  try {
    if (editingScene.value?.id) {
      await sceneStore.updateScene(editingScene.value.id, sceneForm.value)
    } else {
      await sceneStore.createScene(projectId.value, sceneForm.value)
    }
    showCreateDialog.value = false
    editingScene.value = null
    sceneForm.value = {
      name: '', description: '',
      timeOfDay: 'MORNING', weather: 'SUNNY', styleHint: '',
    }
  } catch (err: any) {
    console.error('[SceneView] 保存场景失败:', err.message)
  }
}

async function handleDeleteScene(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除该场景吗？', '确认删除', { type: 'warning' })
    await sceneStore.deleteScene(id)
  } catch (err: any) {
    if (err !== 'cancel' && err?.message !== 'cancel') {
      console.error('[SceneView] 删除场景失败:', err?.message)
    }
  }
}

async function handleExtractScenes() {
  if (!projectId.value) return
  // 确保有当前剧本，如果没有则加载项目的剧本列表并选择第一个
  let scriptId = scriptStore.currentScript?.id
  if (!scriptId) {
    await scriptStore.fetchScripts(projectId.value)
    if (scriptStore.scripts.length > 0) {
      await scriptStore.selectScript(scriptStore.scripts[0].id!)
      scriptId = scriptStore.currentScript?.id
    }
  }
  if (!scriptId) return
  await sceneStore.extractScenes(scriptId)
}

async function handleConfirmAsset(assetId: number) {
  await sceneStore.confirmExtractedAsset(assetId)
}

async function handleGenerateQuadView(sceneId: number) {
  await sceneStore.generateQuadView(sceneId)
}

async function handleRegenerateView(sceneId: number, viewType: string) {
  await sceneStore.regenerateView(sceneId, viewType)
}

function hasAnyView(scene: Scene) {
  return scene.frontViewUrl || scene.backViewUrl || scene.leftViewUrl || scene.rightViewUrl
}

function getTimeLabel(tod: string) {
  const map: Record<string, string> = {
    MORNING: '清晨', NOON: '正午', AFTERNOON: '午后',
    EVENING: '傍晚', NIGHT: '夜晚', DAWN: '黎明',
  }
  return map[tod] || tod
}

function getWeatherLabel(w: string) {
  const map: Record<string, string> = {
    SUNNY: '晴天', CLOUDY: '多云', RAINY: '雨天',
    SNOWY: '雪天', FOGGY: '雾天',
  }
  return map[w] || w
}
</script>

<style scoped>
.scene-view {
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
.pending-card {
  margin-bottom: 16px;
}
.pending-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pending-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.pending-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.pending-name {
  font-weight: 500;
}
.pending-desc {
  font-size: 12px;
  color: #999;
  max-width: 500px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.scene-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
}
.scene-card {
  position: relative;
}
.scene-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.scene-name {
  font-size: 16px;
  font-weight: 600;
}
.scene-tags {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
.scene-style {
  font-size: 12px;
  color: #909399;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.scene-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.quad-view-section {
  border-top: 1px solid #ebeef5;
  padding-top: 10px;
  margin-bottom: 8px;
}
.quad-view-title {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}
.quad-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.quad-item {
  position: relative;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  background: #fafafa;
  aspect-ratio: 1;
}
.quad-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.quad-label {
  position: absolute;
  bottom: 2px;
  left: 2px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 2px;
}
.regenerate-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 22px;
  height: 22px;
}
.scene-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
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
