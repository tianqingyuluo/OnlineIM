<script setup lang="ts">
import { computed, ref } from 'vue'
import { parseStickerContent, stickerDisplaySize } from '@/utils/sticker-message'
import { authorizeChatFileUrl } from '@/utils/chat-file-url'

const props = defineProps<{
  messageType: string
  content: unknown
}>()

const emoji = computed(() => parseStickerContent(props.messageType, props.content))
const emojiSrc = computed(() => authorizeChatFileUrl(emoji.value?.url))
const failed = ref(false)

/** 按原图比例预留尺寸（长边 ≤ 140px），加载前后消息列表不跳动；窄屏随容器收缩 */
const style = computed(() => {
  const size = stickerDisplaySize(emoji.value?.width, emoji.value?.height)
  return { width: `${size.width}px`, maxWidth: '100%', aspectRatio: `${size.width} / ${size.height}` }
})
</script>

<template>
  <div
    v-if="!emoji || failed"
    data-testid="sticker-invalid"
    class="rounded-xl bg-slate-50 px-3 py-2.5 text-sm text-slate-400 ring-1 ring-black/[0.04]"
  >
    [表情]
  </div>
  <img
    v-else
    :src="emojiSrc"
    referrerpolicy="no-referrer"
    :alt="emoji.name || '表情'"
    :title="emoji.name"
    :style="style"
    class="block select-none object-contain [-webkit-touch-callout:none]"
    data-testid="sticker-message"
    :data-animated="emoji.animated ? 'true' : 'false'"
    draggable="false"
    loading="lazy"
    @error="failed = true"
  />
</template>
