
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'

import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const {
  user,
  isAuthenticated,
  initialized,
  loading,
} = storeToRefs(authStore)

const isAdmin = computed(
  () =>
    initialized.value &&
    isAuthenticated.value &&
    user.value?.role === 'ADMIN',
)

const loggingOut = ref(false)
const errorMessage = ref('')

const features = [
  {
    number: '01',
    title: '浏览校园好物',
    description: '发现校园里的闲置教材、数码设备与生活用品。',
    path: '/products',
    label: '浏览商品',
  },
  {
    number: '02',
    title: '发布闲置商品',
    description: '填写商品信息，保存草稿，确认后即可上架。',
    path: '/products/new',
    label: '发布商品',
  },
  {
    number: '03',
    title: '管理我的商品',
    description: '查看自己的商品草稿与已发布商品。',
    path: '/products/mine',
    label: '我的商品',
  },
]

onMounted(async () => {
  if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      // 未登录时仍允许正常访问首页。
    }
  }
})

async function handleLogout() {
  if (loggingOut.value) return

  loggingOut.value = true
  errorMessage.value = ''

  try {
    await authStore.logout()
    await router.replace('/')
  } catch {
    errorMessage.value = '退出登录失败，请稍后重试'
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="home-page">
    <header class="site-header">
      <div class="container header-content">
        <RouterLink to="/" class="brand">
          <span class="brand-icon">C</span>
          <span>CampusTrade</span>
        </RouterLink>

        <nav class="header-nav" aria-label="主导航">
          <RouterLink to="/products" class="nav-link">
            浏览商品
          </RouterLink>

          <template v-if="initialized && isAuthenticated">
            <RouterLink to="/products/new" class="nav-link">
              发布商品
            </RouterLink>

            <RouterLink to="/products/mine" class="nav-link">
              我的商品
            </RouterLink>

            <RouterLink
              v-if="isAdmin"
              to="/admin"
              class="admin-link"
            >
              管理后台
            </RouterLink>

            <span class="user-name">
              你好，{{ user?.nickname || '用户' }}
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

          <template v-else-if="initialized">
            <RouterLink to="/login" class="nav-link">
              登录
            </RouterLink>

            <RouterLink to="/register" class="register-button">
              注册
            </RouterLink>
          </template>

          <span v-else class="checking-text">
            {{ loading ? '检查登录状态...' : '' }}
          </span>
        </nav>
      </div>
    </header>

    <main>
      <section class="hero">
        <div class="container hero-content">
          <div class="hero-copy">
            <span class="eyebrow">
              CAMPUS SECOND-HAND MARKETPLACE
            </span>

            <h1>
              闲置流转，
              <span>让校园生活更简单。</span>
            </h1>

            <p class="hero-description">
              CampusTrade 为校园二手交易提供便捷的平台。
              从教材到生活用品，让闲置物品找到新的主人。
            </p>

            <div class="hero-actions">
              <RouterLink
                to="/products"
                class="primary-button"
              >
                去逛商品广场
              </RouterLink>

              <RouterLink
                v-if="initialized && isAuthenticated"
                to="/products/new"
                class="secondary-button"
              >
                发布我的闲置
              </RouterLink>

              <RouterLink
                v-else
                to="/register"
                class="secondary-button"
              >
                加入 CampusTrade
              </RouterLink>
            </div>

            <p
              v-if="errorMessage"
              class="error-message"
              role="alert"
            >
              {{ errorMessage }}
            </p>
          </div>

          <div class="hero-visual">
            <div class="visual-panel">
              <div class="visual-icon">CT</div>
              <p class="visual-title">
                好物循环，价值延续
              </p>
              <p class="visual-description">
                CAMPUS TRADE
              </p>
            </div>
          </div>
        </div>
      </section>

      <section class="features-section">
        <div class="container">
          <div class="section-heading">
            <span class="eyebrow">EXPLORE CAMPUS TRADE</span>
            <h2>从发现到分享</h2>
            <p>浏览、发布和管理商品，从这里开始。</p>
          </div>

          <div class="features-grid">
            <RouterLink
              v-for="feature in features"
              :key="feature.number"
              :to="feature.path"
              class="feature-card"
            >
              <span class="feature-number">
                {{ feature.number }}
              </span>

              <h3>{{ feature.title }}</h3>

              <p>{{ feature.description }}</p>

              <span class="feature-link">
                {{ feature.label }}
                <span aria-hidden="true">→</span>
              </span>
            </RouterLink>
          </div>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <div class="container footer-content">
        <span>CampusTrade</span>
        <span>校园二手交易平台</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #ffffff;
  color: #202536;
}

.container {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding-left: 28px;
  padding-right: 28px;
  box-sizing: border-box;
}

.site-header {
  border-bottom: 1px solid #e8edf5;
  background: #ffffff;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  min-height: 84px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #202536;
  font-size: 21px;
  font-weight: 750;
  text-decoration: none;
  white-space: nowrap;
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

.header-nav {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 18px;
}

.nav-link {
  color: #475569;
  font-size: 14px;
  text-decoration: none;
  white-space: nowrap;
}

.nav-link:hover {
  color: #4169e1;
}

.admin-link {
  display: inline-flex;
  align-items: center;
  padding: 9px 13px;
  border: 1px solid #cdd8fa;
  border-radius: 9px;
  background: #eef3ff;
  color: #3458c6;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
  white-space: nowrap;
}

.admin-link:hover {
  background: #dfe8ff;
}

.user-name {
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.checking-text {
  color: #94a3b8;
  font-size: 13px;
}

.register-button,
.logout-button {
  padding: 10px 16px;
  border-radius: 9px;
  font-size: 14px;
  text-decoration: none;
}

.register-button {
  background: #4169e1;
  color: #ffffff;
}

.logout-button {
  border: 1px solid #dce3ee;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
}

.logout-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hero {
  background: linear-gradient(
    135deg,
    #f8faff 0%,
    #eef3ff 100%
  );
}

.hero-content {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  align-items: center;
  gap: 48px;
  min-height: 490px;
  padding-top: 65px;
  padding-bottom: 65px;
}

.eyebrow {
  color: #4169e1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.hero-copy h1 {
  margin: 22px 0;
  color: #202536;
  font-size: clamp(34px, 4.5vw, 53px);
  font-weight: 800;
  line-height: 1.35;
}

.hero-copy h1 span {
  display: block;
  color: #4169e1;
}

.hero-description {
  max-width: 520px;
  color: #64748b;
  font-size: 15px;
  line-height: 2;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 36px;
}

.primary-button,
.secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 13px 24px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.primary-button {
  background: #4169e1;
  color: #ffffff;
}

.secondary-button {
  border: 1px solid #cdd8fa;
  background: #ffffff;
  color: #4169e1;
}

.hero-visual {
  display: grid;
  place-items: center;
}

.visual-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: min(100%, 380px);
  min-height: 310px;
  padding: 24px;
  border: 1px solid #e1e8fa;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 20px 60px rgba(65, 105, 225, 0.07);
  box-sizing: border-box;
}

.visual-icon {
  display: grid;
  place-items: center;
  width: 110px;
  height: 110px;
  border-radius: 26px;
  background: #e7edff;
  color: #4169e1;
  font-size: 38px;
  font-weight: 800;
}

.visual-title {
  margin: 28px 0 8px;
  font-size: 19px;
  font-weight: 700;
}

.visual-description {
  color: #94a3b8;
  font-size: 11px;
  letter-spacing: 3px;
}

.error-message {
  color: #dc2626;
  font-size: 13px;
}

.features-section {
  padding: 88px 0 100px;
}

.section-heading {
  margin-bottom: 34px;
}

.section-heading h2 {
  margin: 14px 0;
  font-size: 32px;
}

.section-heading p {
  color: #788196;
  font-size: 14px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

.feature-card {
  display: block;
  min-height: 240px;
  padding: 30px;
  border: 1px solid #e5eaf3;
  border-radius: 16px;
  background: #ffffff;
  color: inherit;
  text-decoration: none;
  box-sizing: border-box;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(30, 48, 90, 0.08);
}

.feature-number {
  color: #4169e1;
  font-size: 14px;
  font-weight: 700;
}

.feature-card h3 {
  margin: 24px 0 12px;
  font-size: 19px;
}

.feature-card p {
  min-height: 56px;
  color: #788196;
  font-size: 13px;
  line-height: 1.8;
}

.feature-link {
  display: inline-flex;
  gap: 10px;
  margin-top: 12px;
  color: #4169e1;
  font-size: 13px;
  font-weight: 650;
}

.site-footer {
  border-top: 1px solid #e8edf5;
  background: #ffffff;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding-top: 26px;
  padding-bottom: 26px;
  color: #94a3b8;
  font-size: 13px;
}

@media (max-width: 900px) {
  .hero-content {
    grid-template-columns: 1fr;
  }

  .hero-visual {
    display: none;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .feature-card {
    min-height: auto;
  }
}

@media (max-width: 640px) {
  .container {
    padding-left: 18px;
    padding-right: 18px;
  }

  .header-content {
    align-items: flex-start;
    flex-direction: column;
    padding-top: 20px;
    padding-bottom: 20px;
  }

  .header-nav {
    justify-content: flex-start;
    gap: 14px;
  }

  .hero-content {
    min-height: 400px;
    padding-top: 56px;
    padding-bottom: 56px;
  }

  .features-section {
    padding: 60px 0;
  }

  .footer-content {
    flex-direction: column;
  }
}
</style>
