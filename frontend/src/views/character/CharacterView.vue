<template>
  <div class="character-view">
    <div class="view-header">
      <div class="header-left">
        <h2>🎭 角色模块</h2>
        <p class="description">AI资产提取、6层身份锚点、定妆图管理与角色圣经</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 新建角色
        </el-button>
        <el-button
          :loading="charStore.generating"
          @click="handleExtractCharacters"
        >
          AI提取角色
        </el-button>
      </div>
    </div>

    <!-- Pending Confirmation Assets -->
    <el-card v-if="charStore.extractedAssets.length > 0" class="pending-card">
      <template #header>
        <span>待确认角色 ({{ charStore.extractedAssets.length }})</span>
      </template>
      <div class="pending-list">
        <div v-for="asset in charStore.extractedAssets" :key="asset.id" class="pending-item">
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

    <!-- Character Grid -->
    <div v-if="charStore.loading" class="loading-center">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
    <div v-else-if="charStore.characters.length === 0" class="empty-hint">
      <el-empty description="暂无角色，可通过AI提取或手动创建" :image-size="80" />
    </div>
    <div v-else class="character-grid">
      <el-card v-for="char in charStore.characters" :key="char.id" class="character-card" shadow="hover">
        <div class="char-avatar">
          <el-icon :size="48"><User /></el-icon>
        </div>
        <div class="char-info">
          <div class="char-name">{{ char.name }}</div>
          <div class="char-tags">
            <el-tag size="small" type="primary">{{ getRoleLabel(char.role) }}</el-tag>
            <el-tag size="small">{{ getGenderLabel(char.gender) }}</el-tag>
            <span v-if="char.ageRange" class="char-age">{{ char.ageRange }}</span>
          </div>
          <div v-if="char.appearance" class="char-detail">
            <strong>外貌：</strong>{{ char.appearance }}
          </div>
          <div v-if="char.personality" class="char-detail">
            <strong>性格：</strong>{{ char.personality }}
          </div>
        </div>
        <div class="char-actions">
          <el-button size="small" @click="handleEditCharacter(char)">编辑</el-button>
          <el-button
            size="small"
            type="primary"
            :loading="charStore.generating"
            @click="handleMakeupImage(char.id!)"
          >
            定妆图
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDeleteCharacter(char.id!)">删除</el-button>
        </div>
      </el-card>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="showCreateDialog" :title="editingChar ? '编辑角色' : '新建角色'" width="600px">
      <el-form :model="charForm" label-width="80px">
        <el-form-item label="角色名">
          <el-input v-model="charForm.name" placeholder="请输入角色名" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="类型">
              <el-select v-model="charForm.role">
                <el-option label="主角" value="PROTAGONIST" />
                <el-option label="反派" value="ANTAGONIST" />
                <el-option label="配角" value="SUPPORTING" />
                <el-option label="龙套" value="EXTRA" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="性别">
              <el-select v-model="charForm.gender">
                <el-option label="男" value="MALE" />
                <el-option label="女" value="FEMALE" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="年龄段">
              <el-input v-model="charForm.ageRange" placeholder="如20-30岁" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="外貌描述">
          <el-input v-model="charForm.appearance" type="textarea" :rows="3" placeholder="脸型、眼睛、发型、体型等详细描述" />
        </el-form-item>
        <el-form-item label="性格特征">
          <el-input v-model="charForm.personality" type="textarea" :rows="2" placeholder="性格关键词和描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveCharacter">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Plus, Loading, User } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useProjectStore } from '@/stores/project'
import { useCharacterStore } from '@/stores/character'
import type { Character, CharacterRole, Gender } from '@/types'

const projectStore = useProjectStore()
const charStore = useCharacterStore()

const showCreateDialog = ref(false)
const editingChar = ref<Character | null>(null)
const charForm = ref<{
  name: string
  role: CharacterRole
  gender: Gender
  ageRange: string
  appearance: string
  personality: string
}>({
  name: '',
  role: 'SUPPORTING',
  gender: 'OTHER',
  ageRange: '',
  appearance: '',
  personality: '',
})

const projectId = computed(() => projectStore.currentProject?.id)

onMounted(async () => {
  if (projectId.value) {
    await charStore.fetchCharacters(projectId.value)
    await charStore.fetchExtractedAssets(projectId.value)
  }
})

function handleEditCharacter(char: Character) {
  editingChar.value = char
  charForm.value = {
    name: char.name,
    role: char.role,
    gender: char.gender,
    ageRange: char.ageRange || '',
    appearance: char.appearance || '',
    personality: char.personality || '',
  }
  showCreateDialog.value = true
}

async function handleSaveCharacter() {
  if (!projectId.value) return
  try {
    if (editingChar.value?.id) {
      await charStore.updateCharacter(editingChar.value.id, charForm.value)
    } else {
      await charStore.createCharacter(projectId.value, {
        ...charForm.value,
        projectId: projectId.value,
      })
    }
    showCreateDialog.value = false
    editingChar.value = null
    charForm.value = { name: '', role: 'SUPPORTING', gender: 'OTHER', ageRange: '', appearance: '', personality: '' }
  } catch {}
}

async function handleDeleteCharacter(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除该角色吗？', '确认删除', { type: 'warning' })
    await charStore.deleteCharacter(id)
  } catch {}
}

async function handleExtractCharacters() {
  if (!projectStore.currentProject?.currentEpisodeId) return
  // Use the first script for now
  await charStore.extractCharacters(1) // TODO: get actual scriptId
}

async function handleConfirmAsset(assetId: number) {
  await charStore.confirmExtractedAsset(assetId)
}

async function handleMakeupImage(characterId: number) {
  await charStore.generateMakeupImage(characterId)
}

function getRoleLabel(role: string) {
  const map: Record<string, string> = { PROTAGONIST: '主角', ANTAGONIST: '反派', SUPPORTING: '配角', EXTRA: '龙套' }
  return map[role] || role
}
function getGenderLabel(gender: string) {
  const map: Record<string, string> = { MALE: '男', FEMALE: '女', OTHER: '其他' }
  return map[gender] || gender
}
</script>

<style scoped>
.character-view {
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
.character-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.character-card {
  position: relative;
}
.char-avatar {
  text-align: center;
  padding: 12px;
  color: #409eff;
}
.char-info {
  padding: 0 12px;
}
.char-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}
.char-tags {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}
.char-age {
  font-size: 12px;
  color: #999;
}
.char-detail {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
  line-height: 1.5;
}
.char-actions {
  display: flex;
  gap: 6px;
  padding: 12px;
  justify-content: flex-end;
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
