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
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { ref, computed, onMounted } from "vue";
import { Input } from "@/components/ui/input";
import { Search, ChevronDown, ChevronRight } from "lucide-vue-next";
import { useListStore } from '@/stores/list.ts'
import { useRouter } from 'vue-router'
import type { Friend } from '@/type/Friends.ts'
import { groupAndSortFriends } from '@/utils/friendGroupUtils'
import AddGroupDialog from "@/components/AddGroupDialog.vue";
import EditGroupDialog from "@/components/EditGroupDialog.vue";
import BlacklistSidebar from "@/components/BlacklistSidebar.vue";
const router = useRouter()
const activeItem = ref<string | null>(null);
const expandedGroups = ref<Record<string, boolean>>({});
const isGroupMenuOpen = ref(false);
const currentGroupId = ref<string | null>(null);
const groupLabelRef = ref<HTMLDivElement | null>(null);
const showAddGroupDialog = ref(false);
const showEditGroupDialog = ref(false);

// 初始化所有分组为展开状态
onMounted(() => {
  listStore.userGroups.forEach(group => {
    expandedGroups.value[group.id] = true;
  });
});

function toggleGroup(groupId: string) {
  expandedGroups.value[groupId] = !expandedGroups.value[groupId];
}

// 分组右键菜单项
const groupMenuItems = [
  {
    title: "添加分组",
    action: () => {
      showAddGroupDialog.value = true;
    }
  },
  {
    title: "更改分组名称",
    action: () => {
      if (currentGroupId.value) {
        showEditGroupDialog.value = true; // 只有点击时才显示
      }
    }
  },
  {
    title: "删除分组",
    action: async () => {
      if (!currentGroupId.value) return;
      try {
        await listStore.deleteFriendGroup(currentGroupId.value);
      } catch (error) {
        console.error('删除分组失败:', error);
      }
    }
  },
];

// 处理分组右键菜单打开状态变化
const handleGroupMenuOpenChange = (open: boolean, groupId: string) => {
  isGroupMenuOpen.value = open;
  currentGroupId.value = groupId;
}

function isGroupExpanded(groupId: string) {
  return expandedGroups.value[groupId] ?? true;
}

const emits = defineEmits(['userSelected'])
const listStore = useListStore()

const groupedFriends = computed(() => {
  return listStore.groupedFriends.length > 0 
    ? listStore.groupedFriends 
    : groupAndSortFriends(listStore.friends, listStore.userGroups)
})

function handleUserClick(user: Friend) {
  activeItem.value = user.friend_info.user_id;
  emits('userSelected', user);
  router.push(`/main/friends/${user.friend_info.user_id}`);
}
</script>

<template>
  <Sidebar
      collapsible="none"
      style=" min-height: 100vh; margin-left: 0; border-right: 1px solid #e5e7eb; "
      class="bg-white transition-all duration-300 w-full"
  >
    <SidebarGroupLabel
        style="border-bottom: 1px solid #e5e7eb; border-radius: 0; height: 60px"
    >
      <div>
        <h2 class="text-lg font-semibold text-gray-800">好友列表</h2>
      </div>
    </SidebarGroupLabel>

    <SidebarGroupLabel
        style="padding: 30px 0; display: flex; justify-content: center; align-items: center;"
    >
      <div class="relative w-[90%]" style="margin-top:5px">
        <Input
            id="search"
            type="text"
            placeholder="搜索"
            class="w-full pl-10 bg-white border-blue-100 focus:border-blue-100 focus:ring-0"
        />
        <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 size-6 text-muted-foreground" />
      </div>
    </SidebarGroupLabel>

    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu>
            <template v-for="group in groupedFriends" :key="group.group.id">
              <div class="group-container">
                <ContextMenu class="w-full" v-model:open="isGroupMenuOpen" @update:open="(open) => handleGroupMenuOpenChange(open, group.group.id)">
                  <ContextMenuTrigger class="w-full">
                    <SidebarGroupLabel
                        ref="groupLabelRef"
                        @click="toggleGroup(group.group.id)"
                        class="cursor-pointer flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors font-bold "
                        :class="[isGroupMenuOpen && currentGroupId === group.group.id ? 'bg-gray-100' : ''] "
                        style="font-size: 15px"
                    >
                      {{ group.group.name }} ({{ group.friends.length }})
                      <ChevronDown
                          v-if="isGroupExpanded(group.group.id)"
                          class="w-5 h-5 transition-transform duration-200"
                      />
                      <ChevronRight
                          v-else
                          class="w-5 h-5 transition-transform duration-200"
                      />
                    </SidebarGroupLabel>
                  </ContextMenuTrigger>
                  <ContextMenuContent class="w-48">
                    <ContextMenuItem 
                      v-for="item in groupMenuItems" 
                      :key="item.title"
                      @click="item.action"
                    >
                      {{ item.title }}
                    </ContextMenuItem>
                  </ContextMenuContent>
                </ContextMenu>

                <transition
                    name="slide"
                    @enter="el => el.style.height = el.scrollHeight + 'px'"
                    @after-enter="el => el.style.height = null"
                    @before-leave="el => el.style.height = el.scrollHeight + 'px'"
                    @leave="el => el.style.height = 0"
                >
                  <div v-show="isGroupExpanded(group.group.id)" class="transition-all duration-300">
                    <SidebarMenuItem
                        v-for="friend in group.friends"
                        :key="friend.friend_info.user_id"
                    >
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
                            <span class="text-[18px] font-bold">{{ friend.friend_info.remark || friend.friend_info.nickname }}</span>
                            <span class="text-[13px] text-gray-500">
                              {{ friend.friend_info.online_status === 'online' ? '在线' : '离线' }}
                            </span>
                          </div>
                        </div>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  </div>
                </transition>
              </div>
            </template>
          </SidebarMenu>
          <BlacklistSidebar />
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>

  </Sidebar>
  <AddGroupDialog 
    :show="showAddGroupDialog"
    @update:show="(val:boolean) => showAddGroupDialog = val"
  />
  <EditGroupDialog
    :show="showEditGroupDialog"
    @close="() => showEditGroupDialog = false"
    v-if="currentGroupId"
    :groupId="currentGroupId"
  />
</template>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: height 0.3s ease-in-out;
  overflow: hidden;
}

.group-container {
  transition: all 0.3s ease;
}

.chevron-rotate-enter-active,
.chevron-rotate-leave-active {
  transition: transform 0.3s ease;
}

.chevron-rotate-enter-from,
.chevron-rotate-leave-to {
  transform: rotate(-90deg);
}
</style>