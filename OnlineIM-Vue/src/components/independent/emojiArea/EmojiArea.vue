<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue"
import ImageEmojiArea from './ImageEmojiArea.vue'
import UnicodeEmojiArea from './UnicodeEmojiArea.vue'
import { Settings, Smile } from "lucide-vue-next"

// 定义emit
const emit = defineEmits(['select'])

const showEmojiArea = ref(false)
const emojiAreaRef = ref<HTMLElement | null>(null)
const currentEmojiType = ref<'image' | 'unicode'>('unicode')

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
</script>

<template>
  <div v-if="showEmojiArea" ref="emojiAreaRef" class="absolute bottom-[calc(100%+5px)] left-4 bg-white border rounded-lg shadow-lg p-6" style="width: 624px;height: 400px;" @click.stop>
    <ImageEmojiArea v-if="currentEmojiType === 'image'" @select="handleEmojiSelect" />
    <UnicodeEmojiArea v-else @select="handleEmojiSelect" />

    <!-- 底部切换栏 - 修改为原来的样式和位置 -->
    <div class="border-t border-gray-200 mt-1 pt-1 absolute bottom-0 left-0 right-0 px-2 pb-2">
      <div class="grid grid-cols-10 gap-2">
        <button 
          @click="currentEmojiType = 'image'"
          :class="[
            'w-8 h-8 p-1 cursor-pointer hover:scale-110 transition-transform text-gray-600 rounded',
            currentEmojiType === 'image' ? 'bg-blue-100' : ''
          ]"
        >
          <component :is="Settings"/>
        </button>
        <button 
          @click="currentEmojiType = 'unicode'"
          :class="[
            'w-8 h-8 p-1 cursor-pointer hover:scale-110 transition-transform text-gray-600 rounded',
            currentEmojiType === 'unicode' ? 'bg-blue-100' : ''
          ]"
        >
          <component :is="Smile"/>
        </button>
      </div>
    </div>
  </div>
</template>


