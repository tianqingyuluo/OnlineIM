<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Download, FileText, ImageOff, X } from 'lucide-vue-next'
import { formatFileSize, parseMediaContent } from '@/utils/media-message'
import { authorizeChatFileUrl } from '@/utils/chat-file-url'
import VoiceMessage from '@/components/MainPart/VoiceMessage.vue'

const props = defineProps<{
  messageType: string
  content: unknown
  isSelf?: boolean
}>()

const media = computed(() => parseMediaContent(props.messageType, props.content))
const thumbnailSrc = computed(() => authorizeChatFileUrl(media.value?.thumbnail_url || media.value?.url))
const originalSrc = computed(() => authorizeChatFileUrl(media.value?.url))
const previewOpen = ref(false)
const thumbFailed = ref(false)

const MAX_THUMB_EDGE = 240

/** 按缩略图/原图比例预留尺寸，避免图片加载完成后消息列表跳动 */
const thumbStyle = computed(() => {
  const value = media.value
  const width = value?.thumbnail_width || value?.width
  const height = value?.thumbnail_height || value?.height
  if (!width || !height) return { width: `${MAX_THUMB_EDGE}px`, maxWidth: '100%', aspectRatio: '4 / 3' }
  const scale = Math.min(1, MAX_THUMB_EDGE / Math.max(width, height))
  // 宽度按比例预留并可随气泡收缩（手机窄屏），高度由 aspect-ratio 跟随
  return {
    width: `${Math.max(40, Math.round(width * scale))}px`,
    maxWidth: '100%',
    aspectRatio: `${width} / ${height}`,
  }
})

const dimensions = computed(() => {
  const value = media.value
  return value?.width && value?.height ? `${value.width}×${value.height}` : ''
})

function openPreview() {
  if (media.value) previewOpen.value = true
}

function closePreview() {
  previewOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closePreview()
}

watch(previewOpen, open => {
  if (typeof window === 'undefined') return
  if (open) window.addEventListener('keydown', onKeydown)
  else window.removeEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div v-if="!media" data-testid="media-invalid" class="text-sm text-slate-400">
    {{ messageType === 'image' ? '[图片]' : messageType === 'voice' ? '[语音]' : '[文件]' }}
  </div>

  <VoiceMessage v-else-if="messageType === 'voice'" :media="media" :is-self="isSelf" />

  <div v-else-if="messageType === 'image'" data-testid="media-image">
    <button
      type="button"
      class="block overflow-hidden rounded-lg bg-slate-100"
      :style="thumbStyle"
      :title="`${media.name}${dimensions ? ` · ${dimensions}` : ''}`"
      @click="openPreview"
    >
      <img
        v-if="!thumbFailed"
        :src="thumbnailSrc"
        referrerpolicy="no-referrer"
        :alt="media.name"
        loading="lazy"
        class="h-full w-full object-cover"
        data-testid="media-thumbnail"
        @error="thumbFailed = true"
      />
      <span v-else class="flex h-full w-full items-center justify-center text-slate-400">
        <ImageOff class="h-6 w-6" />
      </span>
    </button>
    <Teleport to="body">
      <div
        v-if="previewOpen"
        class="fixed inset-0 z-[1000] flex flex-col items-center justify-center bg-black/80 p-4"
        data-testid="media-preview"
        @click.self="closePreview"
      >
        <div class="absolute right-4 top-4 flex items-center gap-3 text-white">
          <a
            :href="originalSrc"
            target="_blank"
            rel="noopener noreferrer"
            referrerpolicy="no-referrer"
            class="rounded px-2 py-1 text-sm hover:bg-white/20"
          >查看原图</a>
          <a
            :href="originalSrc"
            :download="media.name"
            referrerpolicy="no-referrer"
            class="rounded p-1 hover:bg-white/20"
            title="下载"
          ><Download class="h-5 w-5" /></a>
          <button type="button" class="rounded p-1 hover:bg-white/20" title="关闭" @click="closePreview">
            <X class="h-6 w-6" />
          </button>
        </div>
        <img
          :src="originalSrc"
          referrerpolicy="no-referrer"
          :alt="media.name"
          class="max-h-[85vh] max-w-[92vw] object-contain"
          data-testid="media-original"
        />
        <div class="mt-2 text-xs text-white/70">
          {{ media.name }}<template v-if="dimensions"> · {{ dimensions }}</template> · {{ formatFileSize(media.size) }}
        </div>
      </div>
    </Teleport>
  </div>

  <a
    v-else
    :href="originalSrc"
    referrerpolicy="no-referrer"
    :download="media.name"
    class="flex w-64 max-w-full items-center gap-3 rounded-lg bg-white/70 p-2 text-left ring-1 ring-black/[0.06] hover:bg-white"
    data-testid="media-file"
    :title="`下载 ${media.name}`"
  >
    <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-blue-100 text-blue-600">
      <FileText class="h-5 w-5" />
    </span>
    <span class="min-w-0 flex-1">
      <span class="block truncate text-sm font-medium text-slate-800" data-testid="media-file-name">{{ media.name }}</span>
      <span class="block text-xs text-slate-500" data-testid="media-file-size">{{ formatFileSize(media.size) }}</span>
    </span>
    <Download class="h-4 w-4 shrink-0 text-slate-400" />
  </a>
</template>
