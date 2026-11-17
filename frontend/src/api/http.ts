
import axios from 'axios'

// 后端统一 API 响应格式
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 创建 Axios 实例
export const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
})

// 缓存当前 CSRF 信息
let csrfToken: string | null = null
let csrfHeaderName: string | null = null

// 防止多个请求重复获取 CSRF Token
let csrfPromise: Promise<void> | null = null

interface CsrfResponse {
  token: string
  headerName: string
}

// 获取 CSRF Token
async function fetchCsrfToken(): Promise<void> {
  // 单独使用 axios，避免触发 api 自己的拦截器
  const response = await axios.get<ApiResponse<CsrfResponse>>(
    '/api/auth/csrf',
    {
      withCredentials: true,
    },
  )

  csrfToken = response.data.data.token
  csrfHeaderName = response.data.data.headerName
}

// 确保已经获取 CSRF Token
async function ensureCsrfToken(): Promise<void> {
  if (csrfToken && csrfHeaderName) {
    return
  }

  if (!csrfPromise) {
    csrfPromise = fetchCsrfToken().finally(() => {
      csrfPromise = null
    })
  }

  await csrfPromise
}

// 清除本地缓存的 CSRF Token
export function clearCsrfToken(): void {
  csrfToken = null
  csrfHeaderName = null
}

// 请求拦截器
api.interceptors.request.use(async (config) => {
  const method = config.method?.toUpperCase() ?? 'GET'

  const requiresCsrf = ['POST', 'PUT', 'PATCH', 'DELETE']
    .includes(method)

  if (requiresCsrf) {
    await ensureCsrfToken()

    if (csrfToken && csrfHeaderName) {
      config.headers.set(csrfHeaderName, csrfToken)
    }
  }

  return config
})

// 响应拦截器
api.interceptors.response.use(
  (response) => response,

  (error) => {
    if (error.response?.status === 403) {
      // 可能是 CSRF 失效，也可能是权限不足
      // 清除缓存，下次请求重新获取
      clearCsrfToken()
    }

    return Promise.reject(error)
  },
)
