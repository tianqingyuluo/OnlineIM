<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user.ts'
import { useListStore } from '@/stores/list.ts'
import { SidebarProvider } from "@/components/ui/sidebar"
import AppSidebarRightTalks from "@/components/AppSideBar/right/AppSidebarRightTalks.vue"
import AppSidebarRightFriends from "@/components/AppSideBar/right/AppSidebarRightFriends.vue"
import AppSidebarRightGroup from "@/components/AppSideBar/right/AppSidebarRightGroup.vue"
import UserMainPart from "@/components/MainPart/UserMainPart.vue"
import GroupMainPart from "@/components/MainPart/GroupMainPart.vue"
import UserPart from '@/views/userPart.vue'
import { MessageCircleMore,Users,UserRoundPlus,User } from "lucide-vue-next"
import UserThings from "@/components/AppSideBar/left/userThings.vue";
import UserFounding from "@/components/independent/founding/userFounding.vue";
const route = useRoute()
const userStore = useUserStore()
const listStore = useListStore()


const showUserFounding = ref(false);
const handleAddClick = () => {
  showUserFounding.value = true
}

const showUserThings = ref(false)
const userThingsRef = ref<HTMLElement | null>(null)
const avatarPosition = ref({
  bottom: 0,
  left: 0
})

const toggleUserThings = (e?: MouseEvent) => {
  e?.stopPropagation()
  if (e) {
    const avatarEl = e.currentTarget as HTMLElement
    const rect = avatarEl.getBoundingClientRect()
    avatarPosition.value = {
      bottom: window.innerHeight - rect.bottom + 56, // 56px是导航栏高度
      left: rect.left - 200 + rect.width // 200px是弹窗宽度
    }
  }
  showUserThings.value = !showUserThings.value
}

const closeOnOutsideClick = () => {
  if (showUserThings.value) {
    showUserThings.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', closeOnOutsideClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeOnOutsideClick)
})

const showUserProfile = computed(() => route.path.startsWith('/main/friends/'))
const chatType = computed(() => {
  if (route.path.includes('/group/')) return 'group'
  if (route.path.includes('/private/')) return 'private'
  return ''
})
const chatId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})

onMounted(async () => {
  try {
    await userStore.fetchUserData()
    await listStore.fetchUserData()
  } catch (err) {
    console.error('获取用户信息失败:', err)
  }
})
const navItems = [
  { path: '/main/chat', icon: MessageCircleMore },
  { path: '/main/friends', icon: User },
  { path: '/main/groups', icon: Users },
  { path: '#', icon: UserRoundPlus, action: handleAddClick }
]
</script>

<template>
  <div class="phone-layout flex ">
    <!-- 主内容区域 -->
    <div class="content-area">
      <SidebarProvider class="sidebar-provider">
        <AppSidebarRightTalks
            v-if="route.path.startsWith('/main/chat')"
            class="w-full h-full"
        />
        <AppSidebarRightFriends
            v-else-if="route.path.startsWith('/main/friends')"
            class="w-full h-full"
        />
        <AppSidebarRightGroup
            v-else-if="route.path.startsWith('/main/groups')"
            class="w-full h-full"
        />
      </SidebarProvider>

      <UserMainPart
          v-if="chatType === 'private'"
          :key="`private-${chatId}`"
          class="chat-content"
      />
      <GroupMainPart
          v-else-if="chatType === 'group'"
          :key="`group-${chatId}`"
          class="chat-content"
      />
      <UserPart
          v-if="showUserProfile"
          :key="`user-${route.params.userId}`"
          class="chat-content"
      />
    </div>

    <!-- 底部导航栏 - 只显示图标并垂直居中 -->
    <div class="bottom-nav">
  <router-link
      v-for="item in navItems"
      :key="item.path"
      :to="item.path"
      class="nav-item"
      @click="item.action"
  >
    <component :is="item.icon" class="nav-icon"  />
  </router-link>
  
  <button 
    v-if="userStore.loggedInUser"
    class="nav-item"
    @click="toggleUserThings"
  >
    <img
      :src="userStore.loggedInUser.avatar_url"
      :alt="userStore.loggedInUser.nickname"
      class="w-8 h-8 rounded-full object-cover"
    />
  </button>
</div>

<Teleport to="body">
  <Transition 
    name="fade-slide"
  >
    <div
      v-if="showUserThings"
      ref="userThingsRef"
      class="fixed z-[9999] bg-white border shadow-lg rounded-xl"
      style="width: 200px;"
      :style="{ 
        bottom: `${avatarPosition.bottom}px`, 
        left: `${avatarPosition.left}px`, 
        height: 'auto' 
      }"
      @click.stop
    >
    <UserThings />
    </div>
  </Transition>
</Teleport>

<Transition name="fade-slide" mode="out-in">
  <div v-if="showUserFounding" class="fixed inset-0 flex z-50">
    <div class="fixed inset-0 bg-black/80 transition-opacity" @click="showUserFounding = false"></div>
    <UserFounding
        class="fixed z-50 w-[90%] max-w-md top-1/2"
        style="left: 5%"
        @close="showUserFounding = false"
    />
  </div>
</Transition>
  </div>
</template>

<style scoped>
.phone-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  position: relative;
  /* 移除 overflow: hidden */
}

.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-content {
  flex: 1;
  /* 移除 overflow-y: auto 避免嵌套滚动 */
}

.sidebar-provider {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-content {
  flex: 1;
}

.bottom-nav {
  display: flex;
  justify-content: space-around;
  align-items: center; /* 垂直居中 */
  height: 56px; /* 固定高度 */
  background: var(--background);
  border-top: 1px solid var(--border);
}

.nav-item {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  padding: 0 16px;
  opacity: 0.6;
  transition: opacity 0.2s ease;
}

.nav-item.active {
  opacity: 1;
}

.nav-icon {
  width: 24px;
  height: 24px;
}
</style>