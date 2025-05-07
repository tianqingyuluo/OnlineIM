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
import { useUserStore } from '@/stores/user'

interface Friend {
  userName: string;
  arg: string;
  lastTalk: string;
  TalkingList?: Array<{
    messageId: number;
    message: string;
    sender: string;
    sendTo: string;
    sendTime: string;
  }>;
}

interface Group {
  groupId: string;
  groupName: string;
  groupAvatar: string;
  lastMessage: string;
  MessageList?: Array<{
    messageId: number;
    message: string;
    sender: string;
    sendTime: string;
  }>;
}

type ChatItem = Friend | Group;

const usersAndGroups: ChatItem[] = [
  {
    userName: "好友1",
    arg: "/images/help.png",
    lastTalk: "111,吃了吗",
    TalkingList: [
      {
        messageId: 1,
        message: "你好，这是一条示例消息...你好，这是一条示例消息你好，这是一条示例消息" +
            "你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息",
        sender: "默认用户",
        sendTo: "我",
        sendTime: "2023-01-01 12:00"
      },
      {
        messageId: 2,
        message: "你好，这是我的回复",
        sender: "我",
        sendTo: "默认用户",
        sendTime: "2023-01-02 12:05"
      }
    ]
  },
  {
    userName: "好友2",
    arg: "/images/help.png",
    lastTalk: "222,吃了"
  },
  {
    groupId: "1",
    groupName: "群聊1",
    groupAvatar: "/images/group.png",
    lastMessage: "群成员1: 大家好",
    MessageList: [
      {
        messageId: 1,
        message: "大家好，这是一条群消息大家好，这是一条群消息大家好，这是一条群消息大家好，这是一条群消息大家好，" +
            "这是一条群消息大家好，这是一条群消息大家好，这是一条群消息大家好，这是一条群消息大家好，这是一条群消息",
        sender: "群成员1",
        sendTime: "2023-01-01 12:00"
      },
      {
        messageId: 2,
        message: "你好，这是我的回复",
        sender: "我",
        sendTime: "2023-01-02 12:05"
      }
    ]
  },
];

const activeItem = ref<ChatItem | null>(null);
const activeId = ref<string | null>(null);
const emits = defineEmits(['chatSelected'])
const userStore = useUserStore()

function handleChatClick(item: ChatItem) {
  const itemId = isFriend(item) ? item.userName : item.groupId;
  activeId.value = itemId;
  activeItem.value = item;
  emits('chatSelected', item);
}

function isFriend(item: ChatItem): item is Friend {
  return 'userName' in item;
}

function isActive(item: ChatItem): boolean {
  const itemId = isFriend(item) ? item.userName : item.groupId;
  return activeId.value === itemId;
}
</script>

<template>
  <Sidebar collapsible="none" style="--sidebar-width: 80%; min-height: 100vh; margin-left: 0 ;border-right: 1px solid #e5e7eb; " class="bg-white">
    <sidebarGroupLabel style="border-bottom: 1px solid #e5e7eb; border-radius: 0" class="p-7.5">
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
            <SidebarMenuItem v-for="item in usersAndGroups" :key="isFriend(item) ? item.userName : item.groupId">
              <SidebarMenuButton
                  as-child
                  :isActive="isActive(item)"
                  @click="handleChatClick(item)"
                  class="data-[active=true]:bg-gray-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 cursor-default hover:bg-gray-50 transition-colors"
              >
                <div class="flex items-center w-full">
                  <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                    <img
                        :src="isFriend(item) ? item.arg : item.groupAvatar"
                        :alt="isFriend(item) ? item.userName : item.groupName"
                        class="w-full h-full object-cover"
                    />
                  </div>
                  <div class="flex flex-col flex-grow space-y-1">
                    <span class="text-[18px] font-bold">
                      {{ isFriend(item) ? item.userName : item.groupName }}
                      <span v-if="!isFriend(item)" class="ml-2 text-xs text-gray-500"></span>
                    </span>
                    <span class="text-[13px] text-gray-500 truncate">
                      {{ isFriend(item) ? item.lastTalk : item.lastMessage }}
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