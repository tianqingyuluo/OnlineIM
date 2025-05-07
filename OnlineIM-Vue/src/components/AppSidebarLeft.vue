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
import { ref, computed } from "vue";
import { useUserStore } from '@/stores/user'
import UserProfile from "@/components/UserProfile.vue";

const userStore = useUserStore()
const loggedUser = computed(() => {
  console.log('Logged user:', userStore.loggedInUser);
  return userStore.loggedInUser;
})
const isUserSettingsModalVisible = ref(false);

const showUserSettingsModal = () => {
  isUserSettingsModalVisible.value = true;
};

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
          <button
              v-if="loggedUser"
              class="w-[40px] h-[40px] rounded-full overflow-hidden mr-4 flex-shrink-0"
              @click="showUserSettingsModal"
          >
            <img
                :src="loggedUser.avatar_url"
                :alt="loggedUser.nickName"
                class="w-full h-full object-cover"
            />
          </button>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarFooter>
  </Sidebar>

  <!-- 添加过渡效果 -->
  <transition name="fade-slide">
    <div
        v-if="isUserSettingsModalVisible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-white/80"
    >
      <UserProfile @close="isUserSettingsModalVisible = false"/>
    </div>
  </transition>
</template>

<style scoped>
/* 修正后的淡入淡出+滑动过渡效果 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>