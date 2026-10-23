<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PublicProductMedia } from '@vane/shared'
import { fileUrl } from '../api'

const props = withDefaults(
  defineProps<{
    media: PublicProductMedia[]
    videoFileId?: string | null
  }>(),
  { videoFileId: null }
)

const { t } = useI18n()
const active = ref(0)

const images = computed(() => props.media.filter((item) => item.kind?.toUpperCase() === 'IMAGE'))
const extraVideos = computed(() => props.media.filter((item) => item.kind?.toUpperCase() === 'VIDEO'))
const hasVideo = computed(() => Boolean(props.videoFileId) || extraVideos.value.length > 0)
const main = computed<PublicProductMedia | null>(() => images.value[active.value] ?? images.value[0] ?? null)

watch(images, () => {
  if (active.value >= images.value.length) {
    active.value = 0
  }
})

function captionOf(item: PublicProductMedia, index: number): string {
  return item.caption?.trim() || String(index + 1)
}
</script>

<template>
  <section v-if="main" class="media-block">
    <h2 class="media-title">{{ t('portal.products.gallery') }}</h2>
    <div class="stage">
      <img class="stage-image" :src="fileUrl(main.fileId)" :alt="captionOf(main, active)" />
    </div>
    <div v-if="images.length > 1" class="thumbs">
      <button
        v-for="(item, index) in images"
        :key="`${item.fileId}-${index}`"
        type="button"
        class="thumb"
        :class="{ 'thumb--active': index === active }"
        :aria-label="captionOf(item, index)"
        :aria-pressed="index === active"
        @click="active = index"
      >
        <img :src="fileUrl(item.fileId)" :alt="captionOf(item, index)" loading="lazy" />
      </button>
    </div>
  </section>

  <section v-if="hasVideo" class="media-block">
    <h2 class="media-title">{{ t('portal.products.video') }}</h2>
    <video v-if="videoFileId" class="video" controls :src="fileUrl(videoFileId)" />
    <video
      v-for="item in extraVideos"
      :key="item.fileId"
      class="video"
      controls
      :src="fileUrl(item.fileId)"
    />
  </section>
</template>

<style scoped>
.media-block + .media-block {
  margin-top: var(--vane-space-xl);
}

.media-title {
  margin: 0 0 var(--vane-space-sm);
  font-size: var(--vane-font-size-lg);
  font-weight: var(--vane-font-weight-bold);
}

.stage {
  border-radius: var(--vane-radius-card);
  overflow: hidden;
  border: 1px solid var(--vane-color-border);
  background: var(--vane-color-surface-muted);
}

.stage-image {
  display: block;
  width: 100%;
  max-height: 520px;
  object-fit: contain;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vane-space-xs);
  margin-top: var(--vane-space-sm);
}

.thumb {
  width: 88px;
  height: 64px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: var(--vane-radius-sm);
  overflow: hidden;
  background: var(--vane-color-surface-muted);
  cursor: pointer;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb--active {
  border-color: var(--vane-color-brand);
}

.thumb:focus-visible {
  outline: 2px solid var(--vane-color-brand);
  outline-offset: 2px;
}

.video {
  display: block;
  width: 100%;
  border-radius: var(--vane-radius-card);
  background: var(--vane-color-surface-inverse);
}

.video + .video {
  margin-top: var(--vane-space-sm);
}
</style>
