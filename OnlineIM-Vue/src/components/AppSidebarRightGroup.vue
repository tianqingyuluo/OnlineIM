<script setup lang="ts">
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import {onMounted, ref} from "vue";



// 群聊列表
const groups = [
  {
    groupId: "1",
    groupName: "群聊1",
    groupAvatar: "/images/group.png",
    lastMessage: "群成员1: 大家好",
    MessageList: [
      {
        messageId: 1,
        message: "大家好，这是一条群消息",
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
  {
    groupName: "群聊2",
    groupAvatar: "/images/group.png",
    lastMessage: "群成员2: 有人在吗？"
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
  if(props.defaultSelected && groups.length > 0) {
    activeItem.value = groups[0].groupId
    emits('groupSelected', groups[0])
  }
})

const emits = defineEmits(['groupSelected'])

function handleGroupClick(group: any) {
  activeItem.value = group
  emits('groupSelected', group)
}
</script>

<template>
  <Sidebar collapsible="none" style="--sidebar-width: 80%; min-height: 100vh; margin-left: 0" class="bg-gray-100">
    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="group in groups" :key="group.groupId" style="cursor: default">
              <SidebarMenuButton
                  as-child
                  :isActive="activeItem === group.groupId"
                  @click="handleGroupClick(group)"
                  class="data-[active=true]:bg-blue-100 data-[active=true]:text-black flex items-center w-full h-[80px] px-4 cursor-default"
              >
                <div class="flex items-center w-full">
                  <!-- 圆形头像 - 80x80像素 -->
                  <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                    <img
                        :src="group.groupAvatar"
                        :alt="group.groupName"
                        class="w-full h-full object-cover"
                    />
                  </div>
                  <!-- 右侧文本区域 -->
                  <div class="flex flex-col flex-grow space-y-1">
                    <span class="text-[18px] font-bold">{{ group.groupName }}</span>
                    <span class="text-[13px] text-gray-500 truncate">{{ group.MessageList }}</span>
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