import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import { useStoryboardStore } from './storyboard'
import type { Storyboard } from '@/types'

export interface VideoStatus {
  totalShots: number
  videoDone: number
  videoError: number
  videoGenerating: number
  progress: number
}

export interface QueueStats {
  pendingCount: number
  runningCount: number
  completedCount: number
  failedCount: number
  cancelledCount: number
  totalCount: number
  paused: boolean
  maxConcurrent: number
}

export interface VideoTask {
  taskId: string
  taskType: string
  status: string
  priority: string
  progress: number
  errorMessage?: string
  videoUrl?: string
}

export const useDirectorStore = defineStore('director', () => {
  const videoStatus = ref<VideoStatus | null>(null)
  const queueStats = ref<QueueStats | null>(null)
  const tasks = ref<VideoTask[]>([])
  const generating = ref(false)

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

  async function fetchQueueStats() {
    try {
      const res = await http.get('/v1/director/queue/stats')
      queueStats.value = res.data
    } catch (err: any) {
      // Silently ignore errors
    }
  }

  async function fetchTasks() {
    try {
      const res = await http.get('/v1/director/tasks')
      tasks.value = res.data
    } catch (err: any) {
      // Silently ignore errors
    }
  }

  async function pauseQueue() {
    try {
      await http.post('/v1/director/queue/pause')
      useNotificationStore().success('队列已暂停')
      await fetchQueueStats()
    } catch (err: any) {
      useNotificationStore().error('暂停队列失败', err.message)
    }
  }

  async function resumeQueue() {
    try {
      await http.post('/v1/director/queue/resume')
      useNotificationStore().success('队列已恢复')
      await fetchQueueStats()
    } catch (err: any) {
      useNotificationStore().error('恢复队列失败', err.message)
    }
  }

  async function cancelTask(taskId: string) {
    try {
      await http.delete(`/v1/director/tasks/${taskId}`)
      useNotificationStore().success('任务已取消')
      await fetchTasks()
      await fetchQueueStats()
    } catch (err: any) {
      useNotificationStore().error('取消任务失败', err.message)
    }
  }

  async function generateFullVideo(episodeId: number) {
    generating.value = true
    try {
      await http.post(`/v1/episodes/${episodeId}/generate-videos`)
      useNotificationStore().info('整集视频生成已启动', 'AI 正在生成视频，请稍候...')
      await fetchTasks()
      await fetchQueueStats()
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
      await fetchTasks()
      await fetchQueueStats()
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
    storyboards, videoStatus, queueStats, tasks, generating,
    fetchStoryboards, fetchVideoStatus, fetchQueueStats, fetchTasks,
    pauseQueue, resumeQueue, cancelTask,
    generateFullVideo, generateShotVideo, concatVideos,
  }
})
