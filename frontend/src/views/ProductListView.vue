
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'

import {
  getProductImages,
  getProducts,
} from '../api/products'

import type { Product } from '../api/products'

const products = ref<Product[]>([])
const coverImages = ref<Record<number, string>>({})

const loading = ref(true)
const errorMessage = ref('')

function formatPrice(price: number | string): string {
  const value = Number(price)

  return Number.isFinite(value)
    ? `¥ ${value.toFixed(2)}`
    : '价格待确认'
}

function formatDate(date: string | null): string {
  if (!date) return '暂无时间信息'

  const parsed = new Date(date)

  if (Number.isNaN(parsed.getTime())) {
    return '暂无时间信息'
  }

  return parsed.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

async function loadCoverImage(productId: number) {
  try {
    const images = await getProductImages(productId)

    const firstImage = [...images].sort(
      (a, b) => a.displayOrder - b.displayOrder,
    )[0]

    if (firstImage) {
      coverImages.value[productId] = firstImage.url
    }
  } catch (error) {
    // 单张图片查询失败时不影响整个商品列表。
    console.warn(
      `商品 ${productId} 的图片加载失败`,
      error,
    )
  }
}

async function loadProducts() {
  loading.value = true
  errorMessage.value = ''
  coverImages.value = {}

  try {
    const result = await getProducts()
    products.value = result

    await Promise.all(
      result.map((product) =>
        loadCoverImage(product.id),
      ),
    )
  } catch (error) {
    if (axios.isAxiosError(error)) {
      errorMessage.value =
        error.response?.data?.message ||
        '商品加载失败，请稍后重试'
    } else {
      errorMessage.value = '商品加载失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadProducts()
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

        <div class="header-actions">
          <RouterLink to="/products/new">
            发布商品
          </RouterLink>

          <RouterLink to="/">
            返回首页
          </RouterLink>
        </div>
      </header>

      <section class="intro">
        <p class="eyebrow">EXPLORE CAMPUSTRADE</p>
        <h1>发现校园好物</h1>
        <p>浏览校园里的闲置商品，让好物找到新的主人。</p>
      </section>

      <section class="products-section">
        <div class="section-heading">
          <div>
            <h2>商品广场</h2>
            <p>发现正在出售的校园闲置物品</p>
          </div>

          <span
            v-if="!loading && !errorMessage"
            class="count"
          >
            共 {{ products.length }} 件商品
          </span>
        </div>

        <div
          v-if="loading"
          class="state-panel"
          role="status"
        >
          <div class="spinner"></div>
          <h3>正在加载商品</h3>
          <p>正在获取商品信息...</p>
        </div>

        <div
          v-else-if="errorMessage"
          class="state-panel"
          role="alert"
        >
          <h3>商品加载失败</h3>
          <p>{{ errorMessage }}</p>
          <button
            type="button"
            class="retry"
            @click="loadProducts"
          >
            重新加载
          </button>
        </div>

        <div
          v-else-if="products.length === 0"
          class="state-panel"
        >
          <h3>暂时没有在售商品</h3>
          <p>目前还没有已发布的商品。</p>
        </div>

        <div v-else class="product-grid">
          <RouterLink
            v-for="product in products"
            :key="product.id"
            :to="{
              name: 'product-detail',
              params: { id: product.id },
            }"
            class="product-card"
          >
            <div class="product-visual">
              <img
                v-if="coverImages[product.id]"
                :src="coverImages[product.id]"
                :alt="product.title"
                class="cover-image"
                loading="lazy"
              />

              <div
                v-else
                class="image-placeholder"
              >
                CT
              </div>

              <span class="status-badge">
                在售
              </span>
            </div>

            <div class="product-info">
              <div class="meta">
                <span>{{ product.category || '未分类' }}</span>

                <span>
                  {{ product.conditionLevel || '成色未填写' }}
                </span>
              </div>

              <h3>{{ product.title }}</h3>

              <p class="description">
                {{
                  product.description ||
                  '卖家暂未填写商品描述'
                }}
              </p>

              <div class="product-bottom">
                <span class="price">
                  {{ formatPrice(product.price) }}
                </span>

                <span class="date">
                  {{ formatDate(product.createdAt) }}
                </span>
              </div>
            </div>
          </RouterLink>
        </div>
      </section>
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
  width: 37px;
  height: 37px;
  border-radius: 10px;
  background: #4169e1;
  color: white;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.header-actions a {
  color: #4169e1;
  font-size: 14px;
  text-decoration: none;
}

.intro {
  padding: 70px 0 64px;
}

.eyebrow {
  color: #4169e1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.intro h1 {
  margin: 16px 0;
  font-size: clamp(30px, 4vw, 44px);
}

.intro > p:last-child {
  color: #788196;
  font-size: 15px;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 20px;
  margin-bottom: 28px;
}

.section-heading h2 {
  margin: 0 0 10px;
  font-size: 25px;
}

.section-heading p,
.count {
  color: #788196;
  font-size: 14px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.product-card {
  overflow: hidden;
  border: 1px solid #e5eaf3;
  border-radius: 16px;
  background: white;
  color: inherit;
  text-decoration: none;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(30, 48, 90, 0.08);
}

.product-visual {
  position: relative;
  height: 200px;
  overflow: hidden;
  background: linear-gradient(135deg, #edf2ff, #f6f8ff);
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: #4169e1;
  font-size: 32px;
  font-weight: 750;
}

.status-badge {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 6px 12px;
  border-radius: 20px;
  background: #ecfdf3;
  color: #16803c;
  font-size: 12px;
  font-weight: 650;
}

.product-info {
  padding: 22px;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta span {
  padding: 5px 9px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}

.product-info h3 {
  margin: 18px 0 12px;
  font-size: 18px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.description {
  min-height: 44px;
  margin: 0;
  color: #788196;
  font-size: 13px;
  line-height: 1.7;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.product-bottom {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #edf0f5;
}

.price {
  color: #4169e1;
  font-size: 23px;
  font-weight: 750;
}

.date {
  color: #94a3b8;
  font-size: 12px;
}

.state-panel {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px;
  border: 1px solid #e5eaf3;
  border-radius: 16px;
  background: white;
  text-align: center;
}

.state-panel p {
  color: #788196;
}

.spinner {
  width: 34px;
  height: 34px;
  border: 3px solid #e5eaf3;
  border-top-color: #4169e1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.retry {
  margin-top: 16px;
  padding: 11px 22px;
  border: 0;
  border-radius: 8px;
  background: #4169e1;
  color: white;
  cursor: pointer;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 580px) {
  .container {
    padding: 0 18px 50px;
  }

  .intro {
    padding: 48px 0;
  }

  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
