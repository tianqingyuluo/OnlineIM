<script setup lang="ts">
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { ref, nextTick } from 'vue'

defineProps({
  message: {
    type: String,
    required: true
  },
  isSelf: {
    type: Boolean,
    default: false
  },
  // 移除 avatarUrl 和 userName 属性，因为头像将在父组件中处理
})

const isMenuOpen = ref(false)
const messageRef = ref<HTMLDivElement | null>(null)

const functionMenuItems = [
  {
    title: "复制",
  },
  {
    title: "撤回",
  },
  {
    title: "删除",
  },
  {
    title: "引用",
  },
];

// 当菜单打开时选中文本
const handleMenuOpenChange = (open: boolean) => {
  isMenuOpen.value = open
  if (open && messageRef.value) {
    nextTick(() => {
      const range = document.createRange()
      range.selectNodeContents(messageRef.value!)
      const selection = window.getSelection()
      selection?.removeAllRanges()
      selection?.addRange(range)
    })
  }
}
</script>

<template>
  <div :class="['max-w-[100%]']">
    <!-- 对方消息 -->
    <template v-if="!isSelf">
      <ContextMenu class="w-full" v-model:open="isMenuOpen" @update:open="handleMenuOpenChange">
        <ContextMenuTrigger class="w-full">
          <div
              ref="messageRef"
              :class="['bg-gray-50 rounded-lg p-3 shadow-sm w-full select-text', isMenuOpen ? 'bg-gray-200' : '']"
          >
            {{ message }}
          </div>
        </ContextMenuTrigger>
        <ContextMenuContent class="w-48">
          <ContextMenuItem v-for="item in functionMenuItems" :key="item.title">
            {{ item.title }}
          </ContextMenuItem>
        </ContextMenuContent>
      </ContextMenu>
    </template>
    <!-- 自己的消息 -->
    <template v-else>
      <ContextMenu class="w-full" v-model:open="isMenuOpen" @update:open="handleMenuOpenChange">
        <ContextMenuTrigger class="w-full">
          <div
              ref="messageRef"
              :class="['bg-blue-50 rounded-lg p-3 shadow-sm w-full select-text', isMenuOpen ? 'bg-gray-200' : '']"
          >
            {{ message }}
          </div>
        </ContextMenuTrigger>
        <ContextMenuContent class="w-48">
          <ContextMenuItem v-for="item in functionMenuItems" :key="item.title">
            {{ item.title }}
          </ContextMenuItem>
        </ContextMenuContent>
      </ContextMenu>
    </template>
  </div>
</template>

<style scoped>
/* 气泡样式微调 */
.bg-white {
  border-top-left-radius: 0;
}
.bg-blue-50, .bg-gray-100 {
  border-top-right-radius: 0;
}
.select-text {
  user-select: text;
}
</style>