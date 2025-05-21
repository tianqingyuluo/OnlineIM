<script setup lang="ts">
import { ref } from 'vue';
import { Switch } from "@/components/ui/switch";

const settings = ref({
  isPinned: false,
  isMuted: false,
  isBlocked: false
});
</script>

<template>
  <div class="p-4 space-y-4 ">
    <div class="flex items-center justify-between py-2 border-b border-gray-100">
      <span class="text-gray-500">设为置顶</span>
      <Switch v-model="settings.isPinned" />
    </div>
    <div class="flex items-center justify-between py-2 border-b border-gray-100">
      <span class="text-gray-500">消息免打扰</span>
      <Switch v-model="settings.isMuted" />
    </div>
    <div class="flex items-center justify-between py-2 border-b border-gray-100">
      <span class="text-gray-500">屏蔽此人</span>
      <Switch v-model="settings.isBlocked" />
    </div>
    <button 
      class="w-full py-2 mt-4 text-red-500 bg-white border border-red-500 rounded-md hover:bg-red-50"
    >
      删除好友
    </button>
  </div>
</template>

<style scoped>
/* 可根据需要添加样式 */
</style>