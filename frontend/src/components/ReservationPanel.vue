
<script setup lang="ts">
import {
  computed,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from 'vue'

import { useRoute } from 'vue-router'
import axios from 'axios'

import { useAuthStore } from '../stores/auth'

import {
  getReservation,
  createReservation,
  cancelReservation,
} from '../api/reservations'

import type {
  ReservationResponse,
  ReservationStatus,
} from '../api/reservations'

const props = defineProps<{
  productId: number
  sellerId: number
}>()

const route = useRoute()
const authStore = useAuthStore()

const reservation =
  ref<ReservationResponse | null>(null)

const loading = ref(false)
const submitting = ref(false)

const errorMessage = ref('')
const successMessage = ref('')

const currentTime = ref(Date.now())

// 服务器时间与浏览器时间的偏移量。
let serverOffset = 0

let timer: number | undefined

let requestVersion = 0
let lastRefreshAt = 0

const isSeller = computed(
  () => authStore.user?.id === props.sellerId,
)

const statusLabels: Record<
  ReservationStatus,
  string
> = {
  ACTIVE: '预约成功，等待后续安排',
  INVITED: '已获得交易邀请',
  ACCEPTED: '已接受交易邀请',
  DECLINED: '已拒绝交易邀请',
  EXPIRED: '交易邀请已过期',
  CANCELLED: '已取消预约',
}

// 后端预约时间统一使用北京时间。
// LocalDateTime 本身不包含时区，因此显式补充 +08:00。
function parseServerTime(
  value: string | null,
): number | null {
  if (!value) {
    return null
  }

  const hasTimezone =
    /(?:Z|[+-]\d{2}:\d{2})$/.test(value)

  const timestamp = Date.parse(
    hasTimezone ? value : `${value}+08:00`,
  )

  return Number.isFinite(timestamp)
    ? timestamp
    : null
}

function applyResponse(
  result: ReservationResponse,
) {
  reservation.value = result

  const timestamp =
    parseServerTime(result.serverTime)

  if (timestamp !== null) {
    serverOffset = timestamp - Date.now()
  }

  currentTime.value = Date.now() + serverOffset
  lastRefreshAt = Date.now()
}

function formatCountdown(
  deadline: string | null,
): string {
  const timestamp = parseServerTime(deadline)

  if (timestamp === null) {
    return '--:--'
  }

  const seconds = Math.max(
    0,
    Math.ceil(
      (timestamp - currentTime.value) / 1000,
    ),
  )

  const hours = Math.floor(seconds / 3600)

  const minutes =
    Math.floor((seconds % 3600) / 60)

  const remainingSeconds = seconds % 60

  const pad = (value: number) =>
    String(value).padStart(2, '0')

  if (hours > 0) {
    return (
      `${pad(hours)}:` +
      `${pad(minutes)}:` +
      `${pad(remainingSeconds)}`
    )
  }

  return `${pad(minutes)}:${pad(remainingSeconds)}`
}

const phase = computed(() => {
  const data = reservation.value

  if (!data || !data.roundId) {
    return 'IDLE'
  }

  if (data.roundStatus === 'CANCELLED') {
    return 'CANCELLED'
  }

  if (data.roundStatus === 'COMPLETED') {
    return 'COMPLETED'
  }

  if (data.roundStatus === 'MATCHING') {
    return 'MATCHING'
  }

  const sellerDeadline = parseServerTime(
    data.sellerResponseDeadline,
  )

  if (
    sellerDeadline !== null &&
    currentTime.value >= sellerDeadline
  ) {
    return 'TIMEOUT'
  }

  const coolingDeadline = parseServerTime(
    data.coolingEndsAt,
  )

  if (
    data.roundStatus === 'FROZEN' ||
    (
      coolingDeadline !== null &&
      currentTime.value >= coolingDeadline
    )
  ) {
    return 'FROZEN'
  }

  const priorityDeadline = parseServerTime(
    data.priorityEndsAt,
  )

  if (
    priorityDeadline !== null &&
    currentTime.value < priorityDeadline
  ) {
    return 'PRIORITY'
  }

  return 'COOLING'
})

const phaseLabel = computed(() => {
  switch (phase.value) {
    case 'IDLE':
      return '等待首次预约'

    case 'PRIORITY':
      return '首位买家优先期'

    case 'COOLING':
      return '预约冷静期'

    case 'FROZEN':
      return '预约已截止，等待卖家响应'

    case 'MATCHING':
      return '交易匹配中'

    case 'COMPLETED':
      return '预约轮次已完成'

    case 'CANCELLED':
      return '预约轮次已结束'

    case 'TIMEOUT':
      return '卖家响应期限已结束'

    default:
      return '预约状态待确认'
  }
})

const countdownLabel = computed(() => {
  switch (phase.value) {
    case 'PRIORITY':
      return '首位买家优先期剩余'

    case 'COOLING':
      return '距离预约截止'

    case 'FROZEN':
      return '卖家响应剩余'

    default:
      return ''
  }
})

const countdown = computed(() => {
  const data = reservation.value

  if (!data) {
    return ''
  }

  switch (phase.value) {
    case 'PRIORITY':
      return formatCountdown(
        data.priorityEndsAt,
      )

    case 'COOLING':
      return formatCountdown(
        data.coolingEndsAt,
      )

    case 'FROZEN':
      return formatCountdown(
        data.sellerResponseDeadline,
      )

    default:
      return ''
  }
})

const canReserve = computed(() => {
  return (
    !isSeller.value &&
    reservation.value?.canReserve === true &&
    (
      phase.value === 'IDLE' ||
      phase.value === 'PRIORITY' ||
      phase.value === 'COOLING'
    )
  )
})

const canCancel = computed(() => {
  return (
    !isSeller.value &&
    reservation.value?.canCancel === true &&
    (
      phase.value === 'PRIORITY' ||
      phase.value === 'COOLING' ||
      phase.value === 'FROZEN'
    )
  )
})

const reservationStatusLabel = computed(() => {
  const status =
    reservation.value?.reservationStatus

  return status ? statusLabels[status] : ''
})

function handleError(
  error: unknown,
  fallback: string,
) {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 401) {
      authStore.clearUser()
      return
    }

    if (error.response?.status === 404) {
      errorMessage.value =
        '商品已不可预约，请返回商品列表刷新'
      return
    }

    const message =
      error.response?.data?.message

    if (
      typeof message === 'string' &&
      message.length > 0
    ) {
      errorMessage.value = message
      return
    }
  }

  errorMessage.value = fallback
}

async function refreshReservation() {
  if (
    !authStore.isAuthenticated ||
    loading.value ||
    submitting.value
  ) {
    return
  }

  const version = ++requestVersion

  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getReservation(
      props.productId,
    )

    if (version !== requestVersion) {
      return
    }

    applyResponse(result)
  } catch (error) {
    if (version !== requestVersion) {
      return
    }

    reservation.value = null

    handleError(
      error,
      '预约信息加载失败，请稍后重试',
    )
  } finally {
    if (version === requestVersion) {
      loading.value = false
      lastRefreshAt = Date.now()
    }
  }
}

async function handleReserve() {
  if (
    submitting.value ||
    loading.value ||
    !canReserve.value
  ) {
    return
  }

  const confirmed = window.confirm(
    '确认预约该商品？请及时关注后续预约状态。',
  )

  if (!confirmed) {
    return
  }

  const version = ++requestVersion

  submitting.value = true
  successMessage.value = ''
  errorMessage.value = ''

  try {
    const result = await createReservation(
      props.productId,
    )

    if (version !== requestVersion) {
      return
    }

    applyResponse(result)

    successMessage.value =
      '预约成功，请关注后续交易安排。'
  } catch (error) {
    if (version !== requestVersion) {
      return
    }

    reservation.value = null

    handleError(
      error,
      '预约失败，请刷新预约状态后重试',
    )
  } finally {
    if (version === requestVersion) {
      submitting.value = false
    }
  }
}

async function handleCancel() {
  if (
    submitting.value ||
    loading.value ||
    !canCancel.value
  ) {
    return
  }

  const confirmed = window.confirm(
    '取消后不能重新加入本轮预约，' +
    '并且10分钟内不能再次预约该商品。' +
    '确认取消吗？',
  )

  if (!confirmed) {
    return
  }

  const version = ++requestVersion

  submitting.value = true
  successMessage.value = ''
  errorMessage.value = ''

  try {
    const result = await cancelReservation(
      props.productId,
    )

    if (version !== requestVersion) {
      return
    }

    applyResponse(result)

    successMessage.value = '预约已取消。'
  } catch (error) {
    if (version !== requestVersion) {
      return
    }

    reservation.value = null

    handleError(
      error,
      '取消预约失败，请刷新状态后重试',
    )
  } finally {
    if (version === requestVersion) {
      submitting.value = false
    }
  }
}

// 登录身份改变时，清除上一个用户的预约信息。
watch(
  () => authStore.user?.id,
  () => {
    requestVersion++

    reservation.value = null
    loading.value = false
    submitting.value = false

    errorMessage.value = ''
    successMessage.value = ''

    if (authStore.isAuthenticated) {
      void refreshReservation()
    }
  },
  { immediate: true },
)

onMounted(async () => {
  timer = window.setInterval(() => {
    currentTime.value =
      Date.now() + serverOffset

    // 每15秒同步一次真实预约状态。
    if (
      authStore.isAuthenticated &&
      !loading.value &&
      !submitting.value &&
      Date.now() - lastRefreshAt >= 15000
    ) {
      void refreshReservation()
    }
  }, 1000)

  // 商品详情属于公开页面，路由守卫不会
  // 主动初始化身份，因此需要补充这一步。
  if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      errorMessage.value =
        '登录状态查询失败，请重新加载页面'
    }
  }
})

onUnmounted(() => {
  requestVersion++

  if (timer !== undefined) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <section class="reservation-panel">
    <div class="panel-heading">
      <h2>商品预约</h2>

      <button
        v-if="authStore.isAuthenticated"
        type="button"
        class="refresh-button"
        :disabled="loading || submitting"
        @click="refreshReservation"
      >
        {{ loading ? '刷新中...' : '刷新状态' }}
      </button>
    </div>

    <p
      v-if="errorMessage"
      class="message error-message"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <div
      v-if="!authStore.initialized"
      class="notice"
    >
      正在确认登录状态...
    </div>

    <div
      v-else-if="!authStore.isAuthenticated"
      class="guest-panel"
    >
      <p>登录后即可查看预约状态并参与预约。</p>

      <RouterLink
        :to="{
          name: 'login',
          query: {
            redirect: route.fullPath,
          },
        }"
        class="primary-button"
      >
        登录后预约
      </RouterLink>
    </div>

    <p
      v-else-if="loading && !reservation"
      class="notice"
    >
      正在加载预约信息...
    </p>

    <template v-else-if="reservation">
      <div class="round-status">
        <span class="phase-label">
          {{ phaseLabel }}
        </span>

        <span class="buyer-count">
          当前 {{ reservation.activeReservationCount }}
          人预约
        </span>
      </div>

      <div
        v-if="countdownLabel"
        class="countdown-panel"
      >
        <span>{{ countdownLabel }}</span>

        <strong>{{ countdown }}</strong>
      </div>

      <div
        v-if="reservationStatusLabel"
        class="personal-status"
      >
        <span>我的预约</span>

        <strong>
          {{ reservationStatusLabel }}
        </strong>
      </div>

      <p
        v-if="successMessage"
        class="message success-message"
        role="status"
      >
        {{ successMessage }}
      </p>

      <template v-if="isSeller">
        <div class="notice">
          这是你发布的商品，你可以查看预约人数，
          但不能参与自己的商品预约。
        </div>
      </template>

      <template v-else>
        <button
          v-if="canReserve"
          type="button"
          class="primary-button"
          :disabled="loading || submitting"
          @click="handleReserve"
        >
          {{ submitting ? '提交中...' : '立即预约' }}
        </button>

        <button
          v-else-if="canCancel"
          type="button"
          class="cancel-button"
          :disabled="loading || submitting"
          @click="handleCancel"
        >
          {{ submitting ? '处理中...' : '取消预约' }}
        </button>

        <div v-else class="notice">
          当前暂不可发起预约或取消操作。
        </div>
      </template>
    </template>

    <div class="rules">
      <h3>预约规则</h3>

      <ul>
        <li>首位买家预约后启动30分钟预约期。</li>

        <li>
          前1分钟为首位买家优先期，
          其他买家仍可参与预约。
        </li>

        <li>
          后续预约不会延长已有预约期限。
        </li>

        <li>
          每位买家最多同时参与5件商品预约。
        </li>

        <li>
          取消后不能重新加入同一轮预约。
        </li>
      </ul>
    </div>

    <p class="development-note">
      当前为基础预约版本，动态抽签及卖家交易邀请
      功能将在后续版本开放。
    </p>

    <p class="privacy-note">
      卖家联系方式将在正式建立订单后开放。
    </p>
  </section>
</template>

<style scoped>
.reservation-panel {
  display: flex;
  flex-direction: column;
  gap: 15px;
  padding: 20px;
  border: 1px solid #e0e8fb;
  border-radius: 12px;
  background: #f8faff;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.panel-heading h2 {
  margin: 0;
  color: #202536;
  font-size: 18px;
}

.refresh-button {
  padding: 6px 0;
  border: 0;
  background: transparent;
  color: #4169e1;
  font-size: 12px;
  cursor: pointer;
}

.round-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.phase-label {
  padding: 6px 10px;
  border-radius: 6px;
  background: #eaf0ff;
  color: #4169e1;
  font-size: 12px;
  font-weight: 650;
}

.buyer-count {
  color: #64748b;
  font-size: 13px;
}

.countdown-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  background: white;
  color: #64748b;
  font-size: 13px;
}

.countdown-panel strong {
  color: #4169e1;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
}

.personal-status {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-radius: 8px;
  background: #ecfdf3;
  color: #16803c;
  font-size: 13px;
}

.primary-button,
.cancel-button {
  display: block;
  box-sizing: border-box;
  width: 100%;
  padding: 12px 18px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 650;
  text-align: center;
  text-decoration: none;
  cursor: pointer;
}

.primary-button {
  background: #4169e1;
  color: white;
}

.primary-button:hover {
  background: #3458c8;
}

.cancel-button {
  border-color: #f0c6c6;
  background: white;
  color: #c24141;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.guest-panel p,
.notice,
.development-note,
.privacy-note {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.8;
}

.guest-panel .primary-button {
  margin-top: 12px;
}

.notice {
  padding: 10px 12px;
  border-radius: 8px;
  background: #eef2f9;
}

.rules {
  padding-top: 14px;
  border-top: 1px solid #e5eaf3;
}

.rules h3 {
  margin: 0 0 8px;
  color: #475569;
  font-size: 13px;
}

.rules ul {
  margin: 0;
  padding-left: 18px;
  color: #788196;
  font-size: 12px;
  line-height: 1.9;
}

.message {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
}

.error-message {
  background: #fef2f2;
  color: #b91c1c;
}

.success-message {
  background: #ecfdf3;
  color: #16803c;
}

.development-note {
  font-size: 12px;
}

.privacy-note {
  padding-top: 10px;
  border-top: 1px solid #e5eaf3;
  font-size: 12px;
}
</style>
