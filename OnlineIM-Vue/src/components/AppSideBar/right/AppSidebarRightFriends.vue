<script setup lang="ts">
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarGroupLabel
} from "@/components/ui/sidebar"
import { ref, computed } from "vue";
import { Input } from "@/components/ui/input";
import { Search } from "lucide-vue-next";
import { useListStore } from '@/stores/list.ts'
import { useRouter } from 'vue-router'
import type { Friend } from '@/type/Friends.ts'

const router = useRouter()
const activeItem = ref<string | null>(null);
const emits = defineEmits(['userSelected'])
const listStore = useListStore()

// 从store获取好友列表
const friends = computed(() => listStore.friends)

function handleUserClick(user: Friend) {
  activeItem.value = user.friend_info.user_id;
  emits('userSelected', user);
  // 跳转到好友详情页
  router.push(`/main/friends/${user.friend_info.user_id}`);
}
</script>

<template>
  <Sidebar collapsible="none" style="--sidebar-width: 80%; min-height: 100vh; margin-left: 0 ;border-right: 1px solid #e5e7eb; " class="bg-white">
    <sidebarGroupLabel style="border-bottom: 1px solid #e5e7eb; border-radius: 0 ;height: 60px" >
      <div>
        <h2 class="text-lg font-semibold text-gray-800">好友列表</h2>
      </div>
    </sidebarGroupLabel>
    <SidebarGroupLabel style="padding: 30px 0; display: flex; justify-content: center; align-items: center;">
      <div class="relative w-[90%]" style="margin-top:5px">
        <Input
            id="search"
            type="text"
            placeholder="搜索"
            class="w-full pl-10 bg-white border-blue-100 focus:border-blue-100 focus:ring-0"
        />
        <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 size-6 text-muted-foreground" />
      </div>
    </sidebarGroupLabel>
    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="friend in friends" :key="friend.friend_info.user_id">
              <SidebarMenuButton
                  as-child
                  :isActive="activeItem === friend.friend_info.user_id"
                  @click="handleUserClick(friend)"
                  class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 cursor-default hover:bg-gray-50 transition-colors"
              >
                <div class="flex items-center w-full">
                  <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                    <img
                        :src="friend.friend_info.avatar_url || '/default-avatar.png'"
                        :alt="friend.friend_info.nickname"
                        class="w-full h-full object-cover"
                    />
                  </div>
                  <div class="flex flex-col flex-grow space-y-1">
                    <span class="text-[18px] font-bold">{{ friend.friend_info.nickname }}</span>
                    <span class="text-[13px] text-gray-500">{{ friend.friend_info.online_status === 'online' ? '在线' : '离线' }}</span>
                  </div>
                </div>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
  </Sidebar>
</template>

<style scoped>

</style>