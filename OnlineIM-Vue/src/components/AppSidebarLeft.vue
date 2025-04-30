<script setup lang="ts">
 import { Settings,MessageCircleMore,Users,UserRoundPlus,User } from "lucide-vue-next"
 import {
   Sidebar,
   SidebarContent,
   SidebarGroup,
   SidebarGroupContent,
   SidebarMenu,
   SidebarMenuButton,
   SidebarMenuItem,
   SidebarFooter,
 } from "@/components/ui/sidebar"
 import {ref} from "vue";

 const defaultUser = ref({
   userName: "默认用户",
   arg: "/images/help.png",
   lastTalk: "这是默认用户的最后一条消息",
   TalkingList: [
     {
       messageId: 1,
       message: "你好，这是一条示例消息",
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
 })
// Menu items.
const items = [
  {
    title: "聊天",
    url: "#",
     icon: MessageCircleMore,
  },
  {
    title: "好友",
    icon: User,
  },
  {
    title: "群聊",
    url: "#",
     icon: Users,
  },
  {
    title: "添加好友/群",
    url: "#",
     icon: UserRoundPlus,
  },
];
 const buttonItems = [
   {
     title: "设置",
     url: "#",
     icon: Settings,
   },
 ]
 const activeItem = ref('聊天')
</script>

<template>
    <Sidebar collapsible=none style="--sidebar-width: 20%; min-height: 100vh ;border-right:1px solid #e5e7eb; " class="bg-gray-75">
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem v-for="item in items" :key="item.title" >
                <SidebarMenuButton as-child :isActive="activeItem === item.title" @click="activeItem = item.title"
                                   class="data-[active=true]:bg-gray-100 data-[active=true]:text-black h-[40px] mb-4 "  >
                  <a :href="item.url" style="display: flex; justify-content: center; align-items: center; width: 100%;">
                    <component :is="item.icon" style="width: 30px; height: 30px"/>
                  </a>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem v-for="item in buttonItems" :key="item.title">
            <SidebarMenuButton asChild :isActive="activeItem === item.title" @click="activeItem = item.title"
                               class="data-[active=true]:bg-gray-100 data-[active=true]:text-black h-[40px] mb-4 " >
              <a :href="item.url" style="display: flex; justify-content: center; align-items: center; width: 100%; ">
                <component :is="item.icon" style="width: 30px; height: 30px"/>
              </a>
            </SidebarMenuButton>
            <button class="w-[40px] h-[40px] rounded-full overflow-hidden mr-4 flex-shrink-0">
                <img
                    :src="defaultUser.arg"
                    :alt="defaultUser.userName"
                    class="w-full h-full object-cover"
                />
            </button>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>

</template>