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
import {ref, computed, onUnmounted,onMounted} from "vue";
import { useUserStore } from '@/stores/user.ts'
import UserThings from "@/components/AppSideBar/left/userThings.vue";
import UserFounding from "@/components/independent/founding/userFounding.vue";
import router from "@/router";

const userStore = useUserStore()
const loggedUser = computed(() => {
  console.log('Logged user:', userStore.loggedInUser);
  return userStore.loggedInUser;
})


const showUserThings = ref(false); // 新增控制userThings显示的状态
const userThingsRef = ref<HTMLElement | null>(null); // 新增ref引用
const avatarPosition = ref({
  bottom: 0,
  left: 0
});

const toggleUserThings = (e?: MouseEvent) => {
  e?.stopPropagation();
  if (e) {
    const avatarEl = e.currentTarget as HTMLElement;
    const rect = avatarEl.getBoundingClientRect();
    avatarPosition.value = {
      bottom: window.innerHeight - rect.bottom,
      left: rect.left
    };
  }
  showUserThings.value = !showUserThings.value;
};

// 新增点击外部关闭处理
function handleClickOutside(e: MouseEvent) {
  if (showUserThings.value && !userThingsRef.value?.contains(e.target as Node)) {
    showUserThings.value = false;
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});

const items = [
  {
    title: "聊天",
    path: "/main/chat",
    icon: MessageCircleMore
  },
  {
    title: "好友",
    path: "/main/friends", 
    icon: User
  },
  {
    title: "群聊",
    path: "/main/groups",
    icon: Users
  }
];


const showUserFounding = ref(false);

function handleItemClick(item) {
  if (item.path) {
    router.push(item.path);
    activeItem.value = item.title;
  }
}

const handleAddClick = () => {
  showUserFounding.value = true;
};
const buttonItems = [
  {
    title: "设置",
    url: "/main/setting",
    icon: Settings,
  },
]

const activeItem = ref('聊天')

</script>

<template>
  <Sidebar collapsible="none" class="sidebar-left">
    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in items" :key="item.title">
              <SidebarMenuButton 
                as-child 
                :isActive="activeItem === item.title" 
                @click="handleItemClick(item)" 
                class="data-[active=true]:bg-gray-100 data-[active=true]:text-black h-[40px] mb-4"
              >
                <a :href="item.url" style="display: flex; justify-content: center; align-items: center; width: 100%;">
                  <component :is="item.icon" style="width: 30px; height: 30px"/>
                </a>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <!-- 单独渲染的添加按钮 -->
            <SidebarMenuItem>
              <SidebarMenuButton 
                as-child
                class="data-[active=true]:bg-gray-100 data-[active=true]:text-black h-[40px] mb-4"
                @click="handleAddClick"
              >
                <a href="#" style="display: flex; justify-content: center; align-items: center; width: 100%;">
                  <UserRoundPlus style="width: 30px; height: 30px"/>
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
                             class="data-[active=true]:bg-gray-100 data-[active=true]:text-black h-[40px] mb-4">
            <router-link 
              :to="item.path"
              style="display: flex; justify-content: center; align-items: center; width: 100%;"
            >
              <component :is="item.icon" style="width: 30px; height: 30px"/>
            </router-link>
          </SidebarMenuButton>
          <button 
            v-if="loggedUser"
            class="w-[40px] h-[40px] rounded-full overflow-hidden mx-auto flex-shrink-0 block"
            @click="toggleUserThings"
          >
            <img
              :src="loggedUser.avatar_url"
              :alt="loggedUser.nickname"
              class="w-full h-full object-cover"
            />
          </button>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarFooter>
  </Sidebar>

  <!-- 修改为Teleport到body的userThings弹出框 -->
  <Teleport to="body">
    <Transition name="fade-slide">
    <div
        v-if="showUserThings"
        ref="userThingsRef"
        class="fixed z-[9999] bg-white border shadow-lg"
        style="width: 200px; border-radius: 0;"
        :style="{
          bottom: '0',
          left: '60px',
          height: 'auto'
        }"
        @click.stop
    >
    <UserThings />
    </div>
    </Transition>
  </Teleport>

  <Transition name="fade-slide" mode="out-in">
    <div v-if="showUserFounding" class="fixed inset-0 flex  z-50">
      <div class="fixed inset-0 bg-white/80 transition-opacity" @click="showUserFounding = false"></div>
      <userFounding
          class="relative z-50"
          @close="showUserFounding = false"
      />
    </div>
  </Transition>
</template>

<style scoped>
.sidebar-left {
  width: 60px;
  min-height: 100vh;
  border-right: 1px solid #e5e7eb;
  background-color: rgb(249 250 251); /* bg-gray-75 */
}
/* 修改过渡样式为 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* 蒙版过渡 */
.bg-white {
  transition: opacity 0.3s ease;
}
.fade-slide-enter-from .bg-white,
.fade-slide-leave-to .bg-white {
  opacity: 0;
}
</style>