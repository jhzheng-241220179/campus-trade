
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

const {
  user,
  isAuthenticated,
  initialized,
  loading,
} = storeToRefs(authStore)

const loggingOut = ref(false)
const authError = ref('')

const features = [
  {
    id: '01',
    title: '浏览好物',
    description: '发现校园里的闲置商品，寻找自己需要的物品。',
  },
  {
    id: '02',
    title: '发布闲置',
    description: '分享暂时用不到的物品，让闲置资源继续发挥价值。',
  },
  {
    id: '03',
    title: '预约交易',
    description: '通过清晰的交易流程，让买卖双方更方便地完成交易。',
  },
]

// 首页加载后，从后端恢复 Session 登录状态
onMounted(async () => {
  if (initialized.value) {
    return
  }

  try {
    await authStore.fetchCurrentUser()
  } catch (error) {
    console.error('Failed to restore user session:', error)
    authError.value = '登录状态获取失败，请刷新页面重试'
  }
})

// 退出登录
async function handleLogout() {
  if (loggingOut.value) {
    return
  }

  loggingOut.value = true
  authError.value = ''

  try {
    await authStore.logout()
  } catch (error) {
    console.error('Logout failed:', error)
    authError.value = '退出登录失败，请稍后重试'
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-icon">C</span>
        <span class="brand-name">CampusTrade</span>
      </div>

      <div class="topbar-actions">
        <!-- 正在获取登录状态 -->
        <span
          v-if="!initialized && loading"
          class="auth-status"
        >
          正在检查登录状态...
        </span>

        <!-- 登录状态已确认 -->
        <template v-else-if="initialized">
          <!-- 已登录 -->
          <template v-if="isAuthenticated && user">
            <span class="welcome-text">
              你好，{{ user.nickname }}
            </span>

            <button
              type="button"
              class="logout-button"
              :disabled="loggingOut"
              @click="handleLogout"
            >
              {{ loggingOut ? '退出中...' : '退出登录' }}
            </button>
          </template>

          <!-- 未登录 -->
          <template v-else>
            <RouterLink to="/login" class="login-button">
              登录
            </RouterLink>

            <RouterLink to="/register" class="register-button">
              注册
            </RouterLink>
          </template>
        </template>

        <!-- 初始化前 -->
        <span v-else class="auth-status">
          校园二手交易平台
        </span>
      </div>
    </header>

    <p v-if="authError" class="auth-error" role="alert">
      {{ authError }}
    </p>

    <main class="main-content">
      <section class="hero">
        <div class="hero-label">
          <span class="status-dot"></span>
          CAMPUS SECOND-HAND MARKET
        </div>

        <h1>
          让闲置好物，<br />
          <span>找到新的主人。</span>
        </h1>

        <p class="hero-description">
          一个面向校园生活的二手交易平台。<br />
          让物品流转更简单，让每一份闲置都物尽其用。
        </p>

        <div class="hero-tags">
          <span>校园闲置</span>
          <span>资源共享</span>
          <span>便捷交易</span>
        </div>
      </section>

      <section class="feature-section">
        <div class="section-heading">
          <span class="section-label">
            WHAT YOU CAN DO
          </span>

          <h2>让校园闲置流动起来</h2>

          <p>
            从发现商品到完成交易，让每一步都更加简单。
          </p>
        </div>

        <div class="feature-grid">
          <article
            v-for="feature in features"
            :key="feature.id"
            class="feature-card"
          >
            <span class="feature-number">
              {{ feature.id }}
            </span>

            <h3>{{ feature.title }}</h3>

            <p>{{ feature.description }}</p>
          </article>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <span>CampusTrade</span>
      <span>
        校园二手交易平台 ·
        {{ new Date().getFullYear() }}
      </span>
    </footer>
  </div>
</template>

<style scoped>
/* 首页顶部新增的用户操作区域 */
.topbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
}

.auth-status {
  color: #788196;
  font-size: 13px;
}

.welcome-text {
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.login-button,
.register-button,
.logout-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 17px;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.2s;
  box-sizing: border-box;
}

.login-button {
  color: #4169e1;
  background: transparent;
}

.login-button:hover {
  background: #edf1ff;
}

.register-button {
  border: 1px solid #4169e1;
  color: #ffffff;
  background: #4169e1;
}

.register-button:hover {
  background: #3155c8;
}

.logout-button {
  border: 1px solid #dce3ef;
  color: #475569;
  background: #ffffff;
}

.logout-button:hover:not(:disabled) {
  background: #f1f5f9;
}

.logout-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-error {
  padding: 12px 24px;
  color: #b42318;
  background: #fef3f2;
  font-size: 13px;
  text-align: center;
}

@media (max-width: 640px) {
  .topbar-actions {
    gap: 8px;
  }

  .welcome-text {
    font-size: 12px;
  }

  .login-button,
  .register-button,
  .logout-button {
    min-height: 34px;
    padding: 0 10px;
    font-size: 12px;
  }
}
</style>
