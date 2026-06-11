import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import { useStoryboardStore } from './storyboard'
import type { Storyboard } from '@/types'

/** 视频生成进度 */
export interface VideoStatus {
  totalShots: number
  videoDone: number
  videoError: number
  videoGenerating: number
  progress: number
}

/**
 * Director module Store
 * 复用 storyboardStore 的分镜数据，避免数据覆盖问题
 */
export const useDirectorStore = defineStore('director', () => {
  const videoStatus = ref<VideoStatus | null>(null)
  const generating = ref(false)

  // 复用 storyboardStore 的分镜列表，不维护独立副本
  const storyboardStore = useStoryboardStore()
  const storyboards = storyboardStore.storyboards

  async function fetchStoryboards(episodeId: number) {
    await storyboardStore.fetchStoryboards(episodeId)
  }

  async function fetchVideoStatus(episodeId: number) {
    try {
      const res = await http.get(`/v1/episodes/${episodeId}/video-status`)
      videoStatus.value = res.data
    } catch {
      // Silently ignore polling errors
    }
  }

  async function generateFullVideo(episodeId: number) {
    generating.value = true
    try {
      await http.post(`/v1/episodes/${episodeId}/generate-videos`)
      useNotificationStore().info('整集视频生成已启动', 'AI 正在生成视频，请稍候...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('启动视频生成失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function generateShotVideo(storyboardId: number) {
    generating.value = true
    try {
      await http.post(`/v1/storyboards/${storyboardId}/generate-video`)
      useNotificationStore().info('单镜头视频生成已启动')
    } catch (err: any) {
      const detail = err.status === 502 ? 'AI 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('生成视频失败', detail)
    } finally {
      generating.value = false
    }
  }

  async function concatVideos(episodeId: number) {
    generating.value = true
    try {
      await http.post(`/v1/episodes/${episodeId}/concat-videos`)
      useNotificationStore().info('FFmpeg 拼接已启动', '正在拼接视频片段...')
    } catch (err: any) {
      useNotificationStore().error('拼接失败', err.message)
    } finally {
      generating.value = false
    }
  }

  return {
    storyboards, videoStatus, generating,
    fetchStoryboards, fetchVideoStatus,
    generateFullVideo, generateShotVideo, concatVideos,
  }
})
