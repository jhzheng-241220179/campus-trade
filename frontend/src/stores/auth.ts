
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

import { api, clearCsrfToken } from '../api/http'
import type { ApiResponse } from '../api/http'
import { logout as logoutApi } from '../api/auth'

// 当前用户信息，与后端 CurrentUserResponse 对应
export interface CurrentUser {
  id: number
  nickname: string
  email: string
  role: string
  emailVerified: boolean
}

export const useAuthStore = defineStore('auth', () => {
  // State：当前用户
  const user = ref<CurrentUser | null>(null)

  // State：是否正在获取用户信息
  const loading = ref(false)

  // State：是否完成登录状态初始化
  const initialized = ref(false)

  // Getter：根据用户信息判断前端登录状态
  const isAuthenticated = computed(() => user.value !== null)

  // Action：从后端获取当前登录用户
  async function fetchCurrentUser(): Promise<void> {
    loading.value = true

    try {
      const response = await api.get<ApiResponse<CurrentUser>>(
        '/auth/me',
      )

      user.value = response.data.data
    } catch (error) {
      user.value = null

      // HTTP 401 表示当前没有有效的认证身份
      if (
        !axios.isAxiosError(error) ||
        error.response?.status !== 401
      ) {
        throw error
      }
    } finally {
      initialized.value = true
      loading.value = false
    }
  }

  // Action：清除本地用户状态
  function clearUser(): void {
    user.value = null
  }

  // Action：退出登录
  async function logout(): Promise<void> {
    // 后端退出成功后才清除本地状态
    await logoutApi()

    user.value = null
    initialized.value = true

    // Session 失效后清除旧 CSRF Token 缓存
    clearCsrfToken()
  }

  return {
    user,
    loading,
    initialized,
    isAuthenticated,
    fetchCurrentUser,
    clearUser,
    logout,
  }
})
