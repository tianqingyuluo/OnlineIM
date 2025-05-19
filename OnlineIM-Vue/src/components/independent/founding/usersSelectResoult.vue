<script setup lang="ts">
import {searchService} from '@/services/user.service.ts'
import {onMounted, onUnmounted, ref, watch} from 'vue'
import OtherProfile from "@/components/independent/profile/otherProfile.vue";
import { type UserSearchResult } from '@/type/User.ts';
import {Button} from "@/components/ui/button";
import SendFriendRequest from "@/components/independent/friends/SendFriendRequest.vue";

const props = defineProps({
  keyword: {
    type: String,
    required: true
  }
})

let searchResults = ref([] as UserSearchResult[])
const showProfile = ref(false)
const selectedUserId = ref('')
const showFriendRequest = ref(false)
const selectedFriend = ref({} as UserSearchResult)
const page = ref(0)
const loading = ref(false)
const hasMore = ref(true)

// 监听keyword变化
watch(() => props.keyword, async (newKeyword) => {
  if (newKeyword) {
    await searchUsers(newKeyword)
  } else {
    searchResults.value = [] // 添加.value
  }
})

function handleAddFriend(user: UserSearchResult) {
  selectedFriend.value = user
  showFriendRequest.value = true
}

// 监听滚动事件
function handleScroll() {
  if (loading.value || !hasMore.value) return
  
  const element = document.querySelector('.flex.flex-col.space-y-2.p-2')
  if (element && element.scrollHeight - element.scrollTop <= element.clientHeight + 100) {
    loadMore()
  }
}

async function loadMore() {
  if (!props.keyword || loading.value || !hasMore.value) return
  
  loading.value = true
  page.value += 8
  try {
    const response = await searchService.searchUsers(props.keyword, page.value)
    searchResults.value = [...searchResults.value, ...response.users]
    hasMore.value = response.total >= 8
  } catch (error) {
    console.error('加载更多用户失败:', error)
  } finally {
    loading.value = false
  }
}

async function searchUsers(keyword: string) {
  page.value = 0
  hasMore.value = true
  try {
    const response = await searchService.searchUsers(keyword, page.value)
    searchResults.value = response.users
    hasMore.value = response.total >= 8
  } catch (error) {
    console.error('搜索用户失败:', error)
  }
}

onMounted(() => {
  const container = document.querySelector('.flex.flex-col.space-y-2.p-2')
  if (container) {
    container.addEventListener('scroll', handleScroll)
  }
})

onUnmounted(() => {
  const container = document.querySelector('.flex.flex-col.space-y-2.p-2')
  if (container) {
    container.removeEventListener('scroll', handleScroll)
  }
})

</script>

<template>
  <div class="flex flex-col space-y-2 p-2">
    <template v-if="searchResults.length > 0">
      <div 
        v-for="result in searchResults" 
        :key="result.user_id"
        class="flex items-center p-2 hover:bg-gray-100 rounded cursor-pointer"
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
        <Button 
          class="ml-auto px-3 py-1 text-sm"
          @click.stop="handleAddFriend(result)"
        >
          添加
        </Button>
      </div>
      <div v-if="!hasMore" class="text-center py-4 text-gray-500">
        没有更多数据了
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
  
  <SendFriendRequest 
    v-if="showFriendRequest"
    @close="showFriendRequest = false"
    :user="selectedFriend"
    class="fixed inset-0 m-auto w-1/2 h-1/2 z-[9999]"
  />
</template>

<style scoped>
/* 可根据需要添加自定义样式 */
</style>