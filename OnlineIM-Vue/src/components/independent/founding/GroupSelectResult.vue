<script setup lang="ts">
import { groupService } from '@/services/group.service.ts'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import GroupProfile from "@/components/independent/profile/GroupProfile.vue"
import { type GroupSearchResult } from '@/type/group.ts'
import { debounce } from 'lodash';
import { useListStore } from '@/stores/list';
import type {UserSearchResult} from "@/type/User.ts";
import SendGroupRequest from "@/components/independent/group/SendGroupRequest.vue";
import SendFriendRequest from "@/components/independent/friends/SendFriendRequest.vue";
import {Button} from "@/components/ui/button"; // 引入 listStore

const listStore = useListStore(); // 使用 listStore

const props = defineProps({
  keyword: {
    type: String,
    required: true
  }
})

const searchResults = ref([] as GroupSearchResult[])
const showGroupProfile = ref(false)
const selectedGroupId = ref('')
const page = ref(0)
const loading = ref(false)
const hasMore = ref(true)



const debouncedSearch = debounce(async (keyword: string) => {
  if (keyword) {
    await searchGroups(keyword);
  } else {
    searchResults.value = [];
  }
}, 500);

watch(() => props.keyword, (newKeyword) => {
  debouncedSearch(newKeyword);
})

async function handleGroupClick(groupId: string) {
  try {
    selectedGroupId.value = groupId
    await groupService.getGroupInfo(groupId)
    showGroupProfile.value = true
  } catch (error) {
    console.error('打开群组资料失败:', error)
  }
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
    const response = await groupService.searchGroups(props.keyword, page.value)
    searchResults.value = [...searchResults.value, ...response.groups]
    hasMore.value = response.total >= 8
  } catch (error) {
    console.error('加载更多群组失败:', error)
  } finally {
    loading.value = false
  }
}

async function searchGroups(keyword: string) {
  page.value = 0
  hasMore.value = true
  try {
    const response = await groupService.searchGroups(keyword, page.value)
    searchResults.value = response.groups
    hasMore.value = response.total >= 8
  } catch (error) {
    console.error('搜索群组失败:', error)
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

// 判断群组状态
const getGroupStatus = (group: GroupSearchResult) => {
  if (listStore.groups.some(g => g.group_id === group.group_id)) {
    return 'joined'; // 已加入
  }
  return 'stranger'; // 未加入
};
const showGroupRequest = ref(false)
const selectedGroup = ref({} as GroupSearchResult)
// 添加群组的占位函数
function handleAddGroup(group: GroupSearchResult) {
  console.log('添加群组:', group.name);
  selectedGroup.value=group
  showGroupRequest.value = true

}

</script>

<template>
  <send-group-request
      v-if="showGroupRequest"
      @close="showGroupRequest = false"
      :group="selectedGroup"
      class="fixed inset-0 m-auto w-1/2 h-1/2 z-[9999]"/>
  <div class="flex flex-col space-y-2 p-2">
    <template v-if="searchResults.length > 0">
      <div 
        v-for="result in searchResults" 
        :key="result.group_id"
        class="flex items-center p-2 hover:bg-gray-100 rounded cursor-pointer"
        @click="handleGroupClick(result.group_id)"
      >
        <img 
          :src="result.avatar_url || '/images/group.png'" 
          :alt="result.name"
          class="w-8 h-8 rounded-full mr-2"
        >
        <div class="flex flex-col">
          <span class="text-sm font-medium">{{ result.name }}</span>
          <span class="text-xs text-gray-500">群描述: {{ result.description }}</span>
        </div>
        
        <!-- 根据群组状态显示不同内容 -->
        <template v-if="getGroupStatus(result) === 'joined'">
          <span class="ml-auto text-sm text-gray-500">已加入</span>
        </template>
        <template v-else>
          <Button 
            class="ml-auto px-3 py-1 text-sm"
            @click.stop="handleAddGroup(result)"
          >
            添加
          </Button>
        </template>

      </div>
      <div v-if="!hasMore" class="text-center py-4 text-gray-500">
        没有更多数据了
      </div>
    </template>
    <template v-else>
      <div class="text-center py-8 text-gray-500">
        没有找到匹配的群组
      </div>
    </template>
  </div>

  <div v-if="showGroupProfile" class="fixed inset-0 bg-white/80 flex items-center justify-center z-[9999]">
    <GroupProfile @close="showGroupProfile = false" :groupId="selectedGroupId" />
  </div>
</template>

<style scoped>
/* 可根据需要添加自定义样式 */
</style>