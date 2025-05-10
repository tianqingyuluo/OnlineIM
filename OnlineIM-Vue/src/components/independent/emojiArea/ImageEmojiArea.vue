<script setup lang="ts">
const emit = defineEmits(['select'])

const emojiList = [
  { emojiName: "emoji1", src: "/images/group.png" },
  { emojiName: "emoji2", src: "/images/help.png" }
]

function handleSelect(emoji: { emojiName: string, src: string }) {
  emit('select', emoji)
}
</script>

<template>
  <div class="grid grid-cols-6 gap-4">
    <template v-for="(emoji, index) in emojiList" :key="index">
      <img
        :src="emoji.src"
        :alt="emoji.emojiName"
        class="w-24 h-24 object-contain cursor-pointer hover:scale-110 transition-transform"
        @click="handleSelect(emoji)"
      />
    </template>
  </div>
</template>