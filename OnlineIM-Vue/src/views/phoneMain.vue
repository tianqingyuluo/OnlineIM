<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

// 这里可以添加移动端特有的逻辑和UI组件
</script>

<template>
  <div class="phone-main">
    <!-- 移动端布局内容 -->
     111
  </div>
</template>

<style scoped>
.phone-main {
  width: 100%;
  height: 100%;
}
</style>