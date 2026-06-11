import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { Character, ExtractedAsset } from '@/types'

/**
 * Character module Store
 */
export const useCharacterStore = defineStore('character', () => {
  const characters = ref<Character[]>([])
  const extractedAssets = ref<ExtractedAsset[]>([])
  const loading = ref(false)
  const generating = ref(false)

  async function fetchCharacters(projectId: number) {
    loading.value = true
    try {
      const res = await http.get(`/v1/projects/${projectId}/characters`)
      characters.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载角色失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function createCharacter(projectId: number, data: Partial<Character>) {
    try {
      const res = await http.post(`/v1/projects/${projectId}/characters`, data)
      characters.value.push(res.data)
      return res.data
    } catch (err: any) {
      const detail = err.status === 400 ? '请检查输入参数' : err.message
      useNotificationStore().error('创建角色失败', detail)
      throw err
    }
  }

  async function updateCharacter(id: number, data: Partial<Character>) {
    try {
      const res = await http.put(`/v1/characters/${id}`, data)
      const idx = characters.value.findIndex(c => c.id === id)
      if (idx >= 0) characters.value[idx] = res.data
      return res.data
    } catch (err: any) {
      useNotificationStore().error('更新角色失败', err.message)
      throw err
    }
  }

  async function deleteCharacter(id: number) {
    try {
      await http.delete(`/v1/characters/${id}`)
      characters.value = characters.value.filter(c => c.id !== id)
    } catch (err: any) {
      if (err.status !== 404) {
        useNotificationStore().error('删除角色失败', err.message)
        throw err
      }
      characters.value = characters.value.filter(c => c.id !== id)
    }
  }

  async function extractCharacters(scriptId: number) {
    generating.value = true
    try {
      await http.post(`/v1/scripts/${scriptId}/extract-assets`, null, {
        params: { assetType: 'CHARACTER' },
      })
      useNotificationStore().info('角色提取已启动', 'AI 正在提取角色信息...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动角色提取失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function fetchExtractedAssets(projectId: number) {
    try {
      const res = await http.get('/v1/extracted-assets', {
        params: { projectId, type: 'CHARACTER' },
      })
      extractedAssets.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载待确认资产失败', err.message)
    }
  }

  async function confirmExtractedAsset(assetId: number) {
    try {
      const res = await http.put(`/v1/extracted-assets/${assetId}/confirm`)
      characters.value.push(res.data)
      extractedAssets.value = extractedAssets.value.filter(a => a.id !== assetId)
      return res.data
    } catch (err: any) {
      const detail = err.status === 400 ? '该资产可能已被确认' : err.message
      useNotificationStore().error('确认资产失败', detail)
      throw err
    }
  }

  async function generateMakeupImage(characterId: number) {
    generating.value = true
    try {
      await http.post(`/v1/characters/${characterId}/makeup`)
      useNotificationStore().info('定妆图生成已启动', 'AI 正在生成定妆图...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动定妆图生成失败', detail)
    } finally {
      generating.value = false
    }
  }

  return {
    characters, extractedAssets, loading, generating,
    fetchCharacters, createCharacter, updateCharacter, deleteCharacter,
    extractCharacters, fetchExtractedAssets, confirmExtractedAsset, generateMakeupImage,
  }
})
