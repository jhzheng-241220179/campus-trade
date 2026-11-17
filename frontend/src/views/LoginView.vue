
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

import { login } from '../api/auth'
import { clearCsrfToken } from '../api/http'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  email: '',
  password: '',
})

const loading = ref(false)
const showPassword = ref(false)
const errorMessage = ref('')

async function handleLogin() {
  if (loading.value) return

  errorMessage.value = ''

  if (!form.email.trim() || !form.password) {
    errorMessage.value = '请输入邮箱和密码'
    return
  }

  loading.value = true

  let loginSucceeded = false

  try {
    // 1. 提交邮箱和密码
    await login({
      email: form.email.trim().toLowerCase(),
      password: form.password,
    })

    loginSucceeded = true

    // 2. 登录后旧 CSRF Token 可能失效
    clearCsrfToken()

    // 3. 从后端恢复当前用户信息
    await authStore.fetchCurrentUser()

    if (!authStore.isAuthenticated) {
      errorMessage.value = '登录状态获取失败，请刷新后重试'
      return
    }

    // 4. 登录完成，跳转首页
    await router.replace('/')
  } catch (error) {
    if (loginSucceeded) {
      errorMessage.value =
        '登录已提交成功，但用户信息获取失败，请刷新后重试'
    } else if (axios.isAxiosError(error)) {
      const status = error.response?.status

      if (status === 401) {
        errorMessage.value =
          '邮箱或密码错误，或账户尚未激活'
      } else {
        errorMessage.value =
          error.response?.data?.message ||
          '登录失败，请稍后重试'
      }
    } else {
      errorMessage.value = '登录失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <div class="login-card">
      <RouterLink to="/" class="brand">
        <span class="brand-icon">C</span>
        <span>CampusTrade</span>
      </RouterLink>

      <div class="heading">
        <p class="eyebrow">WELCOME BACK</p>
        <h1>欢迎回来</h1>
        <p>登录 CampusTrade，继续发现校园好物。</p>
      </div>

      <form @submit.prevent="handleLogin">
        <label for="email">邮箱地址</label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          autocomplete="email"
          placeholder="请输入邮箱地址"
          :disabled="loading"
        />

        <label for="password">登录密码</label>
        <div class="password-field">
          <input
            id="password"
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="请输入密码"
            :disabled="loading"
          />

          <button
            type="button"
            class="password-toggle"
            @click="showPassword = !showPassword"
          >
            {{ showPassword ? '隐藏' : '显示' }}
          </button>
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
          :disabled="loading"
        >
          {{ loading ? '正在登录...' : '登录 CampusTrade' }}
        </button>
      </form>

      <p class="register-link">
        还没有账户？
        <RouterLink to="/register">立即注册</RouterLink>
      </p>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 36px 20px;
  background: #f7f8fc;
}

.login-card {
  width: min(100%, 480px);
  padding: 42px;
  border: 1px solid #e5eaf3;
  border-radius: 22px;
  background: white;
  box-sizing: border-box;
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
  width: 37px;
  height: 37px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #4169e1;
  color: white;
}

.heading {
  margin: 40px 0 34px;
}

.eyebrow {
  color: #4169e1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.heading h1 {
  margin: 12px 0;
  color: #202536;
  font-size: 30px;
  font-weight: 750;
}

.heading > p:last-child {
  color: #788196;
  font-size: 14px;
  line-height: 1.8;
}

form {
  display: flex;
  flex-direction: column;
}

label {
  margin-bottom: 10px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

form > input,
.password-field input {
  width: 100%;
  height: 48px;
  padding: 0 14px;
  margin-bottom: 24px;
  border: 1px solid #dce3ef;
  border-radius: 9px;
  background: white;
  color: #202536;
  font: inherit;
  font-size: 14px;
  box-sizing: border-box;
}

input:focus {
  border-color: #4169e1;
  outline: 2px solid rgba(65, 105, 225, 0.12);
}

.password-field {
  position: relative;
}

.password-field input {
  padding-right: 74px;
}

.password-toggle {
  position: absolute;
  right: 14px;
  top: 15px;
  border: none;
  background: transparent;
  color: #4169e1;
  font-size: 13px;
  cursor: pointer;
}

.error-message {
  padding: 12px;
  margin: 0 0 20px;
  border-radius: 8px;
  background: #fef3f2;
  color: #b42318;
  font-size: 13px;
  line-height: 1.6;
}

.submit-button {
  height: 48px;
  border: none;
  border-radius: 9px;
  background: #4169e1;
  color: white;
  font-size: 15px;
  font-weight: 650;
  cursor: pointer;
}

.submit-button:hover:not(:disabled) {
  background: #3155c8;
}

.submit-button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.register-link {
  margin: 30px 0 0;
  text-align: center;
  color: #788196;
  font-size: 14px;
}

.register-link a {
  color: #4169e1;
  font-weight: 650;
  text-decoration: none;
}

@media (max-width: 520px) {
  .login-card {
    padding: 30px 24px;
  }

  .heading h1 {
    font-size: 26px;
  }
}
</style>
