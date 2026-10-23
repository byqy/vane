<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDate, type PublicArticleView, type PublicProductView } from '@vane/shared'
import {
  fetchArticles,
  fetchBanners,
  fetchNotices,
  fetchPage,
  fetchProducts,
  fetchSite,
  fileUrl,
  type PublicBanner,
  type PublicNotice,
  type PublicPageContent,
  type PublicSiteInfo
} from '../api'
import { isNotFound } from '../problem'
import { useAsyncSection, type AsyncSection } from '../section'
import { applyHomeSeo } from '../seo'
import { stripHtml } from '../text'
import BannerCarousel from '../components/BannerCarousel.vue'
import ProductCard from '../components/ProductCard.vue'
import SectionHeading from '../components/SectionHeading.vue'

const { t, te, locale } = useI18n()

const site = useAsyncSection<PublicSiteInfo | null>(null)
const banners = useAsyncSection<PublicBanner[]>([])
const about = useAsyncSection<PublicPageContent | null>(null)
const products = useAsyncSection<PublicProductView[]>([])
const articles = useAsyncSection<PublicArticleView[]>([])
const notices = useAsyncSection<PublicNotice[]>([])

/** 区块有加载中、有错误或有数据时才渲染；无数据整块隐藏，不显示空标题。 */
function showBlock(section: AsyncSection<unknown>): boolean {
  if (section.loading || section.error) {
    return true
  }
  const data = section.data as unknown
  return Array.isArray(data) ? data.length > 0 : Boolean(data)
}

/** 首屏主视觉：优先公司介绍页封面，其次首张轮播图；都没有时用令牌渐变背景。 */
const heroImageId = computed(
  () =>
    about.data?.coverFileId ??
    banners.data.find((banner) => banner.imageFileId)?.imageFileId ??
    null
)
const heroStyle = computed(() =>
  heroImageId.value ? { backgroundImage: `url(${fileUrl(heroImageId.value)})` } : undefined
)
const heroTitle = computed(() => site.data?.siteName || t('portal.site'))
const heroSubtitle = computed(() => about.data?.summary || site.data?.description || '')

const aboutExcerpt = computed(() => about.data?.summary || stripHtml(about.data?.body))

const sortedNotices = computed(() =>
  [...notices.data].sort((a, b) => Number(b.pinned) - Number(a.pinned))
)

/** 核心优势：静态文案按门户文案键渲染，键未落地时整块隐藏（不硬编码文案）。 */
const advantages = computed(() =>
  (['quality', 'service', 'expertise'] as const)
    .map((key) => ({ titleKey: `portal.advantages.${key}.title`, descriptionKey: `portal.advantages.${key}.description` }))
    .filter((item) => te(item.titleKey))
    .map((item) => ({
      title: t(item.titleKey),
      description: te(item.descriptionKey) ? t(item.descriptionKey) : ''
    }))
)

function loadSite(): Promise<void> {
  return site.load(() => fetchSite())
}

function loadBanners(): Promise<void> {
  return banners.load(() => fetchBanners('home.top'))
}

function loadAbout(): Promise<void> {
  return about.load(async () => {
    try {
      return await fetchPage('about')
    } catch (e) {
      if (isNotFound(e)) {
        return null
      }
      throw e
    }
  })
}

function loadProducts(): Promise<void> {
  return products.load(async () => (await fetchProducts({ page: 1, size: 6 })).items)
}

function loadArticles(): Promise<void> {
  return articles.load(() => fetchArticles(5))
}

function loadNotices(): Promise<void> {
  return notices.load(() => fetchNotices(20))
}

async function loadAll(): Promise<void> {
  await Promise.all([loadSite(), loadBanners(), loadAbout(), loadProducts(), loadArticles(), loadNotices()])
  void applyHomeSeo(locale.value)
}

onMounted(() => {
  void loadAll()
})

// 语言切换后所有区块内容（公告/导航/轮播/产品/文章的译文）都要重新拉取
watch(locale, () => {
  void loadAll()
})

const levelText = (level: string): string => t(`portal.level_${level}`)
</script>

<template>
  <div class="home">
    <section class="hero" :class="{ 'hero--image': heroImageId }" :style="heroStyle">
      <div class="container hero-inner">
        <h1 class="hero-title">{{ heroTitle }}</h1>
        <p v-if="heroSubtitle" class="hero-subtitle">{{ heroSubtitle }}</p>
        <div class="hero-actions">
          <router-link class="button button--inverse" :to="`/${locale}/contact`">
            {{ t('portal.hero.cta') }}
          </router-link>
          <router-link class="button button--ghost" :to="`/${locale}/products`">
            {{ t('portal.hero.ctaSecondary') }}
          </router-link>
        </div>
      </div>
    </section>

    <section v-if="showBlock(banners)" class="section">
      <div class="container">
        <p v-if="banners.loading" class="state">{{ t('portal.common.loading') }}</p>
        <p v-else-if="banners.error" class="state state--error">
          {{ banners.error }}
          <button type="button" class="state__retry" @click="loadBanners">{{ t('portal.common.retry') }}</button>
        </p>
        <BannerCarousel v-else :items="banners.data" />
      </div>
    </section>

    <section v-if="showBlock(about)" class="section section--muted">
      <div class="container">
        <SectionHeading :title="t('portal.section.about')" />
        <p v-if="about.loading" class="state">{{ t('portal.common.loading') }}</p>
        <p v-else-if="about.error" class="state state--error">
          {{ about.error }}
          <button type="button" class="state__retry" @click="loadAbout">{{ t('portal.common.retry') }}</button>
        </p>
        <template v-else-if="about.data">
          <p class="about-text">{{ aboutExcerpt }}</p>
          <router-link class="about-more" :to="`/${locale}/about`">
            {{ t('portal.section.aboutMore') }}
          </router-link>
        </template>
      </div>
    </section>

    <section v-if="showBlock(products)" class="section">
      <div class="container">
        <SectionHeading
          :title="t('portal.section.products')"
          :action-to="`/${locale}/products`"
          :action-text="t('portal.section.viewAll')"
        />
        <p v-if="products.loading" class="state">{{ t('portal.common.loading') }}</p>
        <p v-else-if="products.error" class="state state--error">
          {{ products.error }}
          <button type="button" class="state__retry" @click="loadProducts">{{ t('portal.common.retry') }}</button>
        </p>
        <div v-else class="product-grid">
          <ProductCard v-for="product in products.data" :key="product.id" :product="product" />
        </div>
      </div>
    </section>

    <section v-if="advantages.length > 0" class="section section--muted">
      <div class="container">
        <SectionHeading :title="t('portal.section.advantages')" />
        <ul class="advantage-grid">
          <li v-for="item in advantages" :key="item.title" class="advantage-card">
            <h3 class="advantage-title">{{ item.title }}</h3>
            <p v-if="item.description" class="advantage-description">{{ item.description }}</p>
          </li>
        </ul>
      </div>
    </section>

    <section v-if="showBlock(articles)" class="section">
      <div class="container">
        <SectionHeading
          :title="t('portal.section.news')"
          :action-to="`/${locale}/articles`"
          :action-text="t('portal.section.viewAll')"
        />
        <p v-if="articles.loading" class="state">{{ t('portal.common.loading') }}</p>
        <p v-else-if="articles.error" class="state state--error">
          {{ articles.error }}
          <button type="button" class="state__retry" @click="loadArticles">{{ t('portal.common.retry') }}</button>
        </p>
        <ul v-else class="news-list">
          <li v-for="article in articles.data" :key="article.id" class="news-item">
            <router-link class="news-link" :to="`/${locale}/articles/${article.slug}`">
              {{ article.title ?? article.slug }}
            </router-link>
            <p v-if="article.summary" class="news-summary">{{ article.summary }}</p>
            <p v-if="article.publishedAt" class="news-date">{{ formatDate(article.publishedAt, locale) }}</p>
          </li>
        </ul>
      </div>
    </section>

    <section v-if="showBlock(notices)" class="section section--muted">
      <div class="container">
        <SectionHeading :title="t('portal.notices')" />
        <p v-if="notices.loading" class="state">{{ t('portal.common.loading') }}</p>
        <p v-else-if="notices.error" class="state state--error">
          {{ notices.error }}
          <button type="button" class="state__retry" @click="loadNotices">{{ t('portal.common.retry') }}</button>
        </p>
        <ul v-else class="notice-list">
          <li v-for="notice in sortedNotices" :key="notice.id" class="notice">
            <div class="notice-meta">
              <span class="tag" :data-level="notice.level">{{ levelText(notice.level) }}</span>
              <span v-if="notice.pinned" class="pin">📌</span>
            </div>
            <h3 class="notice-title">{{ notice.title }}</h3>
            <p v-if="notice.body" class="notice-body">{{ notice.body }}</p>
          </li>
        </ul>
      </div>
    </section>

    <section class="cta">
      <div class="container cta-inner">
        <h2 class="cta-title">{{ t('portal.section.contact') }}</h2>
        <router-link class="button button--primary" :to="`/${locale}/contact`">
          {{ t('portal.hero.cta') }}
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  background: linear-gradient(135deg, var(--vane-color-surface-brand), var(--vane-color-bg));
  background-size: cover;
  background-position: center;
}

.hero--image::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--vane-color-overlay);
}

.hero-inner {
  position: relative;
  z-index: 1;
  padding-block: var(--vane-space-3xl);
  max-width: var(--vane-container);
}

.hero-title {
  margin: 0 0 var(--vane-space-sm);
  font-size: var(--vane-font-size-3xl);
  line-height: var(--vane-line-height-tight);
  color: var(--vane-color-text);
}

.hero-subtitle {
  margin: 0 0 var(--vane-space-xl);
  max-width: 640px;
  font-size: var(--vane-font-size-md);
  color: var(--vane-color-text-secondary);
}

.hero--image .hero-title,
.hero--image .hero-subtitle {
  color: var(--vane-color-text-inverse);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vane-space-sm);
}

.about-text {
  margin: 0 0 var(--vane-space-md);
  max-width: 820px;
  color: var(--vane-color-text-secondary);
  font-size: var(--vane-font-size-md);
}

.about-more {
  color: var(--vane-color-brand);
  text-decoration: none;
  font-size: var(--vane-font-size-base);
}

.about-more:hover {
  text-decoration: underline;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--vane-space-lg);
}

.advantage-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--vane-space-lg);
  list-style: none;
  margin: 0;
  padding: 0;
}

.advantage-card {
  background: var(--vane-color-surface);
  border: 1px solid var(--vane-color-border);
  border-radius: var(--vane-radius-card);
  padding: var(--vane-space-lg);
}

.advantage-title {
  margin: 0 0 var(--vane-space-xs);
  font-size: var(--vane-font-size-md);
  font-weight: var(--vane-font-weight-bold);
}

.advantage-description {
  margin: 0;
  color: var(--vane-color-text-secondary);
}

.news-list {
  display: grid;
  gap: var(--vane-space-md);
  list-style: none;
  margin: 0;
  padding: 0;
}

.news-item {
  background: var(--vane-color-surface);
  border: 1px solid var(--vane-color-border);
  border-radius: var(--vane-radius-card);
  padding: var(--vane-space-md) var(--vane-space-lg);
}

.news-link {
  color: var(--vane-color-text);
  font-size: var(--vane-font-size-md);
  font-weight: var(--vane-font-weight-medium);
  text-decoration: none;
}

.news-link:hover {
  color: var(--vane-color-brand);
}

.news-summary {
  margin: var(--vane-space-xs) 0 0;
  color: var(--vane-color-text-secondary);
}

.news-date {
  margin: var(--vane-space-xs) 0 0;
  color: var(--vane-color-text-subtle);
  font-size: var(--vane-font-size-sm);
}

.notice-list {
  display: grid;
  gap: var(--vane-space-md);
  list-style: none;
  margin: 0;
  padding: 0;
}

.notice {
  background: var(--vane-color-surface);
  border: 1px solid var(--vane-color-border);
  border-radius: var(--vane-radius-card);
  padding: var(--vane-space-md) var(--vane-space-lg);
}

.notice-meta {
  display: flex;
  gap: var(--vane-space-xs);
  align-items: center;
  margin-bottom: var(--vane-space-2xs);
}

.tag {
  font-size: var(--vane-font-size-xs);
  padding: var(--vane-space-2xs) var(--vane-space-xs);
  border-radius: var(--vane-radius-pill);
  background: var(--vane-color-surface-brand);
  color: var(--vane-color-brand-strong);
}

.tag[data-level='WARN'] {
  background: var(--vane-color-warning-soft);
  color: var(--vane-color-warning);
}

.tag[data-level='URGENT'] {
  background: var(--vane-color-danger-soft);
  color: var(--vane-color-danger);
}

.notice-title {
  margin: 0 0 var(--vane-space-2xs);
  font-size: var(--vane-font-size-md);
}

.notice-body {
  margin: 0;
  color: var(--vane-color-text-secondary);
  white-space: pre-wrap;
}

.cta {
  padding-block: var(--vane-space-2xl);
  background: var(--vane-color-surface-inverse);
}

.cta-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--vane-space-md);
}

.cta-title {
  margin: 0;
  color: var(--vane-color-text-inverse);
  font-size: var(--vane-font-size-xl);
}
</style>
