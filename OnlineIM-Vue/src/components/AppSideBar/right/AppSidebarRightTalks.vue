<script setup lang="ts">
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent, SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem
} from "@/components/ui/sidebar"
import {computed, ref} from "vue";
import {Input} from "@/components/ui/input";
import {Search} from "lucide-vue-next";
import {useListStore} from '@/stores/list.ts'
import {type Conversation} from "@/type/Conversation.ts";
import router from "@/router";


const activeId = ref<string | null>(null);
const searchQuery = ref('');
const emits = defineEmits(['chatSelected'])
const listStore = useListStore()


const pinnedConversations = computed(() => {
  const query = searchQuery.value.toLowerCase().trim();
  const conversations = (listStore.conversations || []).filter(conversation => conversation.is_pinned);
  if (!query) return conversations;
  return conversations.filter(conversation => {
    const name = (conversation.target_info?.name || '').toLowerCase();
    return name.includes(query);
  });
});

const unpinnedConversations = computed(() => {
  const query = searchQuery.value.toLowerCase().trim();
  const conversations = (listStore.conversations || []).filter(conversation => !conversation.is_pinned);
  if (!query) return conversations;
  return conversations.filter(conversation => {
    const name = (conversation.target_info?.name || '').toLowerCase();
    return name.includes(query);
  });
});


const hasConversations = computed(() => pinnedConversations.value.length > 0 || unpinnedConversations.value.length > 0);

function handleChatClick(conversation: Conversation) {
  activeId.value = conversation.conversation_id
  emits('chatSelected', conversation)
  if (conversation.type=='private')
    router.push(`/main/chat/private/${conversation.conversation_id}`)
  else
    router.push(`/main/chat/group/${conversation.conversation_id}`)
}
</script>

<template>
  <Sidebar collapsible="none" class="w-full bg-white" style="min-height: 100vh; border-right: 1px solid #e5e7eb;">
    <sidebarGroupLabel class=" border-b border-gray-200 rounded-none" style="height: 60px">
      <div class="w-full ">
        <h2 class="text-lg font-semibold text-gray-800">聊天</h2>
      </div>
    </sidebarGroupLabel>
    <sidebarGroupLabel class="py-8 px-0 rounded-none flex justify-center items-center">
      <div class="relative w-[90%]">
        <Input
            id="search"
            type="text"
            placeholder="搜索"
            v-model="searchQuery"
            class="w-full pl-10 bg-white border-blue-100 focus:border-blue-100 focus:ring-0"
        />
        <Search class="absolute left-3 top-1/2 transform -translate-y-1/2 size-6 text-muted-foreground" />
      </div>
    </sidebarGroupLabel>
    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu class="w-full">
            <!-- 置顶会话 -->
            <template v-if="pinnedConversations.length > 0">
              <div class="px-4 py-2 text-xs font-semibold text-gray-500 uppercase">置顶</div>
              <div class="bg-gray-50">
              <SidebarMenuItem v-for="conversation in pinnedConversations" :key="conversation.conversation_id" class="w-full">
                <SidebarMenuButton
                  as-child
                  :isActive="activeId === conversation.conversation_id"
                  @click="handleChatClick(conversation)"
                  class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 hover:bg-gray-50"
                >
                  <div class="flex items-center w-full">
                    <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                      <img
                        :src="conversation.target_info?.avatar_url || '/default-avatar.png'"
                        :alt="conversation.target_info?.name || ''"
                        class="w-full h-full object-cover"
                      />
                    </div>
                    <div class="flex flex-col flex-grow space-y-1">
                      <span class="text-[18px] font-bold">
                        {{ conversation.target_info?.name || 'Unknown' }}
                      </span>
                      <span class="text-[13px] text-gray-500 truncate">
                        {{ conversation.last_message?.content_preview || '无消息' }}
                      </span>
                    </div>
                  </div>
                </SidebarMenuButton>
              </SidebarMenuItem>
              </div>
            </template>

            <!-- 非置顶会话 -->
            <template v-if="unpinnedConversations.length > 0">
              <div v-if="pinnedConversations.length > 0" class="px-4 py-2 text-xs font-semibold text-gray-500 uppercase">其他</div>
              <SidebarMenuItem v-for="conversation in unpinnedConversations" :key="conversation.conversation_id" class="w-full">
                <SidebarMenuButton
                  as-child
                  :isActive="activeId === conversation.conversation_id"
                  @click="handleChatClick(conversation)"
                  class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 hover:bg-gray-50"
                >
                  <div class="flex items-center w-full">
                    <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                      <img
                        :src="conversation.target_info?.avatar_url || '/default-avatar.png'"
                        :alt="conversation.target_info?.name || ''"
                        class="w-full h-full object-cover"
                      />
                    </div>
                    <div class="flex flex-col flex-grow space-y-1">
                      <span class="text-[18px] font-bold">
                        {{ conversation.target_info?.name || 'Unknown' }}
                      </span>
                      <span class="text-[13px] text-gray-500 truncate">
                        {{ conversation.last_message?.content_preview || '无消息' }}
                      </span>
                    </div>
                  </div>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </template>

            <div v-if="!hasConversations" class="flex justify-center items-center py-8 text-gray-500">
              没有找到对应会话
            </div>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
  </Sidebar>
</template>
