import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { Project, PipelineState } from '@/types'

/**
 * 项目上下文 Store - 管理当前项目与流水线状态
 */
export const useProjectStore = defineStore('project', () => {
  const currentProject = ref<Project | null>(null)
  const pipelineState = ref<PipelineState | null>(null)
  const projectList = ref<Project[]>([])
  const loading = ref(false)

  async function fetchProjects() {
    loading.value = true
    try {
      const res = await http.get('/v1/projects')
      projectList.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载项目列表失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function selectProject(projectId: number) {
    loading.value = true
    try {
      const res = await http.get(`/v1/projects/${projectId}`)
      currentProject.value = res.data
      await fetchPipelineState(projectId)
    } catch (err: any) {
      useNotificationStore().error('加载项目失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function fetchPipelineState(projectId: number) {
    try {
      const res = await http.get(`/v1/projects/${projectId}/pipeline-state`)
      pipelineState.value = res.data
    } catch (err: any) {
      pipelineState.value = null
      console.warn('[ProjectStore] 获取流水线状态失败:', err.message)
    }
  }

  async function advanceStage(projectId: number, nextStage: string) {
    try {
      const res = await http.post('/v1/projects/pipeline-states/advance', null, {
        params: { projectId, nextStage },
      })
      pipelineState.value = res.data
      if (currentProject.value) {
        currentProject.value.pipelineStage = nextStage as any
      }
    } catch (err: any) {
      useNotificationStore().error('推进阶段失败', err.message)
      throw err
    }
  }

  function clearProject() {
    currentProject.value = null
    pipelineState.value = null
  }

  return {
    currentProject,
    pipelineState,
    projectList,
    loading,
    fetchProjects,
    selectProject,
    fetchPipelineState,
    advanceStage,
    clearProject,
  }
})
