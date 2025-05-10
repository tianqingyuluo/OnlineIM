<script setup lang="ts">
import {searchService} from '@/services/user.service.ts'
import { ref, watch } from 'vue'
import OtherProfile from "@/components/independent/profile/otherProfile.vue";
import { type UserSearchResult } from '@/type/User.ts';

const props = defineProps({
  keyword: {
    type: String,
    required: true
  }
})

let searchResults = ref([] as UserSearchResult[])
const showProfile = ref(false)
const selectedUserId = ref('')

// 监听keyword变化
watch(() => props.keyword, async (newKeyword) => {
  if (newKeyword) {
    await searchUsers(newKeyword)
  } else {
    searchResults.value = [] // 添加.value
  }
})

async function handleUserClick(userId: string) {
  try {
    selectedUserId.value = userId
    showProfile.value = true
  } catch (error) {
    console.error('打开用户资料失败:', error)
  }
}

async function searchUsers(keyword: string) {
  try {
    const response = await searchService.searchUsers(keyword)
    searchResults.value = response.users
  } catch (error) {
    console.error('搜索用户失败:', error)
  }
}
</script>

<template>
  <div class="flex flex-col space-y-2 p-2">
    <template v-if="searchResults.length > 0">
      <div 
        v-for="result in searchResults" 
        :key="result.user_id"
        class="flex items-center p-2 hover:bg-gray-100 rounded cursor-pointer"
        @click="handleUserClick(result.user_id)"
      >
        <img 
          :src="result.avatar_url || '/images/default-avatar.png'" 
          :alt="result.nickname"
          class="w-8 h-8 rounded-full mr-2"
        >
        <div class="flex flex-col">
          <span class="text-sm font-medium">{{ result.nickname }}</span>
          <span class="text-xs text-gray-500" v-if="result.username">{{ result.username }}</span>
        </div>
      </div>
    </template>
    <template v-else>
      <div class="text-center py-8 text-gray-500">
        没有找到匹配的用户
      </div>
    </template>
  </div>

  <div v-if="showProfile" class="fixed inset-0 bg-white/80 flex items-center justify-center z-[9999]">
    <OtherProfile @close="showProfile = false" :userId="selectedUserId" />
  </div>
</template>

<style scoped>
/* 可根据需要添加自定义样式 */
</style>