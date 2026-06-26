<template>
  <component :is="currentLayout" />
</template>

<script setup lang="ts">
import { markRaw, shallowRef, onMounted, onBeforeUnmount } from 'vue'
import Main from '@/views/Main.vue'
import phoneMain from '@/components/phone/phoneMain.vue'

const desktopLayout = markRaw(Main)
const mobileLayout = markRaw(phoneMain)
const currentLayout = shallowRef(window.innerWidth < 600 ? mobileLayout : desktopLayout)

const handleResize = () => {
  currentLayout.value = window.innerWidth < 600 ? mobileLayout : desktopLayout
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>
