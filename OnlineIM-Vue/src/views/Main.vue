<script setup lang="ts">
import {SidebarProvider} from "@/components/ui/sidebar";
import AppSidebarLeft from "@/components/AppSideBar/left/AppSidebarLeft.vue";
import AppSidebarRightTalks from "@/components/AppSideBar/right/AppSidebarRightTalks.vue";
import AppSidebarRightFriends from "@/components/AppSideBar/right/AppSidebarRightFriends.vue";
import AppSidebarRightGroup from "@/components/AppSideBar/right/AppSidebarRightGroup.vue";
import UserMainPart from "@/components/MainPart/UserMainPart.vue";
import GroupMainPart from "@/components/MainPart/GroupMainPart.vue";
import {computed, onMounted, ref} from "vue";
import {useUserStore} from '@/stores/user.ts'
import {useListStore} from "@/stores/list.ts";
import {useRoute} from 'vue-router'
import UserPart from '@/views/userPart.vue'
import GroupPart from '@/views/groupPart.vue'
import {useWidthStore} from "@/stores/width";

const userStore = useUserStore()
const listStore = useListStore()
const route = useRoute()
const initialWidth = computed(() => {
  return useWidthStore().sidebarWidth
})
let sidebarWidth = ref(initialWidth)
const isResizing = ref(false)

const showUserProfile = computed(() => route.path.startsWith('/main/friends/'))
const showGroupProfile = computed(() => route.path.startsWith('/main/groups/'))
const chatType = computed(() => {
  if (route.path.includes('/group/')) return 'group'
  if (route.path.includes('/private/')) return 'private'
  return ''
})
const chatId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})

const startResize = (e: MouseEvent) => {
  e.preventDefault()
  isResizing.value = true
  const startX = e.clientX
  const startWidth = sidebarWidth.value

  const doDrag = (e: MouseEvent) => {
    if (!isResizing.value) return
    const deltaX = e.clientX - startX
    const newWidth = Math.max(320, Math.min(500, startWidth + deltaX))
    sidebarWidth.value = newWidth
    useWidthStore().sidebarWidth = newWidth
  }

  const stopDrag = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', doDrag)
    document.removeEventListener('mouseup', stopDrag)
    document.body.classList.remove('disable-select')
  }

  document.body.classList.add('disable-select')
  document.addEventListener('mousemove', doDrag)
  document.addEventListener('mouseup', stopDrag)
}

onMounted(async () => {
  try {
    await userStore.fetchUserData();
    await listStore.fetchUserData();
  } catch (err) {
    console.error('获取用户信息失败:', err);
  }
});
</script>

<template>
  <div class="flex h-screen no-scroll">
    <SidebarProvider
        :style="{ width: `${sidebarWidth}px` }"
        class="flex-shrink-0 relative h-screen flex"
    >
      <div class="flex flex-1 min-w-0">
        <AppSidebarLeft class="sidebar-left flex-shrink-0" />
        <div class="w-full flex-1">
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
        </div>
      </div>
      <div
          class="absolute right-0 top-0 bottom-0 w-1 cursor-ew-resize bg-gray-200 hover:bg-blue-500 z-1"
          @mousedown.prevent="startResize"
      />
    </SidebarProvider>

    <div class="flex-1 flex justify-center items-center min-w-0">
      <UserMainPart
          class="w-full h-full"
          v-if="chatType === 'private'"
          :key="`private-${chatId}`"
      />
      <GroupMainPart
          v-else-if="chatType === 'group'"
          class="w-full h-full"
          :key="`group-${chatId}`"
      />
      <UserPart
          v-if="showUserProfile"
          :key="`user-${route.params.userId}`"
          class="w-full h-full"
      />
      <GroupPart
          v-if="showGroupProfile"
          :key="`group-${route.params.id}`"
          class="w-full h-full"
      />
      <div v-if="!chatType && !showUserProfile && !showGroupProfile" class="text-gray-500">
        还没有选择会话
      </div>
    </div>
  </div>
</template>

<style>
.disable-select {
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
}

.no-scroll {
  overflow: hidden !important;
}

.cursor-ew-resize {
  cursor: ew-resize;
}
</style>