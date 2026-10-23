<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fileUrl, type PublicBanner } from '../api'
import { portalLabel } from '../labels'
import { localizeHref } from '../seo'

const props = withDefaults(defineProps<{ items: PublicBanner[]; interval?: number }>(), {
  interval: 6000
})

const { t, locale } = useI18n()
const index = ref(0)
const paused = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const count = computed(() => props.items.length)
const current = computed<PublicBanner | null>(() => props.items[index.value] ?? null)

function titleOf(banner: PublicBanner | null): string {
  if (!banner) {
    return ''
  }
  return banner.title?.trim() || portalLabel(t, banner.titleKey)
}

function go(step: number): void {
  if (count.value <= 1) {
    return
  }
  index.value = (index.value + step + count.value) % count.value
}

function previous(): void {
  go(-1)
}

function next(): void {
  go(1)
}

/** 键盘可操作：左右方向键切换，RTL 下方向取反。 */
function onKeydown(event: KeyboardEvent): void {
  const rtl = typeof document !== 'undefined' && document.documentElement.dir === 'rtl'
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    go(rtl ? -1 : 1)
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    go(rtl ? 1 : -1)
  }
}

function stop(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function start(): void {
  stop()
  if (typeof setInterval === 'undefined' || count.value <= 1) {
    return
  }
  timer = setInterval(() => {
    if (!paused.value) {
      go(1)
    }
  }, props.interval)
}

onMounted(start)
onBeforeUnmount(stop)

watch(count, () => {
  if (index.value >= count.value) {
    index.value = 0
  }
  start()
})

/** 悬停或聚焦时暂停自动播放，避免用户正在看时被切走（WCAG 2.2.2）。 */
function pause(): void {
  paused.value = true
}

function resume(): void {
  paused.value = false
}
</script>

<template>
  <section
    v-if="current"
    class="carousel"
    role="group"
    aria-roledescription="carousel"
    tabindex="0"
    @keydown="onKeydown"
    @mouseenter="pause"
    @mouseleave="resume"
    @focusin="pause"
    @focusout="resume"
  >
    <figure class="slide">
      <a v-if="current.linkUrl" class="slide-link" :href="localizeHref(locale, current.linkUrl)" rel="noopener">
        <img class="image" :src="fileUrl(current.imageFileId)" :alt="titleOf(current)" loading="lazy" />
      </a>
      <img v-else class="image" :src="fileUrl(current.imageFileId)" :alt="titleOf(current)" loading="lazy" />
      <figcaption v-if="titleOf(current)" class="caption">{{ titleOf(current) }}</figcaption>
    </figure>

    <button
      v-if="count > 1"
      type="button"
      class="control control--prev"
      :aria-label="t('portal.carousel.prev')"
      @click="previous"
    >
      <span aria-hidden="true">‹</span>
    </button>
    <button
      v-if="count > 1"
      type="button"
      class="control control--next"
      :aria-label="t('portal.carousel.next')"
      @click="next"
    >
      <span aria-hidden="true">›</span>
    </button>
  </section>
</template>

<style scoped>
.carousel {
  position: relative;
  border-radius: var(--vane-radius-lg);
  overflow: hidden;
  background: var(--vane-color-surface-brand);
}

.carousel:focus-visible {
  outline: 2px solid var(--vane-color-brand);
  outline-offset: 2px;
}

.slide {
  position: relative;
  margin: 0;
}

.slide-link {
  display: block;
}

.image {
  display: block;
  width: 100%;
  height: 360px;
  object-fit: cover;
}

.caption {
  position: absolute;
  inset-inline-start: var(--vane-space-lg);
  bottom: var(--vane-space-lg);
  padding: var(--vane-space-xs) var(--vane-space-md);
  border-radius: var(--vane-radius-pill);
  background: var(--vane-color-overlay);
  color: var(--vane-color-text-inverse);
  font-size: var(--vane-font-size-base);
}

.control {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--vane-radius-pill);
  background: var(--vane-color-overlay);
  color: var(--vane-color-text-inverse);
  font-size: var(--vane-font-size-xl);
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.control:hover {
  background: var(--vane-color-overlay-strong);
}

.control--prev {
  inset-inline-start: var(--vane-space-md);
}

.control--next {
  inset-inline-end: var(--vane-space-md);
}
</style>
