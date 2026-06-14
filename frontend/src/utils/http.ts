import axios from 'axios'
import type { AxiosInstance, AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

/**
 * HTTP 客户端 - 统一请求封装
 * 开发模式代理到 localhost:8080
 * Electron 模式通过 electronAPI.getBackendPort() 获取动态端口
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 前端日志：写入主进程文件 */
function appLog(level: string, message: string) {
  console[level === 'ERROR' ? 'error' : 'log'](`[${level}] ${message}`)
  try {
    (window as any).electronAPI?.log?.(level, message)
  } catch {}
}

const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Cached backend port for Electron mode
let cachedBackendPort: string | null = null

// Request interceptor: attach backend port in Electron mode
http.interceptors.request.use(async (config) => {
  // In Electron, redirect API calls to the dynamic JVM port
  if ((window as any).electronAPI?.getBackendPort) {
    if (!cachedBackendPort) {
      cachedBackendPort = await (window as any).electronAPI.getBackendPort()
    }
    config.baseURL = `http://localhost:${cachedBackendPort}/api`
  }
  appLog('INFO', `→ ${config.method?.toUpperCase()} ${config.baseURL}${config.url}`)
  return config
}, (error) => {
  appLog('ERROR', `→ Request error: ${error.message}`)
  return Promise.reject(error)
})

/**
 * 根据 HTTP 状态码和后端 code 差异化错误消息
 */
function classifyError(error: AxiosError): string {
  const status = error.response?.status
  const data: any = error.response?.data

  if (status === 404) {
    return data?.message || '请求的资源不存在'
  }
  if (status === 400) {
    return data?.message || '请求参数错误'
  }
  if (status === 401 || status === 403) {
    return '没有权限执行此操作'
  }
  if (status === 502 || status === 503) {
    return 'AI 服务暂不可用，请稍后重试'
  }
  if (status && status >= 500) {
    return data?.message || '服务器内部错误，请稍后重试'
  }
  if (error.code === 'ERR_CANCELED') {
    return '' // 取消的请求不提示
  }
  if (!error.response) {
    return '网络连接失败，请检查网络'
  }
  return data?.message || error.message || '请求失败'
}

// Response interceptor: unwrap unified response format + global error toast
http.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    appLog('INFO', `← ${response.status} ${response.config.url} code=${code}`)
    if (code === 0) return { ...response, data }
    // Business error: reject without toast — let Store decide how to display
    const msg = message || '请求失败'
    appLog('ERROR', `← Business error: ${response.config.url} msg=${msg}`)
    return Promise.reject(new Error(msg))
  },
  (error: AxiosError) => {
    const msg = classifyError(error)
    appLog('ERROR', `← ${error.config?.url || 'unknown'} ${error.response?.status || 'network'} ${msg}`)
    // Only show toast for non-cancelled requests with a message
    // Store-level handlers will also show notifications, so we keep this
    // as a fallback for unhandled cases (e.g. 401/403/502)
    if (msg && (error.response?.status === 401 || error.response?.status === 403 || error.response?.status === 502 || error.response?.status === 503)) {
      ElMessage.error(msg)
    }
    const enrichedError = new Error(msg) as Error & { status?: number; code?: string }
    enrichedError.status = error.response?.status
    enrichedError.code = error.code || undefined
    return Promise.reject(enrichedError)
  },
)

export default http
