<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue"
import CustomEmojiArea from './CustomEmojiArea.vue'
import UnicodeEmojiArea from './UnicodeEmojiArea.vue'
import { Smile, Sticker } from "lucide-vue-next"
import type { CustomEmoji } from "@/utils/sticker-message"

const emit = defineEmits<{
  /** Unicode 表情：插入输入框 */
  select: [emoji: string]
  /** 自定义表情：直接作为表情消息发送 */
  sticker: [emoji: CustomEmoji]
}>()

const showEmojiArea = ref(false)
const emojiAreaRef = ref<HTMLElement | null>(null)
const currentEmojiType = ref<'custom' | 'unicode'>('unicode')

function toggleEmojiArea(e?: MouseEvent) {
  e?.stopPropagation()
  showEmojiArea.value = !showEmojiArea.value
}

function handleClickOutside(e: MouseEvent) {
  if (showEmojiArea.value && !emojiAreaRef.value?.contains(e.target as Node)) {
    showEmojiArea.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

defineExpose({
  showEmojiArea,
  toggleEmojiArea
})

function handleEmojiSelect(emoji: string) {
  emit('select', emoji)
}

function handleStickerSend(emoji: CustomEmoji) {
  emit('sticker', emoji)
  showEmojiArea.value = false
}
</script>

<template>
  <div
    v-if="showEmojiArea"
    ref="emojiAreaRef"
    class="absolute bottom-[calc(100%+5px)] left-2 z-30 flex h-[min(400px,60vh)] w-[min(624px,calc(100vw-16px))] flex-col rounded-lg border bg-white shadow-lg sm:left-4"
    data-testid="emoji-panel"
    @click.stop
  >
    <div class="min-h-0 flex-1 p-3 sm:p-4">
      <CustomEmojiArea v-if="currentEmojiType === 'custom'" @send="handleStickerSend" />
      <UnicodeEmojiArea v-else @select="handleEmojiSelect" />
    </div>

    <div class="flex shrink-0 gap-2 border-t border-gray-200 px-2 py-1.5">
      <button
        type="button"
        title="系统表情"
        data-testid="emoji-tab-unicode"
        :class="[
          'h-8 w-8 rounded p-1 text-gray-600 transition-transform hover:scale-110',
          currentEmojiType === 'unicode' ? 'bg-blue-100' : '',
        ]"
        @click="currentEmojiType = 'unicode'"
      >
        <Smile class="h-full w-full" />
      </button>
      <button
        type="button"
        title="我的表情"
        data-testid="emoji-tab-custom"
        :class="[
          'h-8 w-8 rounded p-1 text-gray-600 transition-transform hover:scale-110',
          currentEmojiType === 'custom' ? 'bg-blue-100' : '',
        ]"
        @click="currentEmojiType = 'custom'"
      >
        <Sticker class="h-full w-full" />
      </button>
    </div>
  </div>
</template>
