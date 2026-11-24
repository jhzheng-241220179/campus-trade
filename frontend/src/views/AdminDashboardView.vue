
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

import {
  banUser,
  forceOffShelfProduct,
  getAdminProducts,
  getAdminUsers,
  unbanUser,
} from '../api/admin'

import type {
  AdminProduct,
  AdminUser,
} from '../api/admin'

type Tab = 'users' | 'products'

const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref<Tab>('users')
const users = ref<AdminUser[]>([])
const products = ref<AdminProduct[]>([])

const loading = ref(true)
const refreshing = ref(false)
const busyAction = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const isAdmin = computed(
  () => authStore.user?.role === 'ADMIN',
)

const activeUsers = computed(
  () => users.value.filter(
    (user) => user.status === 'ACTIVE',
  ).length,
)

const publishedProducts = computed(
  () => products.value.filter(
    (product) => product.status === 'PUBLISHED',
  ).length,
)

const offShelfProducts = computed(
  () => products.value.filter(
    (product) => product.status === 'OFF_SHELF',
  ).length,
)

function statusLabel(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return '正常'
    case 'BANNED':
      return '已封禁'
    case 'PENDING_VERIFICATION':
      return '待验证'
    case 'DRAFT':
      return '草稿'
    case 'PUBLISHED':
      return '已发布'
    case 'OFF_SHELF':
      return '已下架'
    default:
      return status
  }
}

function formatPrice(price: number): string {
  const amount = Number(price)
  return Number.isFinite(amount)
    ? `¥${amount.toFixed(2)}`
    : '价格未知'
}

function formatTime(time: string): string {
  if (!time) return '—'
  const date = new Date(time)
  return Number.isNaN(date.getTime())
    ? time
    : date.toLocaleString('zh-CN')
}

function getErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 401) {
      return '登录已失效，请重新登录'
    }

    if (error.response?.status === 403) {
      return '没有管理员操作权限'
    }

    if (error.response?.status === 409) {
      return '目标状态已发生变化，请刷新后重试'
    }

    const message = error.response?.data?.message

    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }

  return fallback
}

async function loadData(initial = false) {
  if (initial) {
    loading.value = true
  } else {
    refreshing.value = true
  }

  errorMessage.value = ''

  try {
    const [userResult, productResult] = await Promise.all([
      getAdminUsers(),
      getAdminProducts(),
    ])

    users.value = userResult
    products.value = productResult
  } catch (error) {
    errorMessage.value = getErrorMessage(
      error,
      '管理员数据加载失败',
    )
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function handleBan(user: AdminUser) {
  if (busyAction.value || user.role !== 'USER') return

  if (user.status !== 'ACTIVE') return

  const confirmed = window.confirm(
    `确定封禁用户“${user.nickname}”（#${user.id}）吗？\n\n` +
    '封禁后，该用户将无法继续操作账户，' +
    '其所有已发布商品会撤回为草稿。\n' +
    '原有草稿和图片将保留。',
  )

  if (!confirmed) return

  busyAction.value = `ban-${user.id}`
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await banUser(user.id)

    successMessage.value =
      `用户 #${user.id} 已封禁，已发布商品已撤回为草稿`

    await loadData()
  } catch (error) {
    errorMessage.value = getErrorMessage(
      error,
      '封禁用户失败',
    )
  } finally {
    busyAction.value = ''
  }
}

async function handleUnban(user: AdminUser) {
  if (busyAction.value || user.role !== 'USER') return
  if (user.status !== 'BANNED') return

  const confirmed = window.confirm(
    `确定解封用户“${user.nickname}”（#${user.id}）吗？\n\n` +
    '解封后，用户可以重新登录。' +
    '之前撤回的商品仍保持草稿状态，' +
    '需要用户主动发布。',
  )

  if (!confirmed) return

  busyAction.value = `unban-${user.id}`
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await unbanUser(user.id)

    successMessage.value = `用户 #${user.id} 已解封`
    await loadData()
  } catch (error) {
    errorMessage.value = getErrorMessage(
      error,
      '解封用户失败',
    )
  } finally {
    busyAction.value = ''
  }
}

async function handleOffShelf(product: AdminProduct) {
  if (busyAction.value) return
  if (product.status !== 'PUBLISHED') return

  const confirmed = window.confirm(
    `确定强制下架“${product.title}”（#${product.id}）吗？\n\n` +
    '商品将从公开广场和详情页撤下。' +
    '商品记录及图片仍会保留，' +
    '卖家无法直接重新发布该商品。',
  )

  if (!confirmed) return

  busyAction.value = `off-${product.id}`
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await forceOffShelfProduct(product.id)

    successMessage.value =
      `商品 #${product.id} 已强制下架`

    await loadData()
  } catch (error) {
    errorMessage.value = getErrorMessage(
      error,
      '强制下架失败',
    )
  } finally {
    busyAction.value = ''
  }
}

onMounted(async () => {
  try {
    await authStore.fetchCurrentUser()
  } catch {
    errorMessage.value = '无法确认当前登录身份'
    loading.value = false
    return
  }

  if (!authStore.isAuthenticated) {
    await router.replace({
      name: 'login',
      query: { redirect: '/admin' },
    })
    return
  }

  if (!isAdmin.value) {
    errorMessage.value = '当前账户没有管理员权限'
    loading.value = false
    return
  }

  await loadData(true)
})
</script>

<template>
  <main class="admin-page">
    <div class="container">
      <header class="topbar">
        <RouterLink class="brand" to="/">
          <span class="brand-mark">C</span>
          CampusTrade
        </RouterLink>

        <nav class="top-nav">
          <RouterLink to="/">返回首页</RouterLink>
          <RouterLink to="/products">商品广场</RouterLink>
        </nav>
      </header>

      <section class="page-heading">
        <div>
          <p class="eyebrow">ADMINISTRATION</p>
          <h1>管理后台</h1>
          <p>管理平台用户和商品，处理违规内容。</p>
        </div>

        <button
          v-if="isAdmin"
          type="button"
          class="refresh-button"
          :disabled="loading || refreshing || !!busyAction"
          @click="loadData()"
        >
          {{ refreshing ? '刷新中...' : '刷新数据' }}
        </button>
      </section>

      <p
        v-if="errorMessage"
        class="notice error"
        role="alert"
      >
        {{ errorMessage }}
      </p>

      <p
        v-if="successMessage"
        class="notice success"
        role="status"
      >
        {{ successMessage }}
      </p>

      <div v-if="loading" class="empty-state">
        正在加载管理员数据...
      </div>

      <div v-else-if="!isAdmin" class="empty-state">
        <h2>无法访问管理后台</h2>
        <p>当前账户没有管理员权限。</p>
        <RouterLink to="/">返回首页</RouterLink>
      </div>

      <template v-else>
        <section class="stats">
          <div class="stat-card">
            <span>用户总数</span>
            <strong>{{ users.length }}</strong>
          </div>

          <div class="stat-card">
            <span>正常账户</span>
            <strong>{{ activeUsers }}</strong>
          </div>

          <div class="stat-card">
            <span>已发布商品</span>
            <strong>{{ publishedProducts }}</strong>
          </div>

          <div class="stat-card">
            <span>强制或主动下架商品</span>
            <strong>{{ offShelfProducts }}</strong>
          </div>
        </section>

        <section class="panel">
          <div class="tabs">
            <button
              type="button"
              :class="{ selected: activeTab === 'users' }"
              @click="activeTab = 'users'"
            >
              用户管理
            </button>

            <button
              type="button"
              :class="{ selected: activeTab === 'products' }"
              @click="activeTab = 'products'"
            >
              商品管理
            </button>
          </div>

          <div v-if="activeTab === 'users'" class="panel-body">
            <div class="section-title">
              <div>
                <h2>全站用户</h2>
                <p>查看账户状态，封禁或解封普通用户。</p>
              </div>
              <span>{{ users.length }} 位用户</span>
            </div>

            <div v-if="users.length === 0" class="empty-state">
              暂无用户数据
            </div>

            <div v-else class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>用户</th>
                    <th>邮箱</th>
                    <th>角色</th>
                    <th>状态</th>
                    <th>注册时间</th>
                    <th>操作</th>
                  </tr>
                </thead>

                <tbody>
                  <tr v-for="user in users" :key="user.id">
                    <td>#{{ user.id }}</td>

                    <td>
                      <strong>{{ user.nickname }}</strong>
                    </td>

                    <td class="email">
                      {{ user.email || '未绑定' }}
                    </td>

                    <td>
                      <span class="role">
                        {{ user.role }}
                      </span>
                    </td>

                    <td>
                      <span
                        class="status"
                        :class="user.status.toLowerCase()"
                      >
                        {{ statusLabel(user.status) }}
                      </span>
                    </td>

                    <td class="muted">
                      {{ formatTime(user.createdAt) }}
                    </td>

                    <td>
                      <button
                        v-if="
                          user.role === 'USER' &&
                          user.status === 'ACTIVE'
                        "
                        type="button"
                        class="action danger-action"
                        :disabled="!!busyAction"
                        @click="handleBan(user)"
                      >
                        {{
                          busyAction === `ban-${user.id}`
                            ? '处理中...'
                            : '封禁'
                        }}
                      </button>

                      <button
                        v-else-if="
                          user.role === 'USER' &&
                          user.status === 'BANNED'
                        "
                        type="button"
                        class="action"
                        :disabled="!!busyAction"
                        @click="handleUnban(user)"
                      >
                        {{
                          busyAction === `unban-${user.id}`
                            ? '处理中...'
                            : '解封'
                        }}
                      </button>

                      <span v-else class="muted">
                        —
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div v-else class="panel-body">
            <div class="section-title">
              <div>
                <h2>全站商品</h2>
                <p>查看商品状态，强制下架已发布商品。</p>
              </div>
              <span>{{ products.length }} 件商品</span>
            </div>

            <div v-if="products.length === 0" class="empty-state">
              暂无商品数据
            </div>

            <div v-else class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>商品名称</th>
                    <th>卖家 ID</th>
                    <th>分类</th>
                    <th>价格</th>
                    <th>状态</th>
                    <th>创建时间</th>
                    <th>操作</th>
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="product in products"
                    :key="product.id"
                  >
                    <td>#{{ product.id }}</td>

                    <td class="product-title">
                      <strong>{{ product.title }}</strong>
                    </td>

                    <td>#{{ product.sellerId }}</td>
                    <td>{{ product.category }}</td>
                    <td class="price">
                      {{ formatPrice(product.price) }}
                    </td>

                    <td>
                      <span
                        class="status"
                        :class="product.status.toLowerCase()"
                      >
                        {{ statusLabel(product.status) }}
                      </span>
                    </td>

                    <td class="muted">
                      {{ formatTime(product.createdAt) }}
                    </td>

                    <td>
                      <button
                        v-if="product.status === 'PUBLISHED'"
                        type="button"
                        class="action danger-action"
                        :disabled="!!busyAction"
                        @click="handleOffShelf(product)"
                      >
                        {{
                          busyAction === `off-${product.id}`
                            ? '处理中...'
                            : '强制下架'
                        }}
                      </button>

                      <span v-else class="muted">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: #f7f8fc;
  color: #202536;
}

.container {
  width: min(1240px, 100%);
  margin: 0 auto;
  padding: 0 24px 70px;
  box-sizing: border-box;
}

.topbar {
  height: 82px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5eaf3;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #202536;
  font-size: 21px;
  font-weight: 750;
  text-decoration: none;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: #4169e1;
  color: white;
}

.top-nav {
  display: flex;
  gap: 22px;
}

.top-nav a {
  color: #64748b;
  font-size: 14px;
  text-decoration: none;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 44px 0 30px;
}

.eyebrow {
  color: #4169e1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.page-heading h1 {
  margin: 12px 0;
  font-size: 34px;
}

.page-heading p:last-child {
  color: #788196;
  font-size: 14px;
}

.refresh-button,
.action {
  padding: 10px 15px;
  border: 1px solid #d5def0;
  border-radius: 8px;
  background: white;
  color: #4169e1;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.action.danger-action {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.notice {
  padding: 14px 18px;
  margin: 0 0 20px;
  border-radius: 10px;
  font-size: 14px;
}

.notice.error {
  background: #fef2f2;
  color: #b91c1c;
}

.notice.success {
  background: #ecfdf3;
  color: #16803c;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 26px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 24px;
  border: 1px solid #e5eaf3;
  border-radius: 14px;
  background: white;
}

.stat-card span {
  color: #788196;
  font-size: 13px;
}

.stat-card strong {
  font-size: 28px;
  color: #202536;
}

.panel {
  overflow: hidden;
  border: 1px solid #e5eaf3;
  border-radius: 16px;
  background: white;
}

.tabs {
  display: flex;
  gap: 20px;
  padding: 0 26px;
  border-bottom: 1px solid #edf0f5;
}

.tabs button {
  padding: 19px 4px;
  border: 0;
  border-bottom: 3px solid transparent;
  background: none;
  color: #64748b;
  font-size: 14px;
  cursor: pointer;
}

.tabs button.selected {
  border-bottom-color: #4169e1;
  color: #4169e1;
  font-weight: 650;
}

.panel-body {
  padding: 26px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
}

.section-title h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.section-title p,
.section-title > span {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
}

.table-scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  min-width: 850px;
  border-collapse: collapse;
  text-align: left;
}

th,
td {
  padding: 17px 13px;
  border-bottom: 1px solid #edf0f5;
  font-size: 13px;
  vertical-align: middle;
}

th {
  color: #788196;
  font-weight: 600;
  background: #f9fbfe;
  white-space: nowrap;
}

td {
  color: #475569;
}

td.email,
td.product-title {
  max-width: 240px;
  overflow-wrap: anywhere;
}

.role {
  font-weight: 600;
  color: #475569;
}

.status {
  display: inline-flex;
  padding: 5px 9px;
  border-radius: 20px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.status.active,
.status.published {
  background: #ecfdf3;
  color: #16803c;
}

.status.banned,
.status.off_shelf {
  background: #fff1f2;
  color: #b91c1c;
}

.status.draft,
.status.pending_verification {
  background: #fff7ed;
  color: #9a3412;
}

.price {
  color: #4169e1;
  font-weight: 650;
  white-space: nowrap;
}

.muted {
  color: #94a3b8;
}

.empty-state {
  padding: 65px 20px;
  text-align: center;
  color: #64748b;
  font-size: 14px;
}

.empty-state a {
  color: #4169e1;
}

@media (max-width: 850px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 550px) {
  .container {
    padding: 0 14px 40px;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .stats {
    gap: 10px;
  }

  .stat-card {
    padding: 17px;
  }

  .panel-body {
    padding: 14px;
  }
}
</style>
