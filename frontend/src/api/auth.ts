
import { api } from './http'
import type { ApiResponse } from './http'

// 注册请求
export interface RegisterRequest {
  email: string
  password: string
  nickname: string
}

// 登录请求
export interface LoginRequest {
  email: string
  password: string
}

// 邮箱验证请求
export interface VerifyEmailRequest {
  email: string
  code: string
}

// 注册账户
export async function register(
  data: RegisterRequest,
): Promise<number> {
  const response = await api.post<ApiResponse<number>>(
    '/auth/register',
    data,
  )

  return response.data.data
}

// 发送邮箱验证码
export async function sendVerificationCode(
  email: string,
): Promise<void> {
  await api.post<ApiResponse<null>>(
    '/auth/email/send-code',
    { email },
  )
}

// 验证邮箱
export async function verifyEmail(
  data: VerifyEmailRequest,
): Promise<void> {
  await api.post<ApiResponse<null>>(
    '/auth/email/verify',
    data,
  )
}

// 密码登录
export async function login(
  data: LoginRequest,
): Promise<string> {
  const response = await api.post<ApiResponse<string>>(
    '/auth/login',
    data,
  )

  return response.data.data
}

// 退出登录
// 后端成功返回 HTTP 204，没有响应体
export async function logout(): Promise<void> {
  await api.post('/auth/logout')
}
