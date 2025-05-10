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
  console.log('Selected emoji:', emoji)
  emit('select', emoji)
}
</script>

<template>
  <div class="h-[90%] pr-2 overflow-y-auto">
    <div class="grid grid-cols-10 gap-y-4 gap-x-2 p-4">
      <span 
        v-for="(emoji, index) in emojiList"
        :key="index"
        class="text-4xl cursor-pointer hover:scale-110 transition-transform"
        @click="handleSelect(emoji)"
      >{{ emoji }}</span>
    </div>
  </div>
</template>