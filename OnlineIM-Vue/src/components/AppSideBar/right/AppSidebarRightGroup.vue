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
import { ref } from "vue";
import { Input } from "@/components/ui/input";
import { Search } from "lucide-vue-next";

interface Group {
  groupId: string;
  groupName: string;
  groupAvatar: string;
  lastMessage: string;
  memberCount?: number;
}

const groups: Group[] = [
  {
    groupId: "1",
    groupName: "技术交流群",
    groupAvatar: "/images/group.png",
    lastMessage: "张三: 大家早上好",
    memberCount: 12
  },
  {
    groupId: "2",
    groupName: "项目讨论组",
    groupAvatar: "/images/group.png",
    lastMessage: "李四: 会议纪要已上传",
    memberCount: 8
  }
];

const activeItem = ref<string | null>(null);
const emits = defineEmits(['groupSelected'])

function handleGroupClick(group: Group) {
  activeItem.value = group.groupId;
  emits('groupSelected', group);
}
</script>

<template>
  <Sidebar collapsible="none" style="--sidebar-width: 80%; min-height: 100vh; margin-left: 0 ;border-right: 1px solid #e5e7eb; " class="bg-white">
    <sidebarGroupLabel style="border-bottom: 1px solid #e5e7eb; border-radius: 0" class="p-6.5">
      <div>
        <h2 class="text-lg font-semibold text-gray-800">群组列表</h2>
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
            <SidebarMenuItem v-for="group in groups" :key="group.groupId">
              <SidebarMenuButton
                  as-child
                  :isActive="activeItem === group.groupId"
                  @click="handleGroupClick(group)"
                  class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 cursor-default hover:bg-gray-50 transition-colors"
              >
                <div class="flex items-center w-full">
                  <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                    <img
                        :src="group.groupAvatar"
                        :alt="group.groupName"
                        class="w-full h-full object-cover"
                    />
                  </div>
                  <div class="flex flex-col flex-grow space-y-1">
                    <span class="text-[18px] font-bold">{{ group.groupName }}</span>
                    <span class="text-[13px] text-gray-500">{{ group.lastMessage }}</span>
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