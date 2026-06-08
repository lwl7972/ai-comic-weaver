import axios from 'axios'
import type { AxiosInstance } from 'axios'

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
http.interceptors.request.use((config) => {
  // In Electron, redirect API calls to the dynamic JVM port
  if ((window as any).electronAPI?.getBackendPort) {
    const port = await (window as any).electronAPI.getBackendPort()
    config.baseURL = `http://localhost:${port}/api`
  }
  return config Promise.resolve(config)
}, (error) => {
  return Promise.reject(error)
})

// Response interceptor: unwrap unified response format
http.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 0) return { ...response, data }
    return Promise.reject(new Error(message || 'Request failed'))
  },
  (error) => {
    const msg = error.response?.data?.message || error.message
    return Promise.reject(new Error(msg))
  },
)

export default http
