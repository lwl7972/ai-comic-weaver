import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { Storyboard, ShotSize, CameraAngle, CameraMovement, StoryboardStatus } from '@/types'

/**
 * Storyboard module Store
 */
export const useStoryboardStore = defineStore('storyboard', () => {
  const storyboards = ref<Storyboard[]>([])
  const loading = ref(false)
  const generating = ref(false)

  /**
   * 解析 JSON 字符串为数字数组（安全处理）
   */
  function parseJsonArray(jsonStr: string | undefined | null): number[] {
    if (!jsonStr) return []
    try {
      const parsed = JSON.parse(jsonStr)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }

  /**
   * 安全解析后端返回的 JSON 字符串字段
   */
  function parseStoryboardReferences(sb: Storyboard) {
    return {
      ...sb,
      _characterIds: parseJsonArray(sb.involvedCharacterIds),
      _sceneIds: parseJsonArray(sb.referenceImageUrls),
    }
  }

  async function fetchStoryboards(episodeId: number) {
    loading.value = true
    try {
      const res = await http.get(`/v1/episodes/${episodeId}/storyboards`)
      storyboards.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载分镜失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function createStoryboard(episodeId: number, data: Partial<Storyboard>) {
    try {
      const res = await http.post(`/v1/episodes/${episodeId}/storyboards`, data)
      storyboards.value.push(res.data)
      return res.data
    } catch (err: any) {
      const detail = err.status === 400 ? '请检查输入参数' : err.message
      useNotificationStore().error('创建分镜失败', detail)
      throw err
    }
  }

  async function updateStoryboard(id: number, data: Partial<Storyboard>) {
    try {
      const res = await http.put(`/v1/storyboards/${id}`, data)
      const idx = storyboards.value.findIndex(s => s.id === id)
      if (idx >= 0) storyboards.value[idx] = res.data
      return res.data
    } catch (err: any) {
      useNotificationStore().error('更新分镜失败', err.message)
      throw err
    }
  }

  async function deleteStoryboard(id: number) {
    try {
      await http.delete(`/v1/storyboards/${id}`)
      storyboards.value = storyboards.value.filter(s => s.id !== id)
    } catch (err: any) {
      if (err.status !== 404) {
        useNotificationStore().error('删除分镜失败', err.message)
        throw err
      }
      storyboards.value = storyboards.value.filter(s => s.id !== id)
    }
  }

  async function parseScript(episodeId: number) {
    generating.value = true
    try {
      await http.post(`/v1/episodes/${episodeId}/parse`)
      useNotificationStore().info('分镜解析已启动', 'AI 正在解析剧本为结构化分镜数据...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动分镜解析失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function batchUpdateStoryboards(data: Partial<Storyboard>[]) {
    try {
      const res = await http.post('/v1/storyboards/batch-update', data)
      storyboards.value = res.data
      return res.data
    } catch (err: any) {
      useNotificationStore().error('批量更新分镜失败', err.message)
      throw err
    }
  }

  async function generateImages(episodeId: number) {
    generating.value = true
    try {
      await http.post(`/v1/episodes/${episodeId}/generate-images`)
      useNotificationStore().info('分镜图生成已启动', 'AI 正在批量生成分镜图...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动分镜图生成失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function regenerateImage(storyboardId: number) {
    generating.value = true
    try {
      await http.post(`/v1/storyboards/${storyboardId}/generate-image`)
      useNotificationStore().info('分镜图重新生成已启动')
    } catch (err: any) {
      useNotificationStore().error('重新生成分镜图失败', err.message)
    } finally {
      generating.value = false
    }
  }

  /** 自动解析分镜的角色/场景引用 (名称→ID，收集参考图URL) */
  async function resolveReferences(storyboardId: number) {
    try {
      const res = await http.post(`/v1/storyboards/${storyboardId}/resolve-references`)
      const updated = res.data
      const idx = storyboards.value.findIndex(s => s.id === storyboardId)
      if (idx >= 0) storyboards.value[idx] = updated
      return updated
    } catch (err: any) {
      useNotificationStore().error('解析引用失败', err.message)
      throw err
    }
  }

  return {
    storyboards, loading, generating,
    fetchStoryboards, createStoryboard, updateStoryboard, deleteStoryboard,
    parseScript, batchUpdateStoryboards, generateImages, regenerateImage,
    resolveReferences, parseStoryboardReferences, parseJsonArray,
  }
})
