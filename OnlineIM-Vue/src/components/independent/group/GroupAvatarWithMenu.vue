<script setup lang="ts">
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { ref, computed } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { type MessageResponse } from '@/type/message.ts'
import { useUserStore } from '@/stores/user.ts'
import { useListStore } from '@/stores/list.ts'
import HoverProfile from '@/components/independent/profile/hoverProfile.vue' // 引入 HoverProfile 组件
import SendFriendRequest from '@/components/independent/friends/SendFriendRequest.vue' // 引入 SendFriendRequest 组件

const userStore = useUserStore()
const listStore = useListStore()

const props = defineProps({
  avatarUrl: {
    type: String,
    required: true
  },
  message: {
    type: Object as () => MessageResponse,
    required: true
  }
})

const emit = defineEmits(['viewProfile', 'addFriend', 'sendMessage'])

// 悬浮资料卡控制
const showHoverProfile = ref(false)
const triggerRef = ref<HTMLElement | null>(null)

// 点击外部关闭
onClickOutside(triggerRef, () => {
  showHoverProfile.value = false
})
const hoverCardStyle = ref({});
// 头像左键点击处理
const handleAvatarClick = (event: MouseEvent) => {
  if (event.button === 0) { // 0 表示左键
    event.preventDefault()
    const rect = triggerRef.value?.getBoundingClientRect();
    if (rect) {
      // 计算固定位置，考虑页面滚动
      hoverCardStyle.value = {
        top: `${rect.top}px`,
        left: `${rect.right + 12}px`,
      };
      showHoverProfile.value = !showHoverProfile.value;
    }
  }
}

// 右键菜单相关逻辑
const isMenuOpen = ref(false)
const menuItems = computed(() => {
  const items = [
    { title: "查看资料", action: 'viewProfile' },
  ];

  if (props.message.sender_info.user_id !== userStore.loggedInUser.user_id) {
    const isFriend = listStore.friends.some(
        friend => friend.friend_info.user_id === props.message.sender_info.user_id
    )

    if (!isFriend) {
      items.push({ title: "添加好友", action: 'addFriend' })
    }
  }

  return items
})

// 好友请求弹窗控制
const showSendFriendRequest = ref(false)
const selectedUserForFriendRequest = ref(null)

const openSendFriendRequest = (user: any) => {
  selectedUserForFriendRequest.value = user
  showSendFriendRequest.value = true
}

const closeSendFriendRequest = () => {
  showSendFriendRequest.value = false
  selectedUserForFriendRequest.value = null
}

const handleMenuItemClick = (action: string) => {
  if (action === 'viewProfile') {
    emit('viewProfile', props.message.sender_info.user_id)
  } else if (action === 'addFriend') {
    openSendFriendRequest(props.message.sender_info)
  }
}
</script>
<template>
  <div class="flex items-start max-w-[80%] relative">
    <!-- 右键菜单区域 -->
    <ContextMenu v-model:open="isMenuOpen">
      <ContextMenuTrigger>
        <!-- 头像触发区域 -->
        <img
            ref="triggerRef"
            :src="avatarUrl || '/images/group.png'"
            class="w-10 h-10 rounded-full mr-2 cursor-pointer"
            @mouseup="handleAvatarClick"
        />
      </ContextMenuTrigger>
      <ContextMenuContent class="w-48">
        <ContextMenuItem
            v-for="item in menuItems"
            :key="item.title"
            @click="handleMenuItemClick(item.action)"
        >
          {{ item.title }}
        </ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>

    <Teleport to="body">
      <Transition name="hover-profile">
        <div 
          v-if="showHoverProfile"
          class="fixed z-50 bg-white h-[400px] w-[600px] shadow-lg flex items-center justify-center"
          :style="hoverCardStyle"
        >
          <hover-profile
            class="h-full w-full"
            :user-id="message.sender_info.user_id"
          />
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="send-friend-request">
        <div
          v-if="showSendFriendRequest"
          class="fixed inset-0 m-auto w-1/2 h-1/2 z-[100]"
        >
          <SendFriendRequest
            :user="selectedUserForFriendRequest"
            @close="closeSendFriendRequest"
            @success="closeSendFriendRequest"
          />
        </div>
      </Transition>
    </Teleport>

  </div>
</template>

<style scoped>
/* 优化后的动画效果 */
.hover-profile-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  transition-delay: 0.1s;
}

.hover-profile-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 1, 1);
}

.hover-profile-enter-from,
.hover-profile-leave-to {
  opacity: 0;
  transform: translateX(-10px) scale(0.95);
}

.hover-profile-enter-to {
  opacity: 1;
  transform: translateX(0) scale(1);
}

/* 添加细微的悬浮效果 */
.hover-profile-enter-active .profile-card {
  transition: transform 0.3s ease;
}

.hover-profile-enter-active .profile-card:hover {
  transform: translateY(-2px);
}
</style>