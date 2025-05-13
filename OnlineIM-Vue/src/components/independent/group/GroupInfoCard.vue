<script setup lang="ts">
import type { GroupResponse } from '@/type/group.ts'

const props = defineProps<{
  group: GroupResponse
}>()
</script>

<template>
  <div class="group-info-card bg-white rounded-lg shadow-sm p-4">
    <!-- 群头像和基本信息 -->
    <div class="flex items-center mb-4">
      <img 
        :src="group.avatar_url || '/images/default-group-avatar.png'" 
        class="w-16 h-16 rounded-full mr-4"
        alt="群头像"
      >
      <div>
        <h2 class="text-xl font-semibold">{{ group.name }}</h2>
        <p class="text-gray-500 text-sm">群号: {{ group.group_id }}</p>
      </div>
    </div>

    <!-- 群详情 -->
    <div class="space-y-3">
      <div>
        <span class="text-gray-500">群主:</span>
        <span class="ml-2">{{ group.owner_id }}</span>
      </div>
      <div>
        <span class="text-gray-500">创建时间:</span>
        <span class="ml-2">{{ new Date(group.created_at).toLocaleString() }}</span>
      </div>
      <div>
        <span class="text-gray-500">成员数量:</span>
        <span class="ml-2">{{ group.member_count }}</span>
      </div>
      <div>
        <span class="text-gray-500">我的角色:</span>
        <span class="ml-2">{{ group.my_role }}</span>
      </div>
      <div v-if="group.description">
        <span class="text-gray-500">群描述:</span>
        <p class="mt-1">{{ group.description }}</p>
      </div>
      
      <!-- 预留群公告位置 -->
      <div>
        <span class="text-gray-500">群公告:</span>
        <p class="mt-1 text-gray-400">暂无公告</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.group-info-card {
  max-width: 400px;
}
</style>