
<script setup lang="ts">
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
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-icon">C</span>
        <span class="brand-name">CampusTrade</span>
      </div>

      <div class="topbar-caption">
        校园二手交易平台
      </div>
    </header>

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
          <span class="section-label">WHAT YOU CAN DO</span>
          <h2>让校园闲置流动起来</h2>
          <p>从发现商品到完成交易，让每一步都更加简单。</p>
        </div>

        <div class="feature-grid">
          <article
            v-for="feature in features"
            :key="feature.id"
            class="feature-card"
          >
            <span class="feature-number">{{ feature.id }}</span>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.description }}</p>
          </article>
        </div>
      </section>
    </main>

    <footer class="site-footer">
      <span>CampusTrade</span>
      <span>校园二手交易平台 · {{ new Date().getFullYear() }}</span>
    </footer>
  </div>
</template>
