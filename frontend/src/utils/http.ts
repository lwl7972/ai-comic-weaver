import axios from 'axios'
import type { AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'

/**
 * HTTP 客户端 - 统一请求封装
 * 开发模式代理到 localhost:8080
 * Electron 模式通过 electronAPI.getBackendPort() 获取动态端口
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor: attach backend port in Electron mode
http.interceptors.request.use(async (config) => {
  // In Electron, redirect API calls to the dynamic JVM port
  if ((window as any).electronAPI?.getBackendPort) {
    const port = await (window as any).electronAPI.getBackendPort()
    config.baseURL = `http://localhost:${port}/api`
  }
  return config
}, (error) => {
  return Promise.reject(error)
})

// Response interceptor: unwrap unified response format + global error toast
http.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 0) return { ...response, data }
    // Business error: show toast and reject
    const msg = message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误'
    // Only show toast for non-cancelled requests
    if (error.code !== 'ERR_CANCELED') {
      ElMessage.error(msg)
    }
    return Promise.reject(new Error(msg))
  },
)

export default http
