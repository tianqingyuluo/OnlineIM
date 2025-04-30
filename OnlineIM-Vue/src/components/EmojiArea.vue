<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue"
import { Smile } from "lucide-vue-next"

const emojiClass = [
  {
    emojiClassName: "emoji1",
    src: "/images/group.png",
    emojiList: [
      { emojiName: "emoji1", src: "/images/group.png" },
      { emojiName: "emoji2", src: "/images/help.png" },
      { emojiName: "emoji3", src: "/images/smile.png" },
      { emojiName: "emoji4", src: "/images/laugh.png" },
      { emojiName: "emoji5", src: "/images/wink.png" },
      { emojiName: "emoji6", src: "/images/cool.png" }
    ]
  },
  {
    emojiClassName: "emoji2",
    icon: Smile,
    emojiList: [
      { emojiName: "emoji7", src: "/images/example1.png" },
      { emojiName: "emoji8", src: "/images/example2.png" }
    ]
  },
  // 可以继续添加更多表情分类
]

const showEmojiArea = ref(false)
const emojiAreaRef = ref<HTMLElement | null>(null)
const currentEmojiClassIndex = ref(0)

function toggleEmojiArea(e?: MouseEvent) {
  e?.stopPropagation()
  showEmojiArea.value = !showEmojiArea.value
}

function handleClickOutside(e: MouseEvent) {
  if (showEmojiArea.value && !emojiAreaRef.value?.contains(e.target as Node)) {
    showEmojiArea.value = false
  }
}

function selectEmojiClass(index: number) {
  currentEmojiClassIndex.value = index
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
</script>

<template>
  <div
      v-if="showEmojiArea"
      ref="emojiAreaRef"
      class="absolute bottom-[calc(100%+5px)] left-4 bg-white border rounded-lg shadow-lg p-6"
      style="width: 624px;height: 400px;"
      @click.stop
  >
    <div class="grid grid-cols-6 gap-4">
      <template v-for="(emoji, index) in emojiClass[currentEmojiClassIndex].emojiList" :key="index">
        <img
            :src="emoji.src"
            :alt="emoji.emojiName"
            class="w-24 h-24 object-contain cursor-pointer hover:scale-110 transition-transform"
        />
      </template>
    </div>

    <!-- 添加横线和 emoji分类 -->
    <div class="border-t border-gray-200 mt-1 pt-1 absolute bottom-0 left-0 right-0 px-2 pb-2">
      <div class="grid grid-cols-10 gap-2">
        <template v-for="(emojiClassItem, index) in emojiClass" :key="index">
          <!-- 使用动态组件渲染图标 -->
          <component
              v-if="emojiClassItem.icon"
              :is="emojiClassItem.icon"
              :class="[
      'w-8 h-8 p-1 cursor-pointer hover:scale-110 transition-transform text-gray-600 rounded',
      currentEmojiClassIndex === index ? 'bg-blue-100' : ''
    ]"
              @click="selectEmojiClass(index)"
          />
          <!-- 使用图片渲染图标 -->
          <img
              v-else
              :src="emojiClassItem.src"
              :alt="emojiClassItem.emojiClassName"
              :class="[
      'w-8 h-8 object-contain cursor-pointer hover:scale-110 transition-transform rounded',
      currentEmojiClassIndex === index ? 'bg-blue-100' : ''
    ]"
              @click="selectEmojiClass(index)"
          />
        </template>
      </div>
    </div>
  </div>
</template>