import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'

/** 导出配置 */
export interface ExportForm {
  format: string
  resolution: number
  bitrate: number
  fps: number
}

/** 水印配置 */
export interface WatermarkForm {
  watermarkType: 'TEXT' | 'IMAGE'
  watermarkContent: string
}

/**
 * S-Level module Store
 */
export const useSLevelStore = defineStore('slevel', () => {
  const composeStep = ref(0)
  const loading = ref(false)
  const generating = ref(false)

  async function composeVideo(episodeId: number) {
    generating.value = true
    composeStep.value = 1
    try {
      await http.post(`/v1/episodes/${episodeId}/compose`)
      useNotificationStore().info('成片合成已启动', '正在执行 FFmpeg 合成流程...')
    } catch (err: any) {
      const detail = err.status === 502 ? 'FFmpeg 服务暂不可用，请稍后重试' : err.message
      useNotificationStore().error('合成失败', detail)
      composeStep.value = 0
    } finally {
      generating.value = false
    }
  }

  async function exportVideo(projectId: number, form: ExportForm) {
    generating.value = true
    try {
      await http.post('/v1/video/export', {
        projectId,
        ...form,
      })
      useNotificationStore().info('视频导出已启动', `正在导出 ${form.format} ${form.resolution}p...`)
    } catch (err: any) {
      useNotificationStore().error('导出失败', err.message)
    } finally {
      generating.value = false
    }
  }

  async function addWatermark(projectId: number, form: WatermarkForm) {
    generating.value = true
    try {
      await http.post('/v1/video/watermark', {
        projectId,
        ...form,
      })
      useNotificationStore().info('水印添加已启动')
    } catch (err: any) {
      useNotificationStore().error('添加水印失败', err.message)
    } finally {
      generating.value = false
    }
  }

  function setComposeStep(step: number) {
    composeStep.value = step
  }

  return {
    composeStep, loading, generating,
    composeVideo, exportVideo, addWatermark, setComposeStep,
  }
})
