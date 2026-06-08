<template>
  <div class="view-container">
    <h2>项目管理</h2>
    <p class="description">创建和管理您的漫剧项目。每个项目包含完整的六大模块数据。</p>

    <div class="toolbar">
      <el-button type="primary" @click="handleCreate">
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
        <template #default>
          <el-button size="small" text type="primary">打开</el-button>
          <el-button size="small" text type="warning">备份</el-button>
          <el-button size="small" text type="danger">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'

interface ProjectItem {
  id: number
  name: string
  style: string
  pipelineStage: string
  updatedAt: string
}

const projects = ref<ProjectItem[]>([])

function handleCreate() {
  // TODO: open project creation dialog
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
