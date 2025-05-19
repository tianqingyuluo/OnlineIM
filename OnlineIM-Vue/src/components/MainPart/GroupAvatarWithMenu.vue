<script setup lang="ts">
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { ref } from 'vue'

defineProps({
  avatarUrl: {
    type: String,
    required: true
  },
})

const isMenuOpen = ref(false)

const menuItems = [
  {
    title: "查看资料",
  },
  {
    title: "发送消息",
  },
  {
    title: "添加好友",
  },
];
</script>

<template>
  <div class="flex items-start max-w-[80%]">
    <ContextMenu v-model:open="isMenuOpen">
      <ContextMenuTrigger>
        <img 
          :src="avatarUrl || '/images/group.png'" 
          :alt="altText" 
          class="w-10 h-10 rounded-full mr-2 cursor-pointer"
        />
      </ContextMenuTrigger>
      <ContextMenuContent class="w-48">
        <ContextMenuItem v-for="item in menuItems" :key="item.title">
          {{ item.title }}
        </ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>
  </div>
</template>