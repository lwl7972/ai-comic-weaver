import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/utils/http'
import { useNotificationStore } from './notification'
import type { ProjectTemplate } from '@/types'

/**
 * 项目模板 Store - 管理模板列表与从模板创建项目
 */
export const useTemplateStore = defineStore('template', () => {
  const templateList = ref<ProjectTemplate[]>([])
  const currentTemplate = ref<ProjectTemplate | null>(null)
  const loading = ref(false)

  async function fetchTemplates(type?: string) {
    loading.value = true
    try {
      const params: Record<string, string> = {}
      if (type) params.type = type
      const res = await http.get('/v1/templates', { params })
      templateList.value = res.data
    } catch (err: any) {
      useNotificationStore().error('加载模板列表失败', err.message)
    } finally {
      loading.value = false
    }
  }

  async function getTemplate(id: number) {
    loading.value = true
    try {
      const res = await http.get(`/v1/templates/${id}`)
      currentTemplate.value = res.data
      return res.data as ProjectTemplate
    } catch (err: any) {
      useNotificationStore().error('加载模板详情失败', err.message)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function createTemplate(data: Partial<ProjectTemplate>) {
    try {
      const res = await http.post('/v1/templates', data)
      useNotificationStore().success('模板创建成功')
      return res.data as ProjectTemplate
    } catch (err: any) {
      useNotificationStore().error('创建模板失败', err.message)
      throw err
    }
  }

  async function updateTemplate(id: number, data: Partial<ProjectTemplate>) {
    try {
      const res = await http.put(`/v1/templates/${id}`, data)
      useNotificationStore().success('模板更新成功')
      return res.data as ProjectTemplate
    } catch (err: any) {
      useNotificationStore().error('更新模板失败', err.message)
      throw err
    }
  }

  async function deleteTemplate(id: number) {
    try {
      await http.delete(`/v1/templates/${id}`)
      useNotificationStore().success('模板已删除')
      templateList.value = templateList.value.filter(t => t.id !== id)
    } catch (err: any) {
      useNotificationStore().error('删除模板失败', err.message)
      throw err
    }
  }

  async function createProjectFromTemplate(templateId: number, name: string, description: string) {
    try {
      const res = await http.post(`/v1/templates/${templateId}/create-project`, { name, description })
      useNotificationStore().success('从模板创建项目成功')
      return res.data
    } catch (err: any) {
      useNotificationStore().error('从模板创建项目失败', err.message)
      throw err
    }
  }

  return {
    templateList,
    currentTemplate,
    loading,
    fetchTemplates,
    getTemplate,
    createTemplate,
    updateTemplate,
    deleteTemplate,
    createProjectFromTemplate,
  }
})
