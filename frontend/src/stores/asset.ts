import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { AssetItem, AssetType } from '@/types'

/**
 * 素材库 Store
 */
export const useAssetStore = defineStore('asset', () => {
  const assetList = ref<AssetItem[]>([])
  const loading = ref(false)
  const currentAsset = ref<AssetItem | null>(null)

  async function fetchAssets(projectId: number, type?: AssetType, tags?: string) {
    loading.value = true
    try {
      const params: Record<string, string | number> = { projectId }
      if (type) params.type = type
      if (tags) params.tags = tags
      const res = await http.get('/v1/assets', { params })
      assetList.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载素材失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function uploadAsset(projectId: number, file: File, tags?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (tags) formData.append('tags', tags)
    try {
      const res = await http.post(`/v1/projects/${projectId}/assets/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      assetList.value.unshift(res.data)
      useNotificationStore().info('上传成功', `素材 ${file.name} 已上传`)
      return res.data
    } catch (err: any) {
      useNotificationStore().error('上传素材失败', err.message)
      throw err
    }
  }

  async function deleteAsset(id: number) {
    try {
      await http.delete(`/v1/assets/${id}`)
      assetList.value = assetList.value.filter(a => a.id !== id)
      useNotificationStore().info('删除成功', '素材已删除')
    } catch (err: any) {
      if (err.status !== 404) {
        useNotificationStore().error('删除素材失败', err.message)
        throw err
      }
      assetList.value = assetList.value.filter(a => a.id !== id)
    }
  }

  async function linkToCharacter(assetId: number, characterId: number) {
    try {
      const res = await http.put(`/v1/assets/${assetId}/link-character`, null, {
        params: { characterId },
      })
      const idx = assetList.value.findIndex(a => a.id === assetId)
      if (idx >= 0) assetList.value[idx] = res.data
      useNotificationStore().info('关联成功', '素材已关联角色')
      return res.data
    } catch (err: any) {
      useNotificationStore().error('关联角色失败', err.message)
      throw err
    }
  }

  async function linkToScene(assetId: number, sceneId: number) {
    try {
      const res = await http.put(`/v1/assets/${assetId}/link-scene`, null, {
        params: { sceneId },
      })
      const idx = assetList.value.findIndex(a => a.id === assetId)
      if (idx >= 0) assetList.value[idx] = res.data
      useNotificationStore().info('关联成功', '素材已关联场景')
      return res.data
    } catch (err: any) {
      useNotificationStore().error('关联场景失败', err.message)
      throw err
    }
  }

  return {
    assetList, loading, currentAsset,
    fetchAssets, uploadAsset, deleteAsset, linkToCharacter, linkToScene,
  }
})
