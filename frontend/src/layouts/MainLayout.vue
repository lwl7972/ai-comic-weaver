<template>
  <el-container class="main-layout">
    <!-- Header -->
    <el-header class="layout-header" :height="56">
      <div class="header-left">
        <img src="../assets/logo.png" alt="AI漫剧" class="header-logo" />
        <span class="header-title">AI漫剧</span>
      </div>
      <div class="header-center">
        <!-- Pipeline progress indicator -->
        <el-steps :active="pipelineActiveStep" simple finish-status="success">
          <el-step title="剧本" icon="Document" />
          <el-step title="角色" icon="UserFilled" />
          <el-step title="场景" icon="PictureFilled" />
          <el-step title="分镜" icon="Film" />
          <el-step title="导演" icon="VideoCamera" />
          <el-step title="S级" icon="Star" />
        </el-steps>
      </div>
      <div class="header-right">
        <el-button text @click="$router.push('/config')">
          <el-icon><Setting /></el-icon>
        </el-button>
      </div>
    </el-header>

    <el-container>
      <!-- Sidebar -->
      <el-aside width="200px" class="layout-sidebar">
        <el-menu
          :default-active="currentRoute"
          background-color="#1a1a2e"
          text-color="#a0a0b8"
          active-text-color="#818cf8"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/project">
            <el-icon><FolderOpened /></el-icon>
            <span>项目管理</span>
          </el-menu-item>
          <el-menu-item index="/script">
            <el-icon><Document /></el-icon>
            <span>剧本</span>
            <span v-if="pipelineStore.isScriptDirty" class="dirty-dot" />
          </el-menu-item>
          <el-menu-item index="/character">
            <el-icon><UserFilled /></el-icon>
            <span>角色</span>
            <span v-if="pipelineStore.isCharacterDirty" class="dirty-dot" />
          </el-menu-item>
          <el-menu-item index="/scene">
            <el-icon><PictureFilled /></el-icon>
            <span>场景</span>
            <span v-if="pipelineStore.isSceneDirty" class="dirty-dot" />
          </el-menu-item>
          <el-menu-item index="/storyboard">
            <el-icon><Film /></el-icon>
            <span>分镜</span>
            <span v-if="pipelineStore.isStoryboardDirty" class="dirty-dot" />
          </el-menu-item>
          <el-menu-item index="/director">
            <el-icon><VideoCamera /></el-icon>
            <span>导演</span>
            <span v-if="pipelineStore.isDirectorDirty" class="dirty-dot" />
          </el-menu-item>
          <el-menu-item index="/s-level">
            <el-icon><Star /></el-icon>
            <span>S级</span>
            <span v-if="pipelineStore.isOutputDirty" class="dirty-dot" />
          </el-menu-item>
          <el-divider border-style="dashed" />
          <el-menu-item index="/asset">
            <el-icon><Folder /></el-icon>
            <span>素材库</span>
          </el-menu-item>
          <el-menu-item index="/config">
            <el-icon><Setting /></el-icon>
            <span>配置中心</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <!-- Main Content -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import sseClient from '@/utils/sse'
import { useNotificationStore } from '@/stores/notification'
import { usePipelineStore } from '@/stores/pipeline'
import { useProjectStore } from '@/stores/project'
import type { PipelineStage } from '@/types'
import {
  Document, UserFilled, PictureFilled, Film,
  VideoCamera, Star, FolderOpened, Setting, Folder,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const currentRoute = computed(() => route.path)
const notificationStore = useNotificationStore()
const pipelineStore = usePipelineStore()
const projectStore = useProjectStore()

/** 路由路径 -> 流水线阶段映射 */
const stageMap: Record<string, PipelineStage> = {
  '/script': 'SCRIPT',
  '/character': 'CHARACTER',
  '/scene': 'SCENE',
  '/storyboard': 'STORYBOARD',
  '/director': 'DIRECTOR',
  '/s-level': 'OUTPUT',
}

/** 菜单选择处理 - DIRTY 拦截 */
async function handleMenuSelect(index: string) {
  const targetStage = stageMap[index]

  // 非流水线模块（项目管理、配置中心）直接导航
  if (!targetStage) {
    router.push(index)
    return
  }

  // 无当前项目时直接导航（流水线状态需要项目上下文）
  const projectId = projectStore.currentProject?.id
  if (!projectId) {
    router.push(index)
    return
  }

  // 检查目标模块是否有脏标记
  if (pipelineStore.isStageDirty(targetStage)) {
    try {
      await ElMessageBox.confirm(
        `上游数据已变更，${pipelineStore.stageDisplayName(targetStage)}模块需要更新。选择操作方式：`,
        '数据已变更',
        {
          confirmButtonText: '重新执行',
          cancelButtonText: '保持现状',
          distinguishCancelAndClose: true,
          type: 'warning',
        },
      )
      // 用户选择"重新执行"：推进阶段并触发重新生成
      await pipelineStore.advanceStage(projectId, targetStage, true)
    } catch (action) {
      if (action === 'cancel') {
        // 用户选择"保持现状"：清除脏标记后导航
        await pipelineStore.clearDirtyFlag(projectId, targetStage)
      } else {
        // 用户关闭对话框：不做任何操作
        return
      }
    }
  }

  router.push(index)
}

const pipelineActiveStep = computed(() => {
  const stepMap: Record<string, number> = {
    '/script': 0, '/character': 1, '/scene': 2,
    '/storyboard': 3, '/director': 4, '/s-level': 5,
  }
  return stepMap[route.path] ?? -1
})

let offNotification: (() => void) | null = null

onMounted(() => {
  // 连接 SSE 实时推送
  offNotification = sseClient.on('notification', (data: any) => {
    const store = useNotificationStore()
    switch (data.type) {
      case 'success': store.success(data.message); break
      case 'warning': store.warning(data.message); break
      case 'error': store.error(data.message); break
      default: store.info(data.message)
    }
  })
  sseClient.connect()
})

onUnmounted(() => {
  // 路由组件销毁时断开 SSE 连接，移除事件监听器，避免内存泄漏
  if (offNotification) {
    offNotification()
    offNotification = null
  }
  sseClient.disconnect()
  notificationStore.cleanup()
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #0f0c29, #302b63, #24243e);
  color: #fff;
  padding: 0 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  max-width: 600px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.layout-sidebar {
  background: #1a1a2e;
  overflow-y: auto;
}

.layout-main {
  background: #f5f5f7;
  overflow-y: auto;
  padding: 16px;
}

/* Override el-steps theme for dark header */
.layout-header :deep(.el-steps--simple) {
  background: rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 4px 16px;
}

.layout-header :deep(.el-step__title) {
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
}

.layout-header :deep(.is-finish .el-step__title),
.layout-header :deep(.is-process .el-step__title) {
  color: #818cf8;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* DIRTY 橙色圆点指示 */
.dirty-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e6a23c;
  margin-left: 6px;
  box-shadow: 0 0 4px rgba(230, 162, 60, 0.6);
  animation: dirty-pulse 2s ease-in-out infinite;
}

@keyframes dirty-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
