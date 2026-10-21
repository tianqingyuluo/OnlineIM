<script setup lang="ts">
// 删除inject相关代码
// import { inject, type Ref } from 'vue'
// const messageInputRef = inject<Ref<HTMLTextAreaElement>>('messageInputRef')

// 定义emit
const emit = defineEmits(['select'])

const emojiList = [
  '😀','😃','😄','😁','😆','😅','😂','🤣','🥲','🥹',
  '😊','😇','🙂','🙃','😉','😌','😍','🥰','😘','😗',
  '😙','😚','😋','😛','😝','😜','🤪','🤨','🧐','🤓',
  '😎','🥸','🤩','🥳','😏','😒','😞','😔','😟','😕'
]

function handleSelect(emoji: string) {
  emit('select', emoji)
}
</script>

<template>
  <div class="h-full pr-2 overflow-y-auto" data-testid="unicode-emoji-area">
    <div class="grid grid-cols-[repeat(auto-fill,minmax(44px,1fr))] gap-y-3 gap-x-2 p-1 sm:p-2">
      <span
        v-for="(emoji, index) in emojiList"
        :key="index"
        class="text-3xl sm:text-4xl text-center cursor-pointer hover:scale-110 transition-transform"
        @click="handleSelect(emoji)"
      >{{ emoji }}</span>
    </div>
  </div>
</template>