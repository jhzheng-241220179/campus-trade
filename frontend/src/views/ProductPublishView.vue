
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { createProduct } from '../api/products'

const router = useRouter()

const form = reactive({
  title: '',
  price: '',
  category: '',
  description: '',
  conditionLevel: '',
  contact: '',
})

const submitting = ref(false)
const errorMessage = ref('')

const categories = [
  '教材',
  '数码电子',
  '生活用品',
  '服装配饰',
  '运动户外',
  '其他',
]

const conditions = [
  '全新',
  '九五新',
  '九成新',
  '八成新',
  '七成新及以下',
]

async function handleSubmit() {
  if (submitting.value) return

  errorMessage.value = ''

  const title = form.title.trim()
  const priceText = form.price.trim()
  const pricePattern =
    /^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/

  if (!title || title.length > 100) {
    errorMessage.value = '请输入不超过100个字符的商品标题'
    return
  }

  if (
    !pricePattern.test(priceText) ||
    Number(priceText) < 0.01
  ) {
    errorMessage.value = '价格必须大于等于0.01元，最多两位小数'
    return
  }

  if (!form.category || !form.conditionLevel) {
    errorMessage.value = '请选择商品分类与成色'
    return
  }

  if (form.contact.length > 100) {
    errorMessage.value = '联系方式不能超过100个字符'
    return
  }

  submitting.value = true

  try {
    const productId = await createProduct({
      title,
      price: Number(priceText),
      category: form.category,
      description: form.description.trim(),
      conditionLevel: form.conditionLevel,
      contact: form.contact.trim(),
    })

    await router.push({
      name: 'my-products',
      query: {
        created: String(productId),
      },
    })
  } catch (error) {
    if (axios.isAxiosError(error)) {
      errorMessage.value =
        error.response?.status === 401
          ? '登录已失效，请重新登录'
          : error.response?.data?.message ||
            '保存商品失败，请稍后重试'
    } else {
      errorMessage.value = '保存商品失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="page">
    <div class="container">
      <header class="header">
        <RouterLink to="/" class="brand">
          CampusTrade
        </RouterLink>
        <RouterLink to="/products/mine" class="back-link">
          我的商品
        </RouterLink>
      </header>

      <section class="intro">
        <p class="eyebrow">SELL ON CAMPUSTRADE</p>
        <h1>发布闲置商品</h1>
        <p>填写信息并保存草稿，随后添加图片并确认发布。</p>
      </section>

      <form class="form-card" @submit.prevent="handleSubmit">
        <div class="field">
          <label for="title">商品名称 *</label>
          <input
            id="title"
            v-model="form.title"
            type="text"
            maxlength="100"
            placeholder="例如：二手数据库教材"
            required
          />
        </div>

        <div class="field">
          <label for="price">出售价格（元）*</label>
          <input
            id="price"
            v-model="form.price"
            type="text"
            inputmode="decimal"
            placeholder="例如：35.00"
            required
          />
        </div>

        <div class="field-row">
          <div class="field">
            <label for="category">商品分类 *</label>
            <select
              id="category"
              v-model="form.category"
              required
            >
              <option disabled value="">请选择分类</option>
              <option
                v-for="item in categories"
                :key="item"
                :value="item"
              >
                {{ item }}
              </option>
            </select>
          </div>

          <div class="field">
            <label for="condition">商品成色 *</label>
            <select
              id="condition"
              v-model="form.conditionLevel"
              required
            >
              <option disabled value="">请选择成色</option>
              <option
                v-for="item in conditions"
                :key="item"
                :value="item"
              >
                {{ item }}
              </option>
            </select>
          </div>
        </div>

        <div class="field">
          <label for="description">商品描述</label>
          <textarea
            id="description"
            v-model="form.description"
            rows="6"
            placeholder="介绍商品使用情况、配件、瑕疵等..."
          ></textarea>
        </div>

        <div class="field">
          <label for="contact">联系方式（选填）</label>
          <input
            id="contact"
            v-model="form.contact"
            type="text"
            maxlength="100"
            placeholder="例如：站内联系"
          />
          <p class="hint">
            请注意个人信息保护。联系方式暂不在公开详情页展示。
          </p>
        </div>

        <div class="notice">
          <strong>发布流程</strong>
          <ol>
            <li>填写基本信息并保存草稿。</li>
            <li>在“我的商品”中上传最多 5 张图片。</li>
            <li>确认商品信息后，点击“确认发布”。</li>
          </ol>
        </div>

        <p
          v-if="errorMessage"
          class="error"
          role="alert"
        >
          {{ errorMessage }}
        </p>

        <div class="actions">
          <RouterLink to="/products" class="cancel">
            取消
          </RouterLink>
          <button type="submit" :disabled="submitting">
            {{ submitting ? '正在保存...' : '保存草稿，下一步上传图片' }}
          </button>
        </div>
      </form>
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
  width: min(900px, 100%);
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

.back-link,
.cancel {
  color: #64748b;
  font-size: 14px;
  text-decoration: none;
}

.intro {
  padding: 50px 0 30px;
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

.intro > p:last-child {
  color: #788196;
  font-size: 14px;
}

.form-card {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 36px;
  border: 1px solid #e5eaf3;
  border-radius: 18px;
  background: white;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.field label {
  color: #334155;
  font-size: 14px;
  font-weight: 650;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  border: 1px solid #dce3ee;
  border-radius: 9px;
  outline: none;
  background: white;
  color: #202536;
  font: inherit;
  font-size: 14px;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  border-color: #4169e1;
}

.field textarea {
  resize: vertical;
  line-height: 1.8;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.hint {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
}

.notice {
  padding: 17px 20px;
  border-radius: 10px;
  background: #f1f5ff;
  color: #64748b;
  font-size: 13px;
  line-height: 1.9;
}

.notice strong {
  color: #4169e1;
}

.notice ol {
  margin: 6px 0 0;
  padding-left: 22px;
}

.error {
  color: #dc2626;
  font-size: 14px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 22px;
}

.actions button {
  padding: 13px 22px;
  border: 0;
  border-radius: 9px;
  background: #4169e1;
  color: white;
  font-size: 14px;
  cursor: pointer;
}

.actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 600px) {
  .container {
    padding: 0 16px 40px;
  }

  .form-card {
    padding: 22px;
  }

  .field-row {
    grid-template-columns: 1fr;
  }
}
</style>
