
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

import { register, sendVerificationCode } from '../api/auth'

const router = useRouter()

const form = reactive({
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const loading = ref(false)
const errorMessage = ref('')
const showPassword = ref(false)

function validateForm(): string | null {
  const nickname = form.nickname.trim()
  const email = form.email.trim()

  if (!nickname) {
    return '请输入昵称'
  }

  if (nickname.length > 64) {
    return '昵称不能超过64个字符'
  }

  if (!email) {
    return '请输入邮箱'
  }

  if (
    email.length > 254 ||
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  ) {
    return '邮箱格式不正确'
  }

  if (form.password.length < 12 || form.password.length > 72) {
    return '密码长度应在12到72个字符之间'
  }

  if (form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }

  return null
}

async function handleRegister() {
  if (loading.value) {
    return
  }

  errorMessage.value = ''

  const error = validateForm()

  if (error) {
    errorMessage.value = error
    return
  }

  loading.value = true

  const normalizedEmail = form.email.trim().toLowerCase()

  try {
    await register({
      nickname: form.nickname.trim(),
      email: normalizedEmail,
      password: form.password,
    })
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status

      if (status === 409) {
        errorMessage.value = '该邮箱已被注册'
      } else {
        errorMessage.value =
          error.response?.data?.message || '注册失败，请稍后重试'
      }
    } else {
      errorMessage.value = '注册失败，请稍后重试'
    }

    loading.value = false
    return
  }

  // 注册成功后尝试发送验证码。
  // 发送失败不应让用户误以为注册也失败。
  let sendNotice = '请检查邮箱并完成验证'

  try {
    await sendVerificationCode(normalizedEmail)
  } catch {
    sendNotice = '账户已创建，验证码发送未完成，请在验证页面重新发送'
  } finally {
    loading.value = false
  }

  await router.push({
    path: '/verify',
    query: {
      email: normalizedEmail,
      notice: sendNotice,
    },
  })
}
</script>

<template>
  <main class="register-page">
    <div class="register-card">
      <RouterLink to="/" class="brand">
        <span class="brand-icon">C</span>
        <span>CampusTrade</span>
      </RouterLink>

      <div class="heading">
        <p class="eyebrow">JOIN CAMPUSTRADE</p>
        <h1>创建你的账户</h1>
        <p>让闲置好物，找到新的主人。</p>
      </div>

      <form class="register-form" @submit.prevent="handleRegister">
        <label for="nickname">昵称</label>
        <input
          id="nickname"
          v-model="form.nickname"
          type="text"
          maxlength="64"
          autocomplete="nickname"
          placeholder="请输入昵称"
          :disabled="loading"
        />

        <label for="email">邮箱地址</label>
        <input
          id="email"
          v-model="form.email"
          type="email"
          maxlength="254"
          autocomplete="email"
          placeholder="请输入邮箱地址"
          :disabled="loading"
        />

        <label for="password">设置密码</label>
        <div class="password-field">
          <input
            id="password"
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="请输入12～72位密码"
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

        <label for="confirmPassword">确认密码</label>
        <input
          id="confirmPassword"
          v-model="form.confirmPassword"
          :type="showPassword ? 'text' : 'password'"
          autocomplete="new-password"
          placeholder="请再次输入密码"
          :disabled="loading"
        />

        <p class="form-hint">
          注册后需要通过邮箱验证码激活账户，才能登录。
        </p>

        <p v-if="errorMessage" class="error-message" role="alert">
          {{ errorMessage }}
        </p>

        <button
          type="submit"
          class="submit-button"
          :disabled="loading"
        >
          {{ loading ? '正在创建账户...' : '创建账户并发送验证码' }}
        </button>
      </form>

      <p class="login-link">
        已有账户？
        <RouterLink to="/login">立即登录</RouterLink>
      </p>
    </div>
  </main>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 36px 20px;
  background: #f7f8fc;
}

.register-card {
  width: min(100%, 480px);
  padding: 42px;
  border: 1px solid #e5eaf3;
  border-radius: 22px;
  background: #ffffff;
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
  color: white;
  background: #4169e1;
}

.heading {
  margin: 40px 0 30px;
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
}

.register-form {
  display: flex;
  flex-direction: column;
}

.register-form label {
  margin: 0 0 9px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.register-form > input,
.password-field input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  margin-bottom: 22px;
  border: 1px solid #dce3ef;
  border-radius: 9px;
  background: white;
  color: #202536;
  font: inherit;
  font-size: 14px;
  box-sizing: border-box;
}

.register-form input:focus {
  border-color: #4169e1;
  outline: 2px solid rgba(65, 105, 225, 0.12);
}

.register-form input:disabled {
  background: #f6f7fa;
}

.password-field {
  position: relative;
}

.password-field input {
  padding-right: 70px;
}

.password-toggle {
  position: absolute;
  right: 12px;
  top: 12px;
  border: none;
  background: transparent;
  color: #4169e1;
  font-size: 13px;
  cursor: pointer;
}

.form-hint {
  margin: 0 0 20px;
  color: #788196;
  font-size: 12px;
  line-height: 1.7;
}

.error-message {
  margin: 0 0 18px;
  padding: 12px;
  border-radius: 8px;
  color: #b42318;
  background: #fef3f2;
  font-size: 13px;
}

.submit-button {
  height: 48px;
  border: none;
  border-radius: 9px;
  background: #4169e1;
  color: #fff;
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

.login-link {
  margin: 28px 0 0;
  text-align: center;
  color: #788196;
  font-size: 14px;
}

.login-link a {
  color: #4169e1;
  font-weight: 650;
  text-decoration: none;
}

@media (max-width: 520px) {
  .register-card {
    padding: 30px 24px;
  }

  .heading h1 {
    font-size: 26px;
  }
}
</style>
