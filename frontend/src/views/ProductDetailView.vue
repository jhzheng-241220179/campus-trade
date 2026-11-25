
<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'

import {
  getProductById,
  getProductImages,
} from '../api/products'

import type {
  Product,
  ProductImage,
} from '../api/products'

import ReservationPanel
  from '../components/ReservationPanel.vue'

const route = useRoute()

const product = ref<Product | null>(null)
const images = ref<ProductImage[]>([])
const activeImageId = ref<number | null>(null)

const loading = ref(true)
const errorMessage = ref('')

let requestVersion = 0

function formatPrice(
  price: number | string,
): string {
  const value = Number(price)

  return Number.isFinite(value)
    ? `¥ ${value.toFixed(2)}`
    : '价格待确认'
}

function formatDate(
  date: string | null,
): string {
  if (!date) {
    return '暂无时间信息'
  }

  const parsed = new Date(date)

  if (Number.isNaN(parsed.getTime())) {
    return '暂无时间信息'
  }

  return parsed.toLocaleString('zh-CN')
}

async function loadProduct() {
  const currentRequest = ++requestVersion

  product.value = null
  images.value = []
  activeImageId.value = null

  loading.value = true
  errorMessage.value = ''

  const rawId = route.params.id

  if (
    typeof rawId !== 'string' ||
    !/^[1-9]\d*$/.test(rawId)
  ) {
    errorMessage.value = '商品 ID 不合法'
    loading.value = false
    return
  }

  const id = Number(rawId)

  if (!Number.isSafeInteger(id)) {
    errorMessage.value = '商品 ID 不合法'
    loading.value = false
    return
  }

  try {
    const result = await getProductById(id)

    if (currentRequest !== requestVersion) {
      return
    }

    product.value = result

    try {
      const imageResult =
        await getProductImages(id)

      if (currentRequest !== requestVersion) {
        return
      }

      images.value = [...imageResult].sort(
        (a, b) =>
          a.displayOrder - b.displayOrder,
      )

      activeImageId.value =
        images.value[0]?.id ?? null
    } catch (imageError) {
      console.warn(
        '商品图片加载失败',
        imageError,
      )
    }
  } catch (error) {
    if (currentRequest !== requestVersion) {
      return
    }

    if (
      axios.isAxiosError(error) &&
      error.response?.status === 404
    ) {
      errorMessage.value =
        '商品不存在或尚未发布'
    } else if (axios.isAxiosError(error)) {
      errorMessage.value =
        error.response?.data?.message ||
        '商品加载失败，请稍后重试'
    } else {
      errorMessage.value =
        '商品加载失败，请稍后重试'
    }
  } finally {
    if (currentRequest === requestVersion) {
      loading.value = false
    }
  }
}

watch(
  () => route.params.id,
  () => {
    void loadProduct()
  },
  { immediate: true },
)

onUnmounted(() => {
  requestVersion++
})
</script>

<template>
  <main class="page">
    <div class="container">
      <header class="header">
        <RouterLink to="/" class="brand">
          <span class="brand-icon">C</span>

          <span>CampusTrade</span>
        </RouterLink>

        <RouterLink
          to="/products"
          class="back-link"
        >
          返回商品列表
        </RouterLink>
      </header>

      <div
        v-if="loading"
        class="state-panel"
        role="status"
      >
        <h2>正在加载商品详情...</h2>
      </div>

      <div
        v-else-if="errorMessage"
        class="state-panel"
        role="alert"
      >
        <h2>无法查看商品</h2>

        <p>{{ errorMessage }}</p>

        <button
          type="button"
          class="retry"
          @click="loadProduct"
        >
          重新加载
        </button>

        <RouterLink to="/products">
          返回商品列表
        </RouterLink>
      </div>

      <template v-else-if="product">
        <nav class="breadcrumb">
          <RouterLink to="/">首页</RouterLink>

          <span>/</span>

          <RouterLink to="/products">
            商品广场
          </RouterLink>

          <span>/</span>

          <span>{{ product.title }}</span>
        </nav>

        <div class="detail-layout">
          <section class="visual-card">
            <div class="main-image">
              <img
                v-if="
                  images.some(
                    (image) =>
                      image.id === activeImageId
                  )
                "
                :src="
                  images.find(
                    (image) =>
                      image.id === activeImageId
                  )?.url
                "
                :alt="product.title"
              />

              <div v-else class="placeholder">
                <span>CT</span>

                <p>暂无商品图片</p>
              </div>
            </div>

            <div
              v-if="images.length > 1"
              class="thumbnail-list"
            >
              <button
                v-for="image in images"
                :key="image.id"
                type="button"
                class="thumbnail"
                :class="{
                  active: activeImageId === image.id,
                }"
                :aria-label="
                  `查看第${image.displayOrder + 1}张图片`
                "
                @click="activeImageId = image.id"
              >
                <img
                  :src="image.url"
                  :alt="`${product.title}缩略图`"
                />
              </button>
            </div>
          </section>

          <section class="information-card">
            <span class="status-badge">
              在售商品
            </span>

            <h1>{{ product.title }}</h1>

            <p class="price">
              {{ formatPrice(product.price) }}
            </p>

            <div class="information-grid">
              <div>
                <span>商品分类</span>

                <strong>
                  {{ product.category || '未分类' }}
                </strong>
              </div>

              <div>
                <span>商品成色</span>

                <strong>
                  {{
                    product.conditionLevel ||
                    '未填写'
                  }}
                </strong>
              </div>

              <div>
                <span>发布时间</span>

                <strong>
                  {{ formatDate(product.createdAt) }}
                </strong>
              </div>

              <div>
                <span>商品编号</span>

                <strong>#{{ product.id }}</strong>
              </div>
            </div>

            <ReservationPanel
              :key="product.id"
              :product-id="product.id"
              :seller-id="product.sellerId"
            />
          </section>
        </div>

        <section class="description-card">
          <h2>商品详情</h2>

          <p class="description">
            {{
              product.description ||
              '卖家暂未填写商品描述。'
            }}
          </p>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fc;
  color: #202536;
}

.container {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 0 28px 80px;
  box-sizing: border-box;
}

.header {
  min-height: 84px;
  display: flex;
  justify-content: space-between;
  align-items: center;
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
  width: 37px;
  height: 37px;
  border-radius: 10px;
  background: #4169e1;
  color: white;
}

.back-link,
.breadcrumb a {
  color: #4169e1;
  text-decoration: none;
  font-size: 14px;
}

.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 36px 0 28px;
  color: #788196;
  font-size: 13px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  align-items: start;
  gap: 34px;
}

.visual-card,
.information-card,
.description-card {
  overflow: hidden;
  border: 1px solid #e5eaf3;
  border-radius: 18px;
  background: white;
}

.main-image {
  height: 430px;
  background: linear-gradient(
    135deg,
    #edf2ff,
    #f6f8ff
  );
}

.main-image > img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.placeholder span {
  display: grid;
  place-items: center;
  width: 110px;
  height: 110px;
  border-radius: 28px;
  background: #e0e8ff;
  color: #4169e1;
  font-size: 36px;
  font-weight: 750;
}

.placeholder p {
  margin-top: 24px;
  color: #94a3b8;
  font-size: 13px;
}

.thumbnail-list {
  display: flex;
  gap: 12px;
  padding: 18px;
  overflow-x: auto;
}

.thumbnail {
  flex: 0 0 76px;
  width: 76px;
  height: 76px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 9px;
  background: #f1f5ff;
  cursor: pointer;
  overflow: hidden;
}

.thumbnail.active {
  border-color: #4169e1;
}

.thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.information-card {
  padding: 38px;
}

.status-badge {
  display: inline-block;
  padding: 7px 14px;
  border-radius: 20px;
  background: #ecfdf3;
  color: #16803c;
  font-size: 12px;
  font-weight: 650;
}

.information-card h1 {
  margin: 24px 0 18px;
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.price {
  margin: 0 0 34px;
  color: #4169e1;
  font-size: 34px;
  font-weight: 750;
}

.information-grid {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(0, 1fr));
  gap: 26px 20px;
  padding: 28px 0;
  border-top: 1px solid #edf0f5;
}

.information-grid div {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.information-grid span {
  color: #94a3b8;
  font-size: 12px;
}

.information-grid strong {
  color: #334155;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.description-card {
  margin-top: 32px;
  padding: 36px;
}

.description-card h2 {
  margin: 0 0 24px;
  font-size: 22px;
}

.description {
  margin: 0;
  color: #475569;
  font-size: 15px;
  line-height: 2;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.state-panel {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 40px;
  padding: 30px;
  border: 1px solid #e5eaf3;
  border-radius: 18px;
  background: white;
  text-align: center;
}

.state-panel h2 {
  margin: 0;
}

.state-panel p {
  color: #788196;
}

.state-panel button {
  padding: 11px 22px;
  border: 0;
  border-radius: 8px;
  background: #4169e1;
  color: white;
  cursor: pointer;
}

@media (max-width: 800px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .main-image {
    height: 320px;
  }
}

@media (max-width: 580px) {
  .container {
    padding: 0 18px 50px;
  }

  .information-card,
  .description-card {
    padding: 24px;
  }
}
</style>
