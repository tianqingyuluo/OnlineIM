<script setup lang="ts">
import { Settings, Users, UserRoundPlus } from "lucide-vue-next"
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarFooter
} from "@/components/ui/sidebar"
import { ref } from "vue";

// 群聊菜单项
const items = [
  {
    title: "我的群聊",
    url: "#",
    icon: Users,
  },
  {
    title: "创建群聊",
    url: "#",
    icon: UserRoundPlus,
  }
];

const buttonItems = [
  {
    title: "设置",
    url: "#",
    icon: Settings,
  }
]

const activeItem = ref('我的群聊')
</script>

<template>
  <Sidebar collapsible=none style="--sidebar-width: 2.5%; min-height: 100vh" class="bg-blue-500">
    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in items" :key="item.title" style="cursor: default">
              <SidebarMenuButton as-child :isActive="activeItem === item.title" @click="activeItem = item.title" class="data-[active=true]:bg-white data-[active=true]:text-black">
                <a :href="item.url" style="display: flex; justify-content: center; align-items: center; width: 100%; cursor: default">
                  <component :is="item.icon" />
                </a>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
    <SidebarFooter>
      <SidebarMenu>
        <SidebarMenuItem v-for="item in buttonItems" :key="item.title" style="cursor: default">
          <SidebarMenuButton asChild :isActive="activeItem === item.title" @click="activeItem = item.title" class="data-[active=true]:bg-white data-[active=true]:text-black">
            <a :href="item.url" style="display: flex; justify-content: center; align-items: center; width: 100%; cursor: default">
              <component :is="item.icon"/>
            </a>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarFooter>
  </Sidebar>
</template>