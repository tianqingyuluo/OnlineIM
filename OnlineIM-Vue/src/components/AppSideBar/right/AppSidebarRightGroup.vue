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
import CreateGroup from "@/components/independent/group/CreateGroup.vue";
import {Button} from "@/components/ui/button";
import { useListStore } from '@/stores/list'

const listStore = useListStore()
const activeItem = ref<string | null>(null);
const emits = defineEmits(['groupSelected'])

function handleGroupClick(group: any) {
  activeItem.value = group.group_id;
  emits('groupSelected', group);
}

const showCreateGroup = ref(false);

function handleCreateGroup() {
  showCreateGroup.value = true;
}
</script>

<template>
  <Sidebar collapsible="none" style="--sidebar-width: 80%; min-height: 100vh; margin-left: 0 ;border-right: 1px solid #e5e7eb; " class="bg-white">
    <sidebarGroupLabel style="border-bottom: 1px solid #e5e7eb; border-radius: 0 ;height: 60px" class="flax" >
      <div class="flex justify-between items-center">
        <h2 class="text-lg font-semibold text-gray-800">群组列表</h2>
      </div>
      <Button
          @click="handleCreateGroup"
          class="p-2 text-sm bg-gray-100 text-gray-800 rounded-md hover:bg-gray-100 transition-colors flex items-center justify-center border border-gray-200 ml-auto"
      >
        <span class="text-xl">+</span>
      </Button>
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
            <SidebarMenuItem v-for="group in listStore.groups" :key="group.group_id">
              <SidebarMenuButton
                  as-child
                  :isActive="activeItem === group.group_id"
                  @click="handleGroupClick(group)"
                  class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 cursor-default hover:bg-gray-50 transition-colors"
              >
                <div class="flex items-center w-full">
                  <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                    <img
                        :src="group.avatar_url || '/images/group.png'"
                        :alt="group.name"
                        class="w-full h-full object-cover"
                    />
                  </div>
                  <div class="flex flex-col flex-grow space-y-1">
                    <span class="text-[18px] font-bold">{{ group.name }}</span>
                    <span class="text-[13px] text-gray-500">{{ group.announcement || '暂无公告' }}</span>
                  </div>
                </div>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
    <div v-if="showCreateGroup" class="fixed inset-0 bg-white/80 flex items-center justify-center z-[9999]">
      <div class="w-[800px] max-w-[90vw]">
        <CreateGroup @close="showCreateGroup = false" />
      </div>
    </div>
  </Sidebar>
</template>