
<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'

import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'

// ===== Axios 测试状态 =====
const loading = ref(false)
const result = ref('尚未测试')

// ===== Pinia 用户状态 =====
const authStore = useAuthStore()

const {
  user,
  isAuthenticated,
  initialized,
  loading: authLoading,
} = storeToRefs(authStore)

const authResult = ref('尚未检查登录状态')

// 测试 Axios 与后端连接
async function testApi() {
  loading.value = true
  result.value = '正在请求后端...'

  try {
    const response = await api.get('/auth/csrf')
    const data = response.data

    if (
      data.code === 200 &&
      data.data?.token &&
      data.data?.headerName
    ) {
      result.value = '请求成功：CSRF Token 已获取'
    } else {
      result.value = '请求完成，但响应数据不符合预期'
    }
  } catch (error) {
    console.error('API test failed:', error)
    result.value = '请求失败，请检查后端或浏览器控制台'
  } finally {
    loading.value = false
  }
}

// 测试 Pinia 获取当前用户信息
async function testAuthStore() {
  authResult.value = '正在检查登录状态...'

  try {
    await authStore.fetchCurrentUser()

    if (authStore.isAuthenticated) {
      authResult.value =
        '当前用户：' + authStore.user?.nickname
    } else {
      authResult.value = '当前尚未登录'
    }
  } catch (error) {
    console.error('Auth store test failed:', error)
    authResult.value = '获取登录状态失败'
  }
}
</script>

<template>
  <main class="demo-page">
    <div class="demo-card">
      <p class="eyebrow">DAY 21 · FRONTEND TEST</p>

      <h1>CampusTrade 接口测试</h1>

      <p class="description">
        验证 Axios 网络请求和 Pinia 用户状态管理。
      </p>

      <!-- Axios 测试 -->
      <section class="test-section">
        <h2>1. Axios 网络请求</h2>

        <p class="description">
          通过 Vite Proxy 请求 Spring Boot 的 CSRF 接口。
        </p>

        <button
          class="test-button"
          :disabled="loading"
          @click="testApi"
        >
          {{ loading ? '请求中...' : '测试后端连接' }}
        </button>

        <p class="result">{{ result }}</p>
      </section>

      <!-- Pinia 测试 -->
      <section class="test-section">
        <h2>2. Pinia 登录状态</h2>

        <p class="description">
          通过 GET /api/auth/me 查询当前 Session 的用户信息。
        </p>

        <button
          class="test-button"
          :disabled="authLoading"
          @click="testAuthStore"
        >
          {{ authLoading ? '检查中...' : '测试 Pinia 登录状态' }}
        </button>

        <p class="result">{{ authResult }}</p>

        <div class="status-panel">
          <p>
            Store 是否初始化：
            <strong>{{ initialized }}</strong>
          </p>

          <p>
            是否已登录：
            <strong>{{ isAuthenticated }}</strong>
          </p>

          <p v-if="user">
            当前昵称：
            <strong>{{ user.nickname }}</strong>
          </p>
        </div>
      </section>

      <RouterLink to="/" class="back-link">
        返回 CampusTrade 首页
      </RouterLink>
    </div>
  </main>
</template>

<style scoped>
.demo-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 24px;
  background: #f5f7fc;
}

.demo-card {
  width: min(100%, 620px);
  padding: 48px;
  background: white;
  border: 1px solid #e5eaf3;
  border-radius: 20px;
  box-sizing: border-box;
}

.eyebrow {
  color: #4169e1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

h1 {
  margin: 24px 0 16px;
  color: #202536;
  font-size: 30px;
}

h2 {
  margin: 0 0 12px;
  color: #202536;
  font-size: 20px;
}

.description {
  color: #64748b;
  line-height: 1.8;
}

.test-section {
  margin-top: 32px;
  padding-top: 28px;
  border-top: 1px solid #e5eaf3;
}

.test-button {
  margin-top: 20px;
  padding: 12px 22px;
  border: none;
  border-radius: 8px;
  background: #4169e1;
  color: white;
  font-size: 14px;
  cursor: pointer;
}

.test-button:hover:not(:disabled) {
  background: #3155c8;
}

.test-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.result {
  margin-top: 20px;
  color: #334155;
  line-height: 1.6;
}

.status-panel {
  margin-top: 20px;
  padding: 18px;
  background: #f1f5ff;
  border-radius: 10px;
}

.status-panel p {
  margin: 8px 0;
  color: #475569;
  font-size: 14px;
}

.status-panel strong {
  color: #202536;
}

.back-link {
  display: inline-block;
  margin-top: 32px;
  color: #4169e1;
  text-decoration: none;
  font-weight: 600;
}

.back-link:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .demo-card {
    padding: 28px 22px;
  }

  h1 {
    font-size: 25px;
  }
}
</style>
