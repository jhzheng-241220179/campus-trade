
<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

import {
  sendVerificationCode,
  verifyEmail,
} from '../api/auth'

const route = useRoute()
const router = useRouter()

const email = computed(() => {
  const value = route.query.email
  return typeof value === 'string' ? value.trim().toLowerCase() : ''
})

const code = ref('')
const loading = ref(false)
const sending = ref(false)

const errorMessage = ref('')
const successMessage = ref('')
const notice = ref(
  typeof route.query.notice === 'string'
    ? route.query.notice
    : '如果验证码已发送，请检查邮箱',
)

// 重新发送倒计时
const countdown = ref(60)
let timer: ReturnType<typeof setInterval> | null = null

function startCountdown() {
  if (timer !== null) {
    clearInterval(timer)
  }

  countdown.value = 60

  timer = setInterval(() => {
    if (countdown.value > 0) {
      countdown.value--
    }

    if (countdown.value === 0 && timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const canResend = computed(
  () => countdown.value === 0 && !sending.value,
)

function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || '请求失败，请稍后重试'
  }

  return '请求失败，请稍后重试'
}

// 提交验证码
async function handleVerify() {
  if (loading.value) return

  errorMessage.value = ''
  successMessage.value = ''

  if (!email.value) {
    errorMessage.value = '缺少邮箱地址，请返回注册页面'
    return
  }

  if (!/^[0-9]{6}$/.test(code.value)) {
    errorMessage.value = '请输入六位数字验证码'
    return
  }

  loading.value = true

  try {
    await verifyEmail({
      email: email.value,
      code: code.value,
    })

    successMessage.value = '邮箱验证成功，账户已激活'
    code.value = ''

    // 登录页面完成后，这里会跳转到 /login
    // 当前阶段先保留成功提示
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

// 重新发送验证码
async function handleResend() {
  if (!canResend.value || !email.value) return

  sending.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await sendVerificationCode(email.value)

    notice.value =
      '请求已受理。如果账户符合发送条件，请检查邮箱'

    startCountdown()
  } catch (error) {
    errorMessage.value = getErrorMessage(error)
  } finally {
    sending.value = false
  }
}

// 组件卸载时清理定时器
onUnmounted(() => {
  if (timer !== null) {
    clearInterval(timer)
  }
})

// 注册页面已尝试发送过验证码
startCountdown()

function goToRegister() {
  router.push('/register')
}
</script>

<template>
  <main class="verify-page">
    <div class="verify-card">
      <RouterLink to="/" class="brand">
        <span class="brand-icon">C</span>
        <span>CampusTrade</span>
      </RouterLink>

      <div class="heading">
        <p class="eyebrow">EMAIL VERIFICATION</p>
        <h1>验证你的邮箱</h1>
        <p>
          完成邮箱验证，即可激活 CampusTrade 账户。
        </p>
      </div>

      <div v-if="!email" class="error-message">
        缺少邮箱信息，请返回注册页面。
      </div>

      <template v-else>
        <div class="email-box">
          <span>验证邮箱</span>
          <strong>{{ email }}</strong>
        </div>

        <p class="notice">{{ notice }}</p>

        <form
          v-if="!successMessage"
          @submit.prevent="handleVerify"
        >
          <label for="code">六位数字验证码</label>

          <input
            id="code"
            v-model="code"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="请输入六位验证码"
            :disabled="loading"
          />

          <p class="hint">
            验证码有效期为 5 分钟。
          </p>

          <p
            v-if="errorMessage"
            class="error-message"
            role="alert"
          >
            {{ errorMessage }}
          </p>

          <button
            type="submit"
            class="primary-button"
            :disabled="loading"
          >
            {{ loading ? '正在验证...' : '验证并激活账户' }}
          </button>

          <button
            type="button"
            class="resend-button"
            :disabled="!canResend"
            @click="handleResend"
          >
            {{
              sending
                ? '发送中...'
                : countdown > 0
                  ? `${countdown} 秒后可重新发送`
                  : '重新发送验证码'
            }}
          </button>
        </form>

        <div v-else class="success-panel" role="status">
        <span class="success-icon">✓</span>
        <h2>邮箱验证成功</h2>
        <p>账户已经激活，可以使用邮箱和密码登录。</p>

        <RouterLink
            :to="{ path: '/login', query: { email } }"
            class="login-button"
        >
            前往登录
        </RouterLink>
        </div>
      </template>

      <button
        type="button"
        class="back-button"
        @click="goToRegister"
      >
        返回注册页面
      </button>
    </div>
  </main>
</template>

<style scoped>
.verify-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 36px 20px;
  background: #f7f8fc;
}

.verify-card {
  width: min(100%, 480px);
  padding: 42px;
  border: 1px solid #e5eaf3;
  border-radius: 22px;
  background: #fff;
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
}

.heading > p:last-child {
  color: #788196;
  font-size: 14px;
  line-height: 1.8;
}

.email-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px;
  background: #f1f5ff;
  border-radius: 10px;
}

.email-box span {
  color: #788196;
  font-size: 12px;
}

.email-box strong {
  color: #202536;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.notice {
  margin: 20px 0 28px;
  color: #64748b;
  font-size: 13px;
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

input {
  width: 100%;
  height: 52px;
  padding: 0 16px;
  border: 1px solid #dce3ef;
  border-radius: 9px;
  color: #202536;
  font-size: 22px;
  letter-spacing: 6px;
  text-align: center;
  box-sizing: border-box;
}

input:focus {
  border-color: #4169e1;
  outline: 2px solid rgba(65, 105, 225, 0.12);
}

.hint {
  margin: 12px 0 24px;
  color: #788196;
  font-size: 12px;
}

.primary-button {
  height: 48px;
  border: none;
  border-radius: 9px;
  background: #4169e1;
  color: white;
  font-size: 15px;
  font-weight: 650;
  cursor: pointer;
}

.primary-button:disabled {
  opacity: 0.65;
}

.resend-button {
  margin-top: 18px;
  padding: 10px;
  border: none;
  background: transparent;
  color: #4169e1;
  cursor: pointer;
}

.resend-button:disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

.error-message {
  margin: 12px 0;
  padding: 12px;
  border-radius: 8px;
  color: #b42318;
  background: #fef3f2;
  font-size: 13px;
}

.success-panel {
  padding: 30px 20px;
  text-align: center;
  border-radius: 12px;
  background: #effaf4;
}

.success-icon {
  color: #16803c;
  font-size: 42px;
}

.success-panel h2 {
  margin: 12px 0;
  color: #166534;
  font-size: 22px;
}

.success-panel p {
  color: #475569;
  font-size: 14px;
  line-height: 1.7;
}

.back-button {
  display: block;
  margin: 28px auto 0;
  border: none;
  background: transparent;
  color: #4169e1;
  cursor: pointer;
}

@media (max-width: 520px) {
  .verify-card {
    padding: 30px 24px;
  }
}
.login-button {
  display: block;
  margin-top: 24px;
  padding: 13px;
  border-radius: 9px;
  background: #4169e1;
  color: white;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.login-button:hover {
  background: #3155c8;
}
</style>
