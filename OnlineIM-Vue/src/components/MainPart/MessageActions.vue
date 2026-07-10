<script setup lang="ts">
import { Copy, Reply } from 'lucide-vue-next'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'

withDefaults(defineProps<{
  canReply?: boolean
  align?: 'start' | 'end'
}>(), {
  canReply: true,
  align: 'start',
})

const emit = defineEmits<{
  reply: []
  copy: []
}>()
</script>

<template>
  <ContextMenu>
    <ContextMenuTrigger class="block min-w-0">
      <div class="group/message relative min-w-0">
        <button
          v-if="canReply"
          type="button"
          data-testid="quick-reply"
          aria-label="回复这条消息"
          :class="[
            'absolute top-1/2 z-10 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 opacity-0 shadow-sm transition-all hover:-translate-y-[55%] hover:text-slate-900 focus:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400/40 group-hover/message:opacity-100',
            align === 'end' ? '-left-9' : '-right-9',
          ]"
          @click.stop="emit('reply')"
        >
          <Reply class="h-3.5 w-3.5" aria-hidden="true" />
        </button>
        <slot />
      </div>
    </ContextMenuTrigger>
    <ContextMenuContent class="w-40">
      <ContextMenuItem class="gap-2" @select="emit('copy')">
        <Copy class="h-4 w-4" aria-hidden="true" />
        复制
      </ContextMenuItem>
      <ContextMenuItem v-if="canReply" class="gap-2" @select="emit('reply')">
        <Reply class="h-4 w-4" aria-hidden="true" />
        回复
      </ContextMenuItem>
    </ContextMenuContent>
  </ContextMenu>
</template>
