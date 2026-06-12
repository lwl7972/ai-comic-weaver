import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { PipelineState, PipelineStage } from '@/types'

/**
 * 流水线状态 Store - 管理流水线阶段和脏标记
 */
export const usePipelineStore = defineStore('pipeline', () => {
  const pipelineState = ref<PipelineState | null>(null)
  const loading = ref(false)

  // 脏标记映射：阶段 -> 对应的 dirty 字段
  const dirtyFieldMap: Record<PipelineStage, keyof PipelineState> = {
    SCRIPT: 'scriptDirty',
    CHARACTER: 'characterDirty',
    SCENE: 'sceneDirty',
    STORYBOARD: 'storyboardDirty',
    DIRECTOR: 'directorDirty',
    OUTPUT: 'sLevelDirty',
  }

  // 计算属性：各阶段脏标记状态
  const isScriptDirty = computed(() => pipelineState.value?.scriptDirty ?? false)
  const isCharacterDirty = computed(() => pipelineState.value?.characterDirty ?? false)
  const isSceneDirty = computed(() => pipelineState.value?.sceneDirty ?? false)
  const isStoryboardDirty = computed(() => pipelineState.value?.storyboardDirty ?? false)
  const isDirectorDirty = computed(() => pipelineState.value?.directorDirty ?? false)
  const isOutputDirty = computed(() => pipelineState.value?.sLevelDirty ?? false)

  /**
   * 获取流水线状态
   */
  async function fetchPipelineState(projectId: number) {
    loading.value = true
    try {
      const res = await http.get(`/v1/projects/${projectId}/pipeline-state`)
      pipelineState.value = res.data
    } catch (err: any) {
      pipelineState.value = null
      console.warn('[PipelineStore] 获取流水线状态失败:', err.message)
    } finally {
      loading.value = false
    }
  }

  /**
   * 推进流水线阶段
   */
  async function advanceStage(projectId: number, targetStage: PipelineStage, reExecute: boolean = false) {
    loading.value = true
    try {
      const res = await http.post(`/v1/projects/${projectId}/pipeline-advance`, {
        targetStage,
        reExecute,
      })
      pipelineState.value = res.data
      useNotificationStore().success('阶段推进成功', `已进入 ${stageDisplayName(targetStage)} 阶段`)
      return res.data
    } catch (err: any) {
      useNotificationStore().error('推进阶段失败', err.message)
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * 清除指定阶段的脏标记
   */
  async function clearDirtyFlag(projectId: number, stage: PipelineStage) {
    try {
      const res = await http.post(`/v1/projects/${projectId}/pipeline-clear-dirty`, null, {
        params: { stage },
      })
      pipelineState.value = res.data
    } catch (err: any) {
      console.warn('[PipelineStore] 清除脏标记失败:', err.message)
    }
  }

  /**
   * 检查指定阶段是否有脏标记
   */
  function isStageDirty(stage: PipelineStage): boolean {
    if (!pipelineState.value) return false
    const field = dirtyFieldMap[stage]
    return pipelineState.value[field] as boolean
  }

  /**
   * 获取阶段显示名称
   */
  function stageDisplayName(stage: PipelineStage): string {
    const names: Record<PipelineStage, string> = {
      SCRIPT: '剧本',
      CHARACTER: '角色',
      SCENE: '场景',
      STORYBOARD: '分镜',
      DIRECTOR: '导演',
      OUTPUT: 'S级',
    }
    return names[stage]
  }

  /**
   * 清空状态
   */
  function clearState() {
    pipelineState.value = null
  }

  return {
    pipelineState,
    loading,
    isScriptDirty,
    isCharacterDirty,
    isSceneDirty,
    isStoryboardDirty,
    isDirectorDirty,
    isOutputDirty,
    fetchPipelineState,
    advanceStage,
    clearDirtyFlag,
    isStageDirty,
    stageDisplayName,
    clearState,
  }
})
