<script setup lang="ts">
import { SidebarProvider } from "@/components/ui/sidebar";
import AppSidebarLeft from "@/components/AppSidebarLeft.vue";
import AppSidebarRightUser from "@/components/AppSidebarRightUser.vue";
import UserMainPart from "@/components/UserMainPart.vue";
import GroupMainPart from "@/components/GroupMainPart.vue";
import { ref, provide } from "vue";
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const currentView = ref('chat')

// 提供修改视图的函数
function setCurrentView(view) {
  currentView.value = view;
}

// 提供 currentView 和 setCurrentView
provide('currentView', currentView);
provide('setCurrentView', setCurrentView);

function handleChatSelected(chat) {
  userStore.setSelectedUser(chat)
}
</script>

<template>
  <div class="flex h-screen">
    <SidebarProvider style="min-width: 15% ;">
      <AppSidebarLeft />
      <AppSidebarRightUser
          @chatSelected="handleChatSelected"
          :defaultSelected="true"
          class="ml-[var(--sidebar-width)]"
      />
    </SidebarProvider>
    <div class="flex-1">
      <template v-if="userStore.selectedUser">
        <UserMainPart v-if="'userName' in userStore.selectedUser" />
        <GroupMainPart v-else />
      </template>
      <div v-else class="flex items-center justify-center h-full">
        <span class="text-gray-500">请从左侧选择一个会话</span>
      </div>
    </div>
  </div>
</template>