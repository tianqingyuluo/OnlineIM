<script setup lang="ts">
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { ref, computed } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { type MessageResponse } from '@/type/message'
import { useUserStore } from '@/stores/user'
import { useListStore } from '@/stores/list'
import HoverProfile from '@/components/independent/profile/hoverProfile.vue' // 引入 HoverProfile 组件

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
// 悬浮资料卡位置控制
const hoverCardStyle = ref({});

// 点击外部关闭
onClickOutside(triggerRef, () => {
  showHoverProfile.value = false
})

// 头像左键点击处理
const handleAvatarClick = (event: MouseEvent) => {
  if (event.button === 0) { // 0 表示左键
    event.preventDefault()
    const rect = triggerRef.value?.getBoundingClientRect();
    if (rect) {
      // Position the hover card 12px to the right of the avatar
      hoverCardStyle.value = {
        position: 'fixed',
        top: `${rect.top}px`,
        left: `${rect.right + 12}px`, // right is x + width
        zIndex: 50, // Ensure it's above other content
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

const handleMenuItemClick = (action: string) => {
  if (action === 'viewProfile') {
    // 直接控制悬浮资料卡显示，不再emit事件
    const rect = triggerRef.value?.getBoundingClientRect();
    if (rect) {
      // Position the hover card 12px to the right of the avatar
      hoverCardStyle.value = {
        position: 'fixed',
        top: `${rect.top}px`,
        left: `${rect.right + 12}px`, // right is x + width
        zIndex: 50, // Ensure it's above other content
      };
      showHoverProfile.value = true;
    }
  } else if (action === 'addFriend') {
    emit('addFriend', props.message.sender_info.user_id)
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

    <!-- 悬浮资料卡片 (通过 Teleport 渲染到 body) -->
    <Teleport to="body">
      <Transition name="hover-profile">
        <HoverProfile
            v-if="showHoverProfile"
            :style="hoverCardStyle" // 应用计算出的样式
            class="shadow-lg bg-white h-[300px] w-[400px] rounded-lg"
            :user-id="message.sender_info.user_id"
        />
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* 添加过渡动画 */
.hover-profile-enter-active,
.hover-profile-leave-active {
  transition: all 0.2s ease;
}

.hover-profile-enter-from,
.hover-profile-leave-to {
  opacity: 0;
  /* 过渡动画不再依赖 transform，因为位置由 style 动态设置 */
}

/* 确保进入和离开的最终状态 */
.hover-profile-enter-to,
.hover-profile-leave-from {
  opacity: 1;
  /* 最终位置由 style 动态设置 */
}
</style>