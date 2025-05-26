<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import {SidebarGroupLabel} from "@/components/ui/sidebar";
import {Input} from "@/components/ui/input";
import {Search} from "lucide-vue-next";
import {Button} from "@/components/ui/button";
import usersSelectResult from './usersSelectResoult.vue'
import FriendRequestsList from "@/components/independent/founding/FriendRequestsList.vue";
import GroupSelectResult from './GroupSelectResult.vue';
import GroupJoinRequestsList from "@/components/independent/founding/GroupJoinRequestsList.vue";
import RedPoint from '@/components/common/redPoint.vue'; // 引入红点组件
import { useNotificationStore } from '@/stores/notificationStore'; // 引入 notificationStore

const notificationStore = useNotificationStore(); // 使用 store

// 拖动逻辑
const modalRef = ref<HTMLElement | null>(null);
let isDragging = false;
let initialX = 0;
let initialY = 0;
let currentX = 0;
let currentY = 0;

const startDrag = (e: MouseEvent) => {
  if (!modalRef.value) return;
  isDragging = true;
  initialX = e.clientX;
  initialY = e.clientY;
  document.addEventListener('mousemove', drag);
  document.addEventListener('mouseup', stopDrag);
};

const drag = (e: MouseEvent) => {
  if (!isDragging || !modalRef.value) return;
  e.preventDefault();
  currentX = e.clientX - initialX;
  currentY = e.clientY - initialY;
  initialX = e.clientX;
  initialY = e.clientY;
  const modal = modalRef.value;
  modal.style.top = `${modal.offsetTop + currentY}px`;
  modal.style.left = `${modal.offsetLeft + currentX}px`;
};

const stopDrag = () => {
  isDragging = false;
  document.removeEventListener('mousemove', drag);
  document.removeEventListener('mouseup', stopDrag);
};

// 菜单选项
const activeTab = ref('好友');
const tabs = ['好友', '群聊', '好友请求', '群组请求'];
const searchQuery = ref('');

// 添加对activeTab的监听
watch(activeTab, (newTab) => {
  console.log('标签切换至:', newTab)
  if (searchQuery.value && (newTab === '好友' || newTab === '群聊')) {
    // 使用更可靠的方式触发watch
    const temp = searchQuery.value
    searchQuery.value = ''
    nextTick(() => {
      searchQuery.value = temp
    })
  }
  // 添加逻辑：当切换到好友请求或群组请求标签页时，清除对应的红点状态
  if (newTab === '好友请求') {
    notificationStore.readedfriend();
  } else if (newTab === '群组请求') {
    notificationStore.readedgroup();
  }
})

// 添加emit定义
const emit = defineEmits(['close']);

const handleClose = () => {
  // 在这里添加其他逻辑
  console.log('关闭按钮被点击');
  emit('close');
  
  // 可以添加更多操作
  searchQuery.value = ''; // 清空搜索框
  activeTab.value = '好友'; // 重置标签页
};
</script>

<template>
  <div ref="modalRef" class="fixed z-50 top-[10%] left-[30%] w-[80%] max-w-2xl ">
    <div class="bg-white shadow-lg w-full rounded-lg">
      <!-- 顶部栏 -->
      <div
        class="flex items-center justify-between h-12 w-full bg-gray-100 rounded-t-lg px-6 cursor-move pr-0"
        @mousedown="startDrag"
      >
        <h3 class="text-lg font-medium text-gray-900">搜索</h3>
        <div class="mr-0">
          <Button
            @click="handleClose"
            class="text-gray-500 bg-gray-100 text-3xl hover:text-gray-700 transition-transform duration-200 hover:bg-white border-none shadow-none"
          >
            ×
          </Button>
        </div>
      </div>

      <!-- 搜索栏 -->
      <SidebarGroupLabel style="padding: 30px 0; display: flex; justify-content: center; align-items: center;">
        <div class="relative w-[95%]" style="margin-top:5px">
          <Input
              id="search"
              type="text"
              placeholder="搜索"
              class="w-full pl-10 bg-gray-100 border-blue-100 focus:border-blue-100 focus:ring-0"
              v-model="searchQuery"
          />
          <Search 
            class="absolute left-3 top-1/2 transform -translate-y-1/2 size-6 text-muted-foreground bg-white rounded-full p-1 hover:scale-[1.25] transition-transform duration-200"
 
          />
        </div>
      </SidebarGroupLabel>

      <!-- 横向菜单 -->
      <div class="flex border-b">
        <button
          v-for="tab in tabs"
          :key="tab"
          @click="activeTab = tab"
          class="px-6 py-3 text-center relative" 
          :class="{ 'border-b-2 border-black': activeTab === tab }"
        >
          {{ tab }}
          <RedPoint v-if="tab === '好友请求' && notificationStore.hasnewfriend" class="absolute top-2 right-2"/> <!-- 好友请求红点 -->
          <RedPoint v-if="tab === '群组请求' && notificationStore.hasnewgroup" class="absolute top-2 right-2"/> <!-- 群组请求红点 -->
        </button>
      </div>

      <!-- 内容区域 -->
      <div class="p-6 bg-white  overflow-auto h-[600px] rounded-b-lg">
        <template v-if="activeTab === '好友'">
          <usersSelectResult :keyword="searchQuery" class="h-full"/>
        </template>
        <template v-else-if="activeTab ==='群聊'">
          <GroupSelectResult :keyword="searchQuery" class="h-full"/>
        </template>
        <template v-else-if="activeTab ==='好友请求'">
          <FriendRequestsList/>
        </template>
        <template v-else-if="activeTab ==='群组请求'">
          <GroupJoinRequestsList/>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 拖动光标反馈 */
.cursor-move {
  cursor: move;
  user-select: none;
}


</style>