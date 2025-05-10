<script setup lang="ts">
import { Smile } from "lucide-vue-next"
import { ref } from "vue"
import EmojiArea from "@/components/independent/emojiArea/EmojiArea.vue"

const tools = [
  {
    toolName: "emoji",
    icon: Smile,
  }
]

const emojiAreaRef = ref<InstanceType<typeof EmojiArea> | null>(null)

// 定义emit
const emit = defineEmits(['select'])

function handleEmojiSelect(emoji: string) {
  emit('select', emoji)
}

function toggleEmojiArea(e?: MouseEvent) {
  emojiAreaRef.value?.toggleEmojiArea(e)
}
</script>

<template>
  <!-- 新增工具栏 -->
  <div class="flex items-center justify-between px-4 py-3.5 border-t relative" style="height: 40px">
    <div class="flex space-x-2">
      <template v-for="item in tools" :key="item.toolName">
        <div class="h-[40px] flex items-center justify-center hover:text-blue-400" @click="toggleEmojiArea">
          <component :is="item.icon" style="width: 30px; height: 30px"/>
        </div>
      </template>
    </div>
    <transition name="fade-slide" mode="out-in">
    <EmojiArea ref="emojiAreaRef" @select="handleEmojiSelect" />
    </transition>
  </div>
</template>
<style scoped>
/* 统一过渡效果 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 拖动光标反馈 */
.cursor-move {
  cursor: move;
  user-select: none;
}
</style>