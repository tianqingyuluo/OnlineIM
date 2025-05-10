<script setup lang="ts">
import { SidebarProvider } from "@/components/ui/sidebar";
import AppSidebarLeft from "@/components/AppSideBar/left/AppSidebarLeft.vue";
import AppSidebarRightTalks from "@/components/AppSideBar/right/AppSidebarRightTalks.vue";
import AppSidebarRightFriends from "@/components/AppSideBar/right/AppSidebarRightFriends.vue";
import AppSidebarRightGroup from "@/components/AppSideBar/right/AppSidebarRightGroup.vue";
import UserMainPart from "@/components/MainPart/UserMainPart.vue";
import GroupMainPart from "@/components/MainPart/GroupMainPart.vue";
import {  computed, onMounted} from "vue";
import { useUserStore } from '@/stores/user.ts'
import {useListStore} from "@/stores/list.ts";
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const listStore = useListStore()
const router = useRouter()
const currentRoute = computed(() => router.currentRoute.value)

// 移除原有的currentView计算属性
// 移除setCurrentView方法
// 移除provide相关代码

onMounted(async () => {
  try {
    await userStore.fetchUserData();
    await listStore.fetchUserData();
  } catch (err) {
    console.error('获取用户信息失败:', err);
  }
});
</script>

<template>
  <div class="flex h-screen">
    <SidebarProvider class="w-80">
      <AppSidebarLeft class="sidebar-left" />
      <div class="w-full">
        <AppSidebarRightTalks
          v-if="currentRoute.path === '/main/chat'"
          class="w-full"
        />
        <AppSidebarRightFriends class="w-full"
          v-else-if="currentRoute.path === '/main/friends'"
        />
        <AppSidebarRightGroup class="w-full"
          v-else-if="currentRoute.path === '/main/groups'"
        />
      </div>
    </SidebarProvider>
    <div class="flex-1">
      <UserMainPart/>
      <!-- <GroupMainPart v-else /> -->
    </div>
  </div>
</template>