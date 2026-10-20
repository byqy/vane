<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { watch } from 'vue'
import { formatDate, http, problemOf, type PageResult } from '@vane/shared'
import { portalMessageKey } from '../i18n'
import { applyHomeSeo } from '../seo'
import SiteHeader from '../components/SiteHeader.vue'

interface PublicNotice {
  id: string
  level: string
  pinned: boolean
  title: string
  body: string | null
  publishAt: string | null
}


interface ArticleSummary {
  id: string
  slug: string
  title: string
  summary: string | null
  publishedAt: string | null
}

interface Banner {
  id: string
  position: string
  imageFileId: string
  titleKey: string | null
  linkUrl: string | null
  sortNo: number
}

const { t, locale } = useI18n()
const items = ref<PublicNotice[]>([])
const banners = ref<Banner[]>([])
const latestArticles = ref<ArticleSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const label = (key: string | null): string => (key ? t(portalMessageKey(key)) : '')

const bannerUrl = (banner: Banner): string => `/api/v1/public/files/${banner.imageFileId}`

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [noticeResponse, bannerResponse, articleResponse] = await Promise.all([
      http.get<PageResult<PublicNotice>>('/public/notices', {
        params: { 'page[number]': 0, 'page[size]': 20 }
      }),

      http.get<Banner[]>('/public/banners', { params: { position: 'home.top' } }),
      http.get<PageResult<ArticleSummary>>('/public/articles', {
        params: { 'page[number]': 0, 'page[size]': 5 }
      })
    ])
    items.value = noticeResponse.data.items
    banners.value = bannerResponse.data
    latestArticles.value = articleResponse.data.items
  } catch (e) {
    const problem = problemOf(e)
    error.value = problem ? `${problem.detail ?? problem.title}（${problem.code ?? problem.status}）` : t('common.loadFailed')
  } finally {
    loading.value = false
  }
}


const levelText = (level: string): string => t(`portal.level_${level}`)

// 切换语言后必须重新拉取内容（公告/导航/轮播的译文由后端按 Accept-Language 返回）
watch(locale, () => {
  void load()
    void applyHomeSeo(locale.value)
})

const sorted = computed(() =>
  [...items.value].sort((a, b) => Number(b.pinned) - Number(a.pinned))
)

onMounted(() => {
  void load()
  void applyHomeSeo(locale.value)
})
</script>

<template>
  <div class="container">
    <SiteHeader />

    <section v-if="banners.length > 0" class="banners">
      <a v-for="banner in banners" :key="banner.id" class="banner" :href="banner.linkUrl ?? '#'" rel="noopener">
        <img :src="bannerUrl(banner)" :alt="label(banner.titleKey)" loading="lazy" />
        <span v-if="banner.titleKey" class="banner-title">{{ label(banner.titleKey) }}</span>
      </a>
    </section>

    <h2 class="section">{{ t('portal.notices') }}</h2>

    <p v-if="loading" class="muted">…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="sorted.length === 0" class="muted">{{ t('portal.empty') }}</p>

    <ul class="list">
      <li v-for="notice in sorted" :key="notice.id" class="card">
        <div class="meta">
          <span class="tag" :data-level="notice.level">{{ levelText(notice.level) }}</span>
          <span v-if="notice.pinned" class="pin">📌</span>
        </div>
        <h3>{{ notice.title }}</h3>
        <p v-if="notice.body" class="body">{{ notice.body }}</p>
      </li>
    </ul>

    <section v-if="latestArticles.length > 0" class="articles">
      <div class="section-row">
        <h2 class="section">{{ t('portal.latestArticles') }}</h2>
        <router-link class="more" :to="`/${locale}/articles`">{{ t('portal.moreArticles') }}</router-link>
      </div>
      <ul class="list">
        <li v-for="article in latestArticles" :key="article.id" class="card">
          <h3>
            <router-link :to="`/${locale}/articles/${article.slug}`">{{ article.title }}</router-link>
          </h3>
          <p v-if="article.summary" class="body">{{ article.summary }}</p>
          <p v-if="article.publishedAt" class="article-meta">{{ formatDate(article.publishedAt, locale) }}</p>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
}

.brand {
  font-size: 20px;
  font-weight: 600;
  color: inherit;
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 16px;
  flex: 1;
  flex-wrap: wrap;
}

.nav a {
  color: #374151;
  text-decoration: none;
  font-size: 14px;
}

.nav a:hover {
  color: var(--vane-color-brand);
}

.ghost {
  border: 1px solid #d0d5dd;
  background: #fff;
  border-radius: 8px;
  padding: 6px 14px;
  cursor: pointer;
}

.banners {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  margin-bottom: 8px;
}

.banner {
  position: relative;
  display: block;
  border-radius: var(--vane-radius-card);
  overflow: hidden;
  background: #eef2ff;
  min-height: 140px;
}

.banner img {
  width: 100%;
  height: 160px;
  object-fit: cover;
  display: block;
}

.banner-title {
  position: absolute;
  inset-inline-start: 12px;
  bottom: 10px;
  color: #fff;
  font-size: 14px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}

.section {
  font-size: 16px;
  color: #4b5563;
  margin: 24px 0 12px;
}

.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}

.card {
  background: #fff;
  border-radius: var(--vane-radius-card);
  padding: 16px 18px;
  border: 1px solid #eceff3;
}

.meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #3b5bdb;
}

.tag[data-level='WARN'] {
  background: #fff7e6;
  color: #b25e09;
}

.tag[data-level='URGENT'] {
  background: #ffecec;
  color: #c92a2a;
}

h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.body {
  margin: 0;
  color: #4b5563;
  white-space: pre-wrap;
}

.muted {
  color: #6b7280;
}

.error {
  color: #c92a2a;
}
</style>







