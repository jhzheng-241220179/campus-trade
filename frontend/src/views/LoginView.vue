
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

import { login } from '../api/auth'
import { clearCsrfToken } from '../api/http'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  email:
    typeof route.query.email === 'string'
      ? route.query.email
      : '',
  password: '',
})

const submitting = ref(false)
const errorMessage = ref('')

const redirectTarget = computed(() => {
  const redirect = route.query.redirect

  if (
    typeof redirect === 'string' &&
    redirect.startsWith('/') &&
    !redirect.startsWith('//') &&
    !redirect.startsWith('/\\')
  ) {
    return redirect
  }

  return '/'
})

async function handleLogin() {
  if (submitting.value) return

  errorMessage.value = ''

  if (!form.email.trim() || !form.password) {
    errorMessage.value = '请输入邮箱和密码'
    return
  }

  submitting.value = true

  try {
    await login({
      email: form.email.trim(),
      password: form.password,
    })

    // 登录后 Session 可能发生轮换，清除旧 CSRF 缓存。
    clearCsrfToken()

    await authStore.fetchCurrentUser()

    if (!authStore.isAuthenticated) {
      throw new Error('登录成功，但未能恢复用户状态')
    }

    await router.replace(redirectTarget.value)
  } catch (error) {
    if (axios.isAxiosError(error)) {
      if (error.response?.status === 401) {
        errorMessage.value = '邮箱或密码错误，或账户尚未激活'
      } else {
        errorMessage.value =
          error.response?.data?.message || '登录失败，请稍后重试'
      }
    } else {
      errorMessage.value =
        error instanceof Error
          ? error.message
          : '登录失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <div class="auth-container">
      <header class="auth-header">
        <RouterLink to="/" class="brand">
          <span class="brand-icon">C</span>
          <span>CampusTrade</span>
        </RouterLink>

        <RouterLink to="/" class="back-link">
          返回首页
        </RouterLink>
      </header>

      <section class="auth-content">
        <div class="auth-heading">
          <p class="eyebrow">WELCOME BACK</p>
          <h1>欢迎回来</h1>
          <p>登录 CampusTrade，继续你的校园好物之旅。</p>
        </div>

        <form
          class="auth-card"
          @submit.prevent="handleLogin"
        >
          <div class="field">
            <label for="login-email">邮箱地址</label>
            <input
              id="login-email"
              v-model="form.email"
              type="email"
              autocomplete="username"
              placeholder="请输入注册邮箱"
              required
            />
          </div>

          <div class="field">
            <label for="login-password">登录密码</label>
            <input
              id="login-password"
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              required
            />
          </div>

          <p
            v-if="errorMessage"
            class="error-message"
            role="alert"
          >
            {{ errorMessage }}
          </p>

          <button
            type="submit"
            class="submit-button"
            :disabled="submitting"
          >
            {{ submitting ? '登录中...' : '登录' }}
          </button>

          <p class="register-tip">
            还没有账户？
            <RouterLink to="/register">
              立即注册
            </RouterLink>
          </p>
        </form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: #f7f8fc;
  color: #202536;
}

.auth-container {
  width: min(1000px, 100%);
  margin: 0 auto;
  padding: 0 24px 80px;
  box-sizing: border-box;
}

.auth-header {
  min-height: 84px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid #e5eaf3;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #202536;
  font-size: 21px;
  font-weight: 750;
  text-decoration: none;
}

.brand-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: #4169e1;
  color: #ffffff;
}

.back-link {
  color: #64748b;
  font-size: 14px;
  text-decoration: none;
}

.auth-content {
  width: min(440px, 100%);
  margin: 76px auto 0;
}

.auth-heading {
  margin-bottom: 28px;
  text-align: center;
}

.eyebrow {
  color: #4169e1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.auth-heading h1 {
  margin: 14px 0;
  font-size: 36px;
}

.auth-heading > p:last-child {
  color: #788196;
  font-size: 14px;
  line-height: 1.8;
}

.auth-card {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 36px;
  border: 1px solid #e5eaf3;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(30, 48, 90, 0.04);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field label {
  color: #334155;
  font-size: 14px;
  font-weight: 650;
}

.field input {
  width: 100%;
  padding: 13px 14px;
  box-sizing: border-box;
  border: 1px solid #dce3ee;
  border-radius: 9px;
  outline: none;
  background: #ffffff;
  color: #202536;
  font: inherit;
  font-size: 14px;
}

.field input:focus {
  border-color: #4169e1;
}

.error-message {
  margin: 0;
  color: #dc2626;
  font-size: 13px;
  line-height: 1.7;
}

.submit-button {
  width: 100%;
  padding: 14px 20px;
  border: 0;
  border-radius: 9px;
  background: #4169e1;
  color: #ffffff;
  font-size: 15px;
  font-weight: 650;
  cursor: pointer;
}

.submit-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.register-tip {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  text-align: center;
}

.register-tip a {
  color: #4169e1;
  text-decoration: none;
}

@media (max-width: 600px) {
  .auth-container {
    padding-left: 16px;
    padding-right: 16px;
  }

  .auth-content {
    margin-top: 48px;
  }

  .auth-card {
    padding: 24px;
  }
}
</style>
