import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { Script, Episode, Novel, ChapterSummary } from '@/types'

/**
 * Script module Store - manage scripts, episodes, novels
 */
export const useScriptStore = defineStore('script', () => {
  const scripts = ref<Script[]>([])
  const currentScript = ref<Script | null>(null)
  const episodes = ref<Episode[]>([])
  const novels = ref<Novel[]>([])
  const chapterSummaries = ref<ChapterSummary[]>([])
  const loading = ref(false)
  const generating = ref(false)

  // ==================== Scripts ====================

  async function fetchScripts(projectId: number) {
    loading.value = true
    try {
      const res = await http.get(`/v1/projects/${projectId}/scripts`)
      scripts.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载剧本失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function createScript(projectId: number, data: Partial<Script>) {
    try {
      const res = await http.post(`/v1/projects/${projectId}/scripts`, data)
      scripts.value.unshift(res.data)
      return res.data
    } catch (err: any) {
      // 400 = 参数校验失败，404 = 项目不存在
      const detail = err.status === 400 ? '请检查输入参数' : err.message
      useNotificationStore().error('创建剧本失败', detail)
      throw err
    }
  }

  async function updateScript(id: number, data: Partial<Script>) {
    try {
      const res = await http.put(`/v1/scripts/${id}`, data)
      const idx = scripts.value.findIndex(s => s.id === id)
      if (idx >= 0) scripts.value[idx] = res.data
      if (currentScript.value?.id === id) currentScript.value = res.data
      return res.data
    } catch (err: any) {
      useNotificationStore().error('更新剧本失败', err.message)
      throw err
    }
  }

  async function deleteScript(id: number) {
    try {
      await http.delete(`/v1/scripts/${id}`)
      scripts.value = scripts.value.filter(s => s.id !== id)
      if (currentScript.value?.id === id) currentScript.value = null
    } catch (err: any) {
      // 404 = 已被删除，不需要再提示
      if (err.status !== 404) {
        useNotificationStore().error('删除剧本失败', err.message)
        throw err
      }
      // 如果是 404，从本地列表移除即可
      scripts.value = scripts.value.filter(s => s.id !== id)
    }
  }

  async function selectScript(id: number) {
    try {
      const res = await http.get(`/v1/scripts/${id}`)
      currentScript.value = res.data
      await fetchEpisodes(id)
    } catch (err: any) {
      if (err.status === 404) {
        useNotificationStore().error('剧本不存在', '该剧本可能已被删除')
        currentScript.value = null
      } else {
        useNotificationStore().error('加载剧本失败', err.message)
      }
    }
  }

  async function generateOutline(scriptId: number) {
    generating.value = true
    try {
      await http.post(`/v1/scripts/${scriptId}/outline`)
      useNotificationStore().info('大纲生成已启动', 'AI 正在生成大纲，请稍候...')
    } catch (err: any) {
      // 502 = AI 服务不可用
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动大纲生成失败', detail)
    } finally {
      generating.value = false
    }
  }

  // ==================== Episodes ====================

  async function fetchEpisodes(scriptId: number) {
    try {
      const res = await http.get(`/v1/scripts/${scriptId}/episodes`)
      episodes.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载剧集失败', err.message)
    }
  }

  async function createEpisode(scriptId: number, data: Partial<Episode>) {
    try {
      const res = await http.post(`/v1/scripts/${scriptId}/episodes`, data)
      episodes.value.push(res.data)
      return res.data
    } catch (err: any) {
      useNotificationStore().error('创建剧集失败', err.message)
      throw err
    }
  }

  async function updateEpisode(id: number, data: Partial<Episode>) {
    try {
      const res = await http.put(`/v1/episodes/${id}`, data)
      const idx = episodes.value.findIndex(e => e.id === id)
      if (idx >= 0) episodes.value[idx] = res.data
      return res.data
    } catch (err: any) {
      useNotificationStore().error('更新剧集失败', err.message)
      throw err
    }
  }

  async function generateEpisodeScript(episodeId: number) {
    generating.value = true
    try {
      await http.post(`/v1/episodes/${episodeId}/generate-script`)
      useNotificationStore().info('剧本生成已启动', 'AI 正在生成剧集剧本，请稍候...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动剧本生成失败', detail)
    } finally {
      generating.value = false
    }
  }

  // ==================== Novels ====================

  async function fetchNovels(projectId: number) {
    try {
      const res = await http.get(`/v1/projects/${projectId}/novels`)
      novels.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载小说失败', err.message)
    }
  }

  async function uploadNovel(projectId: number, file: File, title?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (title) formData.append('title', title)
    try {
      const res = await http.post(`/v1/projects/${projectId}/novels/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      novels.value.unshift(res.data)
      return res.data
    } catch (err: any) {
      // 400 = 文件格式/大小不合规
      const detail = err.status === 400 ? err.message || '文件格式或大小不符合要求' : err.message
      useNotificationStore().error('上传小说失败', detail)
      throw err
    }
  }

  async function summarizeNovel(novelId: number) {
    generating.value = true
    try {
      await http.post(`/v1/novels/${novelId}/summarize`)
      useNotificationStore().info('分章摘要已启动', 'AI 正在生成章节摘要...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动摘要生成失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function fetchChapterSummaries(novelId: number) {
    try {
      const res = await http.get(`/v1/novels/${novelId}/summaries`)
      chapterSummaries.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载摘要失败', err.message)
    }
  }

  async function convertNovelToScript(novelId: number) {
    generating.value = true
    try {
      await http.post(`/v1/novels/${novelId}/convert`)
      useNotificationStore().info('转换已启动', 'AI 正在将小说转换为剧本...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动转换失败', detail)
    } finally {
      generating.value = false
    }
  }

  return {
    scripts, currentScript, episodes, novels, chapterSummaries,
    loading, generating,
    fetchScripts, createScript, updateScript, deleteScript, selectScript, generateOutline,
    fetchEpisodes, createEpisode, updateEpisode, generateEpisodeScript,
    fetchNovels, uploadNovel, summarizeNovel, fetchChapterSummaries, convertNovelToScript,
  }
})
