<script setup lang="ts">
import { Smile } from "lucide-vue-next"
import { ref } from "vue"
import EmojiArea from "@/components/EmojiArea.vue"

const tools = [
  {
    toolName: "emoji",
    icon: Smile,
  }
]

const emojiAreaRef = ref<InstanceType<typeof EmojiArea> | null>(null)

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

    <EmojiArea ref="emojiAreaRef" />
  </div>
</template>