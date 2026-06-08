<template>
  <el-container class="main-layout">
    <!-- Header -->
    <el-header class="layout-header" :height="56">
      <div class="header-left">
        <img src="../../assets/logo.png" alt="AI漫剧" class="header-logo" />
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
          router
          background-color="#1a1a2e"
          text-color="#a0a0b8"
          active-text-color="#818cf8"
        >
          <el-menu-item index="/project">
            <el-icon><FolderOpened /></el-icon>
            <span>项目管理</span>
          </el-menu-item>
          <el-menu-item index="/script">
            <el-icon><Document /></el-icon>
            <span>📝 剧本</span>
          </el-menu-item>
          <el-menu-item index="/character">
            <el-icon><UserFilled /></el-icon>
            <span>🎭 角色</span>
          </el-menu-item>
          <el-menu-item index="/scene">
            <el-icon><PictureFilled /></el-icon>
            <span>🌄 场景</span>
          </el-menu-item>
          <el-menu-item index="/storyboard">
            <el-icon><Film /></el-icon>
            <span>🎬 分镜</span>
          </el-menu-item>
          <el-menu-item index="/director">
            <el-icon><VideoCamera /></el-icon>
            <span>🎥 导演</span>
          </el-menu-item>
          <el-menu-item index="/s-level">
            <el-icon><Star /></el-icon>
            <span>⭐ S级</span>
          </el-menu-item>
          <el-divider border-style="dashed" />
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
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Document, UserFilled, PictureFilled, Film,
  VideoCamera, Star, FolderOpened, Setting,
} from '@element-plus/icons-vue'

const route = useRoute()
const currentRoute = computed(() => route.path)
const pipelineActiveStep = computed(() => {
  const stepMap: Record<string, number> = {
    '/script': 0, '/character': 1, '/scene': 2,
    '/storyboard': 3, '/director': 4, '/s-level': 5,
  }
  return stepMap[route.path] ?? -1
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
</style>
