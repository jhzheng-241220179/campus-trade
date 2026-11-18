
<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'

import {
  deleteProductImage,
  getMyProducts,
  getProductImages,
  publishProduct,
  uploadProductImage,
} from '../api/products'

import type {
  Product,
  ProductImage,
} from '../api/products'

const route = useRoute()

const products = ref<Product[]>([])
const imagesByProduct =
  ref<Record<number, ProductImage[]>>({})

const selectedFiles =
  ref<Record<number, File | null>>({})

const previewUrls =
  ref<Record<number, string>>({})

const imageErrors =
  ref<Record<number, string>>({})

const loading = ref(true)
const errorMessage = ref('')
const actionMessage = ref('')

const busyProductId = ref<number | null>(null)

const createdId = Number(route.query.created)

if (Number.isSafeInteger(createdId) && createdId > 0) {
  actionMessage.value =
    `商品 #${createdId} 已保存为草稿。请上传图片，然后确认发布。`
}

function formatPrice(price: number | string): string {
  const value = Number(price)

  return Number.isFinite(value)
    ? `¥ ${value.toFixed(2)}`
    : '价格待确认'
}

function getErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 401) {
      return '登录已失效，请重新登录'
    }

    const message = error.response?.data?.message

    return typeof message === 'string' && message
      ? message
      : fallback
  }

  return fallback
}

function releasePreview(productId: number) {
  const existing = previewUrls.value[productId]

  if (existing) {
    URL.revokeObjectURL(existing)
  }

  delete previewUrls.value[productId]
  delete selectedFiles.value[productId]
}

function onFileSelected(
  event: Event,
  productId: number,
) {
  releasePreview(productId)
  delete imageErrors.value[productId]

  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) return

  const allowedExtensions = /\.(jpg|jpeg|png|webp)$/i

  if (!allowedExtensions.test(file.name)) {
    imageErrors.value[productId] =
      '仅支持 JPG、JPEG、PNG 和 WebP 图片'
    input.value = ''
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    imageErrors.value[productId] =
      '单张图片不能超过5MB'
    input.value = ''
    return
  }

  if ((imagesByProduct.value[productId]?.length ?? 0) >= 5) {
    imageErrors.value[productId] =
      '每件商品最多上传5张图片'
    input.value = ''
    return
  }

  selectedFiles.value[productId] = file
  previewUrls.value[productId] =
    URL.createObjectURL(file)

  // 清空选择框，使同一文件也可以重新选择。
  input.value = ''
}

async function loadProductImages(productId: number) {
  try {
    const images = await getProductImages(productId)

    imagesByProduct.value[productId] =
      [...images].sort(
        (a, b) => a.displayOrder - b.displayOrder,
      )

    delete imageErrors.value[productId]
  } catch (error) {
    imageErrors.value[productId] =
      getErrorMessage(error, '图片列表加载失败')

    imagesByProduct.value[productId] = []
  }
}

async function loadProducts() {
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getMyProducts()
    products.value = result

    await Promise.all(
      result.map((product) =>
        loadProductImages(product.id),
      ),
    )
  } catch (error) {
    errorMessage.value = getErrorMessage(
      error,
      '我的商品加载失败',
    )
  } finally {
    loading.value = false
  }
}

async function handleUpload(product: Product) {
  if (busyProductId.value !== null) return

  const file = selectedFiles.value[product.id]

  if (!file) {
    imageErrors.value[product.id] = '请先选择图片'
    return
  }

  busyProductId.value = product.id
  errorMessage.value = ''
  actionMessage.value = ''
  delete imageErrors.value[product.id]

  try {
    await uploadProductImage(product.id, file)

    releasePreview(product.id)
    await loadProductImages(product.id)

    actionMessage.value =
      `“${product.title}”的图片已上传`
  } catch (error) {
    imageErrors.value[product.id] = getErrorMessage(
      error,
      '图片上传失败',
    )
  } finally {
    busyProductId.value = null
  }
}

async function handleDeleteImage(
  product: Product,
  image: ProductImage,
) {
  if (busyProductId.value !== null) return

  if (!window.confirm('确认删除这张图片吗？')) {
    return
  }

  busyProductId.value = product.id
  actionMessage.value = ''
  errorMessage.value = ''

  try {
    await deleteProductImage(
      product.id,
      image.id,
    )

    await loadProductImages(product.id)
    actionMessage.value = '图片已删除'
  } catch (error) {
    imageErrors.value[product.id] = getErrorMessage(
      error,
      '删除图片失败',
    )
  } finally {
    busyProductId.value = null
  }
}

async function handlePublish(product: Product) {
  if (busyProductId.value !== null) return

  if (
    !window.confirm(
      `确认发布“${product.title}”吗？发布后暂不能修改图片。`,
    )
  ) {
    return
  }

  busyProductId.value = product.id
  errorMessage.value = ''
  actionMessage.value = ''

  try {
    await publishProduct(product.id)

    actionMessage.value =
      `“${product.title}”已成功发布`

    const result = await getMyProducts()
    products.value = result

    releasePreview(product.id)
  } catch (error) {
    errorMessage.value = getErrorMessage(
      error,
      '商品发布失败',
    )
  } finally {
    busyProductId.value = null
  }
}

onMounted(() => {
  void loadProducts()
})

onUnmounted(() => {
  Object.values(previewUrls.value).forEach((url) => {
    URL.revokeObjectURL(url)
  })
})
</script>

<template>
  <main class="page">
    <div class="container">
      <header class="header">
        <RouterLink to="/" class="brand">
          CampusTrade
        </RouterLink>

        <RouterLink to="/products" class="back-link">
          商品广场
        </RouterLink>
      </header>

      <section class="intro">
        <div>
          <p class="eyebrow">MY PRODUCTS</p>
          <h1>我的商品</h1>
          <p>管理商品草稿、上传图片并确认发布。</p>
        </div>

        <RouterLink to="/products/new" class="primary-link">
          + 创建商品
        </RouterLink>
      </section>

      <p
        v-if="actionMessage"
        class="message success"
        role="status"
      >
        {{ actionMessage }}
      </p>

      <p
        v-if="errorMessage"
        class="message error"
        role="alert"
      >
        {{ errorMessage }}
      </p>

      <div v-if="loading" class="state-panel">
        正在加载我的商品...
      </div>

      <div
        v-else-if="!errorMessage && products.length === 0"
        class="state-panel"
      >
        <h2>还没有商品</h2>
        <p>创建一件闲置商品，开始使用 CampusTrade。</p>
        <RouterLink to="/products/new">
          创建商品
        </RouterLink>
      </div>

      <section v-else class="product-list">
        <article
          v-for="product in products"
          :key="product.id"
          class="product-card"
        >
          <div class="product-heading">
            <div class="product-summary">
              <div class="title-row">
                <h2>{{ product.title }}</h2>

                <span
                  class="status"
                  :class="{
                    published: product.status === 'PUBLISHED',
                  }"
                >
                  {{
                    product.status === 'PUBLISHED'
                      ? '已发布'
                      : product.status === 'DRAFT'
                        ? '草稿'
                        : product.status
                  }}
                </span>
              </div>

              <p>
                {{ product.category }}
                ·
                {{ product.conditionLevel }}
                ·
                #{{ product.id }}
              </p>

              <strong class="price">
                {{ formatPrice(product.price) }}
              </strong>
            </div>

            <div class="heading-actions">
              <button
                v-if="product.status === 'DRAFT'"
                type="button"
                class="primary-button"
                :disabled="busyProductId !== null"
                @click="handlePublish(product)"
              >
                {{
                  busyProductId === product.id
                    ? '处理中...'
                    : '确认发布'
                }}
              </button>

              <RouterLink
                v-else-if="product.status === 'PUBLISHED'"
                :to="`/products/${product.id}`"
                class="primary-link"
              >
                查看商品
              </RouterLink>
            </div>
          </div>

          <div class="image-section">
            <div class="image-section-heading">
              <h3>商品图片</h3>

              <span>
                {{ imagesByProduct[product.id]?.length ?? 0 }} / 5
              </span>
            </div>

            <div
              v-if="imagesByProduct[product.id]?.length"
              class="image-grid"
            >
              <div
                v-for="image in imagesByProduct[product.id]"
                :key="image.id"
                class="image-item"
              >
                <img
                  :src="image.url"
                  :alt="`${product.title}的图片`"
                />

                <button
                  v-if="product.status === 'DRAFT'"
                  type="button"
                  class="delete-image"
                  :disabled="busyProductId !== null"
                  @click="handleDeleteImage(product, image)"
                >
                  删除
                </button>
              </div>
            </div>

            <p v-else class="hint">
              暂无商品图片。
            </p>

            <template v-if="product.status === 'DRAFT'">
              <div
                v-if="previewUrls[product.id]"
                class="preview-area"
              >
                <p>待上传图片预览</p>

                <img
                  :src="previewUrls[product.id]"
                  alt="待上传图片预览"
                />
              </div>

              <div class="upload-controls">
                <label class="file-label">
                  选择图片
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    :disabled="
                      busyProductId !== null ||
                      (imagesByProduct[product.id]?.length ?? 0) >= 5
                    "
                    @change="onFileSelected($event, product.id)"
                  />
                </label>

                <span class="hint">
                  JPEG / PNG / WebP，单张不超过5MB
                </span>

                <button
                  type="button"
                  class="upload-button"
                  :disabled="
                    busyProductId !== null ||
                    !selectedFiles[product.id] ||
                    (imagesByProduct[product.id]?.length ?? 0) >= 5
                  "
                  @click="handleUpload(product)"
                >
                  {{
                    busyProductId === product.id
                      ? '处理中...'
                      : '上传图片'
                  }}
                </button>
              </div>
            </template>

            <p
              v-else
              class="hint published-hint"
            >
              商品已发布，当前版本暂不支持修改图片。
            </p>

            <p
              v-if="imageErrors[product.id]"
              class="image-error"
              role="alert"
            >
              {{ imageErrors[product.id] }}
              <button
                type="button"
                class="retry-link"
                @click="loadProductImages(product.id)"
              >
                重试加载
              </button>
            </p>
          </div>
        </article>
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
  width: min(1050px, 100%);
  margin: 0 auto;
  padding: 0 24px 80px;
  box-sizing: border-box;
}

.header {
  height: 82px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e5eaf3;
}

.brand {
  color: #4169e1;
  font-size: 22px;
  font-weight: 750;
  text-decoration: none;
}

.back-link {
  color: #64748b;
  text-decoration: none;
  font-size: 14px;
}

.intro {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 50px 0 32px;
}

.eyebrow {
  color: #4169e1;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.intro h1 {
  margin: 12px 0;
  font-size: 36px;
}

.intro p:last-child {
  color: #788196;
  font-size: 14px;
}

.primary-link,
.primary-button,
.upload-button {
  display: inline-block;
  padding: 11px 18px;
  border: 0;
  border-radius: 9px;
  background: #4169e1;
  color: white;
  font-size: 14px;
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.product-card {
  padding: 28px;
  border: 1px solid #e5eaf3;
  border-radius: 16px;
  background: white;
}

.product-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.product-summary {
  min-width: 0;
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.title-row h2 {
  margin: 0;
  font-size: 20px;
  overflow-wrap: anywhere;
}

.status {
  padding: 5px 10px;
  border-radius: 20px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}

.status.published {
  background: #ecfdf3;
  color: #16803c;
}

.product-summary p {
  color: #94a3b8;
  font-size: 13px;
}

.price {
  color: #4169e1;
  font-size: 21px;
}

.image-section {
  margin-top: 26px;
  padding-top: 24px;
  border-top: 1px solid #edf0f5;
}

.image-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.image-section-heading h3 {
  margin: 0;
  font-size: 16px;
}

.image-section-heading span {
  color: #94a3b8;
  font-size: 13px;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.image-item {
  position: relative;
  overflow: hidden;
  aspect-ratio: 1;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  background: #f1f5ff;
}

.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.delete-image {
  position: absolute;
  bottom: 8px;
  right: 8px;
  padding: 6px 10px;
  border: 0;
  border-radius: 6px;
  background: rgba(220, 38, 38, 0.9);
  color: white;
  font-size: 12px;
  cursor: pointer;
}

.preview-area {
  margin-top: 20px;
  padding: 14px;
  border: 1px dashed #b9c9f6;
  border-radius: 10px;
  background: #f8faff;
}

.preview-area p {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}

.preview-area img {
  display: block;
  width: 150px;
  height: 150px;
  object-fit: cover;
  border-radius: 9px;
}

.upload-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 20px;
}

.file-label {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 10px 16px;
  border: 1px solid #cdd8fa;
  border-radius: 9px;
  background: #f1f5ff;
  color: #4169e1;
  font-size: 13px;
  cursor: pointer;
}

.file-label input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.hint {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.7;
}

.published-hint {
  margin-top: 18px;
}

.image-error {
  color: #dc2626;
  font-size: 13px;
}

.retry-link {
  margin-left: 10px;
  padding: 0;
  border: 0;
  background: none;
  color: #4169e1;
  cursor: pointer;
}

.message {
  padding: 14px 18px;
  border-radius: 9px;
  margin: 0 0 20px;
  font-size: 14px;
}

.success {
  background: #ecfdf3;
  color: #16803c;
}

.error {
  background: #fef2f2;
  color: #dc2626;
}

.state-panel {
  padding: 80px 20px;
  border: 1px solid #e5eaf3;
  border-radius: 15px;
  background: white;
  text-align: center;
  color: #64748b;
}

.state-panel a {
  color: #4169e1;
}

@media (max-width: 750px) {
  .image-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .intro,
  .product-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .container {
    padding: 0 16px 40px;
  }

  .product-card {
    padding: 18px;
  }

  .image-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
