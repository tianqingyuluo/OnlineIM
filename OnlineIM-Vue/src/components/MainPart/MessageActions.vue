<script setup lang="ts">
import { Copy, Forward, ListChecks, Reply, SmilePlus } from 'lucide-vue-next'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'

withDefaults(defineProps<{
  canReply?: boolean
  canCopy?: boolean
  canSaveEmoji?: boolean
  canForward?: boolean
  canSelect?: boolean
  align?: 'start' | 'end'
}>(), {
  canReply: true,
  canCopy: true,
  canSaveEmoji: false,
  canForward: false,
  canSelect: false,
  align: 'start',
})

const emit = defineEmits<{
  reply: []
  copy: []
  saveEmoji: []
  forward: []
  select: []
}>()
</script>

<template>
  <ContextMenu>
    <ContextMenuTrigger class="block min-w-0" :disabled="!canCopy && !canReply && !canSaveEmoji && !canForward && !canSelect">
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
      <ContextMenuItem v-if="canCopy" class="gap-2" @select="emit('copy')">
        <Copy class="h-4 w-4" aria-hidden="true" />
        复制
      </ContextMenuItem>
      <ContextMenuItem v-if="canReply" class="gap-2" @select="emit('reply')">
        <Reply class="h-4 w-4" aria-hidden="true" />
        回复
      </ContextMenuItem>
      <ContextMenuItem v-if="canSaveEmoji" class="gap-2" data-testid="save-emoji-action" @select="emit('saveEmoji')">
        <SmilePlus class="h-4 w-4" aria-hidden="true" />
        添加到表情
      </ContextMenuItem>
      <ContextMenuItem v-if="canForward" class="gap-2" data-testid="forward-action" @select="emit('forward')">
        <Forward class="h-4 w-4" aria-hidden="true" />
        转发
      </ContextMenuItem>
      <ContextMenuItem v-if="canSelect" class="gap-2" data-testid="multi-select-action" @select="emit('select')">
        <ListChecks class="h-4 w-4" aria-hidden="true" />
        多选
      </ContextMenuItem>
    </ContextMenuContent>
  </ContextMenu>
</template>
