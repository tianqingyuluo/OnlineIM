<script setup lang="ts">
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
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
import ConversationListItem from "@/components/AppSideBar/right/ConversationListItem.vue";
import MessageSearchResults from "@/components/search/MessageSearchResults.vue";
import type { MessageSearchItem } from "@/type/message";
import { searchResultLocation } from "@/utils/message-search";
import { sortByActivity } from "@/stores/conversation-live";


const activeId = ref<string | null>(null);
const emits = defineEmits(['chatSelected'])
const listStore = useListStore()
const searchQuery = ref('')
// 置顶在前，其余按最近活动时间倒序；支持按名称搜索
const conversations = computed(() => {
  const query = searchQuery.value.toLowerCase().trim()
  const all = (listStore.conversations || []).filter(conversation =>
    !query || (conversation.target_info?.name || '').toLowerCase().includes(query))
  return [
    ...sortByActivity(all.filter(conversation => conversation.is_pinned)),
    ...sortByActivity(all.filter(conversation => !conversation.is_pinned)),
  ]
})

// 之前只 emit 事件而父组件没有监听，点击会话不会进入聊天页
function handleChatClick(conversation: Conversation) {
  activeId.value = conversation.conversation_id
  emits('chatSelected', conversation)
  listStore.setConversationFieldsToNull(conversation.conversation_id)
  const isGroup = conversation.type === 'group' || conversation.conversation_id.startsWith('grp_')
  void router.push(`/main/chat/${isGroup ? 'group' : 'private'}/${conversation.conversation_id}`)
}
import { useRoute } from 'vue-router'

/** 点击聊天记录搜索结果：打开所属会话并跳到该消息 */
function handleSearchSelect(item: MessageSearchItem) {
  activeId.value = item.conversation?.conversation_id || item.message.conversation_id
  void router.push(searchResultLocation(item))
}
import UserMainPart from '@/components/MainPart/UserMainPart.vue'
import GroupMainPart from '@/components/MainPart/GroupMainPart.vue'

const route = useRoute()
const chatType = computed(() => {
  if (route.path.includes('/group/')) return 'group'
  if (route.path.includes('/private/')) return 'private'
  return ''
})
const chatId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})
</script>


<template>
  <Sidebar collapsible="none" class="w-full bg-white" style="min-height: 100vh; border-right: 1px solid #e5e7eb;" v-if="!chatType">
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
            <SidebarMenuItem v-for="conversation in conversations" :key="conversation.conversation_id" class="w-full">
              <SidebarMenuButton
                  as-child
                  :isActive="activeId === conversation.conversation_id"
                  @click="handleChatClick(conversation)"
                  class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 hover:bg-gray-50"
              >
                <ConversationListItem :conversation="conversation" />
              </SidebarMenuButton>
            </SidebarMenuItem>
            <div v-if="searchQuery.trim() && !conversations.length" class="flex justify-center py-3 text-sm text-gray-500">
              没有找到对应会话
            </div>
            <MessageSearchResults :query="searchQuery" show-conversation @select="handleSearchSelect" />
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
  </Sidebar>
  <UserMainPart
      v-if="chatType === 'private'"
      :key="`private-${chatId}`"
      class="chat-content w-full"
  />
  <GroupMainPart
      v-else-if="chatType === 'group'"
      :key="`group-${chatId}`"
      class="chat-content w-full"
  />
</template>