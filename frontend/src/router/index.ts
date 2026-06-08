import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/project',
    children: [
      // 六大模块流水线
      {
        path: '/script',
        name: 'Script',
        component: () => import('@/views/script/ScriptView.vue'),
        meta: { title: '剧本模块', icon: 'Document' },
      },
      {
        path: '/character',
        name: 'Character',
        component: () => import('@/views/character/CharacterView.vue'),
        meta: { title: '角色模块', icon: 'UserFilled' },
      },
      {
        path: '/scene',
        name: 'Scene',
        component: () => import('@/views/scene/SceneView.vue'),
        meta: { title: '场景模块', icon: 'PictureFilled' },
      },
      {
        path: '/storyboard',
        name: 'Storyboard',
        component: () => import('@/views/storyboard/StoryboardView.vue'),
        meta: { title: '分镜模块', icon: 'Film' },
      },
      {
        path: '/director',
        name: 'Director',
        component: () => import('@/views/director/DirectorView.vue'),
        meta: { title: '导演模块', icon: 'VideoCamera' },
      },
      {
        path: '/s-level',
        name: 'SLevel',
        component: () => import('@/views/slevel/SLevelView.vue'),
        meta: { title: 'S级模块', icon: 'Star' },
      },
      // 项目管理
      {
        path: '/project',
        name: 'Project',
        component: () => import('@/views/project/ProjectView.vue'),
        meta: { title: '项目管理', icon: 'FolderOpened' },
      },
      // 配置中心
      {
        path: '/config',
        name: 'Config',
        component: () => import('@/views/config/ConfigView.vue'),
        meta: { title: '配置中心', icon: 'Setting' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
