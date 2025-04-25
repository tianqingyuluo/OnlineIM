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
import {onMounted, ref} from "vue";
import {Input} from "@/components/ui/input";
import { Search } from "lucide-vue-next";

// Menu items.
const users = [
  {
    userName: "好友1",
    arg:"/image/help.png",//默认头像
    lastTalk:"111,吃了吗",
    TalkingList: [
      {
        messageId: 1,
        message: "你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，" +
            "这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息你好，" +
            "这是一条示例消息你好，这是一条示例消息你好，这是一条示例消息",
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
    arg:"/image/help.png",
    lastTalk:"222,吃了"
  }
]

const props = defineProps({
  defaultSelected: {
    type: Boolean,
    default: false
  }
})

const activeItem = ref(null)

onMounted(() => {
  if(props.defaultSelected && users.length > 0) {
    activeItem.value = users[0]
    emits('userSelected', users[0])
  }
})

const emits = defineEmits(['userSelected'])

function handleUserClick(user: any) {
  activeItem.value = user
  emits('userSelected', user)
}
</script>

<template>
  <Sidebar collapsible="none" style="--sidebar-width: 80%; min-height: 100vh; margin-left: 0" class="bg-gray-100">
    <SidebarGroupLabel>
      <div class="relative w-[100%] " style="margin-top:5px ">
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
            <SidebarMenuItem v-for="user in users" :key="user.userName">
              <SidebarMenuButton
                  as-child
                  :isActive="activeItem?.userName === user.userName"
                  @click="handleUserClick(user)"
                  class="data-[active=true]:bg-blue-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 cursor-default"
              >
                <div class="flex items-center w-full">
                  <!-- 圆形头像 - 80x80像素 -->
                  <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                    <img
                        :src="user.arg"
                        :alt="user.userName"
                        class="w-full h-full object-cover"
                    />
                  </div>
                  <!-- 右侧文本区域 -->
                  <div class="flex flex-col flex-grow space-y-1">
                    <span class="text-[18px] font-bold">{{ user.userName }}</span>
                    <span class="text-[13px] text-gray-500 truncate">{{ user.lastTalk }}</span>
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