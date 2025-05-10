<script setup lang="ts">
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem
} from "@/components/ui/sidebar"
import {computed, ref} from "vue";
import {Input} from "@/components/ui/input";
import {Search} from "lucide-vue-next";
import {useListStore} from '@/stores/list.ts'
import {type Conversation} from "@/type/Conversation.ts";


const activeId = ref<string | null>(null);
const emits = defineEmits(['chatSelected'])
const listStore = useListStore()
const conversations = computed(() => listStore.conversations)

function handleChatClick(conversation: Conversation) {
  activeId.value = conversation.conversation_id
  emits('chatSelected', conversation)
}
</script>

<template>
  <Sidebar collapsible="none" style="--sidebar-width: 80%; min-height: 100vh; margin-left: 0 ;border-right: 1px solid #e5e7eb; " class="bg-white">
    <sidebarGroupLabel style="border-bottom: 1px solid #e5e7eb; border-radius: 0" class="p-6.5">
      <div >
        <h2 class="text-lg font-semibold text-gray-800">聊天</h2>
      </div>
    </sidebarGroupLabel>
    <SidebarGroupLabel style="
  padding: 30px 0; /* 上下30px，左右0 */
  border-radius: 0;
  display: flex; /* 启用 Flex 布局 */
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中 */
">
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
            <SidebarMenuItem v-for="conversation in conversations" :key="conversation.conversation_id">
              <SidebarMenuButton
                as-child
                :isActive="activeId === conversation.conversation_id"
                @click="handleChatClick(conversation)"
                class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 cursor-default hover:bg-gray-50 transition-colors"
              >
                <div class="flex items-center w-full">
                  <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                    <img
                      :src="conversation.target_info?.avatar_url || '/default-avatar.png'"
                      :alt="conversation.target_info.name"
                      class="w-full h-full object-cover"
                    />
                  </div>
                  <div class="flex flex-col flex-grow space-y-1">
                    <span class="text-[18px] font-bold">
                      {{ conversation.target_info.name }}
                    </span>
                    <span class="text-[13px] text-gray-500 truncate">
                      {{ conversation.last_message.content_preview }}
                    </span>
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