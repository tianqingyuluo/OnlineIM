<script setup lang="ts">
import {searchService} from '@/services/user.service.ts'
import {onMounted, onUnmounted, ref, watch} from 'vue'
import OtherProfile from "@/components/independent/profile/otherProfile.vue";
import { type UserSearchResult } from '@/type/User.ts';
import {Button} from "@/components/ui/button";
import SendFriendRequest from "@/components/independent/friends/SendFriendRequest.vue";
import { debounce } from 'lodash';
import { useUserStore } from '@/stores/user'; // 引入 userStore
import { useListStore } from '@/stores/list'; // 引入 listStore

defineOptions({
  inheritAttrs: false
})

const userStore = useUserStore(); // 使用 userStore
const listStore = useListStore(); // 使用 listStore

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


const debouncedSearch = debounce(async (keyword: string) => {
  if (keyword) {
    await searchUsers(keyword);
  } else {
    searchResults.value = [];
  }
}, 500);

watch(() => props.keyword, (newKeyword) => {
  debouncedSearch(newKeyword);
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

// 判断用户状态
const getUserStatus = (user: any) => {
  if (user.user_id === userStore.loggedInUser.user_id) {
    return 'self'; // 当前用户
  }
  if (listStore.friends.some(friend => friend.friend_info.user_id === user.user_id)) {
    return 'friend'; // 已是好友
  }
  const friendRequests = Array.isArray(listStore.FriendRequestsList) ? listStore.FriendRequestsList : []
  if (friendRequests.some(request => request.sender_info.user_id === user.user_id)) {
    return 'pending'; // 待处理请求
  }
  return 'stranger'; // 陌生人
};

</script>

<template>
  <div v-bind="$attrs">
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

          <!-- 根据用户状态显示不同内容 -->
          <template v-if="getUserStatus(result) === 'stranger'">
            <Button
              class="ml-auto px-3 py-1 text-sm"
              @click.stop="handleAddFriend(result)"
            >
              添加
            </Button>
          </template>
          <template v-else-if="getUserStatus(result) === 'friend'">
            <span class="ml-auto text-sm text-gray-500">已添加</span>
          </template>
          <template v-else-if="getUserStatus(result) === 'pending'">
            <span class="ml-auto text-sm text-gray-500">待处理</span>
          </template>
          <!-- 如果是当前用户，不显示任何按钮或文字 -->

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
  </div>
</template>

<style scoped>
/* 可根据需要添加自定义样式 */
</style>
