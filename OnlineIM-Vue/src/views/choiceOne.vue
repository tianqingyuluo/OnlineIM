<template>
  <component :is="currentLayout" />
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import Main from '@/views/Main.vue'
import phoneMain from '@/components/phone/phoneMain.vue'

const currentLayout = ref(window.innerWidth < 600 ? phoneMain : Main)

const handleResize = () => {
  currentLayout.value = window.innerWidth < 600 ? phoneMain : Main
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>