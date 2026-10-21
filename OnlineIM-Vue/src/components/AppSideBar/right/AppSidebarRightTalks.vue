<script setup lang="ts">
import { useUserStore } from '@/stores/user'
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
import { useRoute } from "vue-router";
import ConversationListItem from "@/components/AppSideBar/right/ConversationListItem.vue";
import MessageSearchResults from "@/components/search/MessageSearchResults.vue";
import type { MessageSearchItem } from "@/type/message";
import { searchResultLocation } from "@/utils/message-search";
import { sortByActivity } from "@/stores/conversation-live";


const route = useRoute();
const selectedId = ref<string | null>(null);
// 高亮跟随当前路由，直接打开聊天地址时也能正确选中
const activeId = computed(() => {
  const id = route.params.id;
  return (Array.isArray(id) ? id[0] : id) || selectedId.value;
});
const searchQuery = ref('');
const emits = defineEmits(['chatSelected'])
const listStore = useListStore()


const pinnedConversations = computed(() => {
  const query = searchQuery.value.toLowerCase().trim();
  const conversations = sortByActivity((listStore.conversations || []).filter(conversation => conversation.is_pinned));
  if (!query) return conversations;
  return conversations.filter(conversation => {
    const name = (conversation.target_info?.name || '').toLowerCase();
    return name.includes(query);
  });
});

const unpinnedConversations = computed(() => {
  const query = searchQuery.value.toLowerCase().trim();
  const conversations = sortByActivity((listStore.conversations || []).filter(conversation => !conversation.is_pinned));
  if (!query) return conversations;
  return conversations.filter(conversation => {
    const name = (conversation.target_info?.name || '').toLowerCase();
    return name.includes(query);
  });
});


const hasConversations = computed(() => pinnedConversations.value.length > 0 || unpinnedConversations.value.length > 0);

function handleChatClick(conversation: Conversation) {
  selectedId.value = conversation.conversation_id;
  emits('chatSelected', conversation);
  listStore.setConversationFieldsToNull(conversation.conversation_id);
  if (conversation.type=='private')
    router.push(`/main/chat/private/${conversation.conversation_id}`);
  else
    router.push(`/main/chat/group/${conversation.conversation_id}`);
}

/** 点击聊天记录搜索结果：打开所属会话并跳到该消息 */
function handleSearchSelect(item: MessageSearchItem) {
  const location = searchResultLocation(item);
  selectedId.value = item.conversation?.conversation_id || item.message.conversation_id;
  void router.push(location);
}

/** 会话预览里区分“你撤回了一条消息” */
const currentUserId = computed(() => useUserStore().loggedInUser?.user_id)
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
            placeholder="搜索会话或聊天记录"
            data-testid="global-search-input"
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
                  <ConversationListItem :conversation="conversation" :current-user-id="currentUserId" />
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
                  <ConversationListItem :conversation="conversation" :current-user-id="currentUserId" />
                </SidebarMenuButton>
              </SidebarMenuItem>
            </template>

            <div v-if="!hasConversations" :class="['flex justify-center items-center text-gray-500', searchQuery.trim() ? 'py-3 text-sm' : 'py-8']">
              没有找到对应会话
            </div>
            <MessageSearchResults :query="searchQuery" show-conversation @select="handleSearchSelect" />
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
  </Sidebar>
</template>
