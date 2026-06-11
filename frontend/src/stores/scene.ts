import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { Scene, ExtractedAsset, TimeOfDay, Weather } from '@/types'

/**
 * Scene module Store
 */
export const useSceneStore = defineStore('scene', () => {
  const scenes = ref<Scene[]>([])
  const extractedAssets = ref<ExtractedAsset[]>([])
  const loading = ref(false)
  const generating = ref(false)

  async function fetchScenes(projectId: number) {
    loading.value = true
    try {
      const res = await http.get(`/v1/projects/${projectId}/scenes`)
      scenes.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载场景失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function createScene(projectId: number, data: {
    name: string
    description?: string
    timeOfDay?: TimeOfDay
    weather?: Weather
    styleHint?: string
  }) {
    try {
      const res = await http.post(`/v1/projects/${projectId}/scenes`, data)
      scenes.value.push(res.data)
      return res.data
    } catch (err: any) {
      const detail = err.status === 400 ? '请检查输入参数' : err.message
      useNotificationStore().error('创建场景失败', detail)
      throw err
    }
  }

  async function updateScene(id: number, data: Partial<Scene>) {
    try {
      const res = await http.put(`/v1/scenes/${id}`, data)
      const idx = scenes.value.findIndex(s => s.id === id)
      if (idx >= 0) scenes.value[idx] = res.data
      return res.data
    } catch (err: any) {
      useNotificationStore().error('更新场景失败', err.message)
      throw err
    }
  }

  async function deleteScene(id: number) {
    try {
      await http.delete(`/v1/scenes/${id}`)
      scenes.value = scenes.value.filter(s => s.id !== id)
    } catch (err: any) {
      if (err.status !== 404) {
        useNotificationStore().error('删除场景失败', err.message)
        throw err
      }
      scenes.value = scenes.value.filter(s => s.id !== id)
    }
  }

  async function extractScenes(scriptId: number) {
    generating.value = true
    try {
      await http.post(`/v1/scripts/${scriptId}/extract-scenes`)
      useNotificationStore().info('场景提取已启动', 'AI 正在从剧本提取场景信息...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动场景提取失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function fetchExtractedAssets(projectId: number) {
    try {
      const res = await http.get('/v1/scenes/extracted-assets', {
        params: { projectId },
      })
      extractedAssets.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载待确认场景失败', err.message)
    }
  }

  async function confirmExtractedAsset(assetId: number) {
    try {
      const res = await http.put(`/v1/extracted-assets/${assetId}/confirm-scene`)
      scenes.value.push(res.data)
      extractedAssets.value = extractedAssets.value.filter(a => a.id !== assetId)
      return res.data
    } catch (err: any) {
      const detail = err.status === 400 ? '该资产可能已被确认' : err.message
      useNotificationStore().error('确认场景资产失败', detail)
      throw err
    }
  }

  async function generateQuadView(sceneId: number) {
    generating.value = true
    try {
      await http.post(`/v1/scenes/${sceneId}/generate-quad-view`)
      useNotificationStore().info('四视图生成已启动', 'AI 正在生成场景四视图...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动四视图生成失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function regenerateView(sceneId: number, viewType: string) {
    generating.value = true
    try {
      await http.post(`/v1/scenes/${sceneId}/regenerate-view/${viewType}`)
      useNotificationStore().info('单视图重新生成已启动', `正在重新生成 ${getViewLabel(viewType)} 视图...`)
    } catch (err: any) {
      useNotificationStore().error('重新生成视图失败', err.message)
    } finally {
      generating.value = false
    }
  }

  function getViewLabel(viewType: string) {
    const map: Record<string, string> = {
      front: '正面', back: '背面', left: '左侧', right: '右侧',
    }
    return map[viewType] || viewType
  }

  return {
    scenes, extractedAssets, loading, generating,
    fetchScenes, createScene, updateScene, deleteScene,
    extractScenes, fetchExtractedAssets, confirmExtractedAsset,
    generateQuadView, regenerateView,
  }
})
