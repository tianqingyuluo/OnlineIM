<script setup lang="ts">
import { computed, ref } from 'vue'
import { Copy, Forward, ListChecks, Pencil, Reply, SmilePlus, Undo2 } from 'lucide-vue-next'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'

const props = withDefaults(defineProps<{
  canReply?: boolean
  canCopy?: boolean
  canSaveEmoji?: boolean
  canForward?: boolean
  canSelect?: boolean
  /** 可编辑截止时间（毫秒时间戳），0 表示不可编辑；菜单打开时判断，过了时间窗自动不再显示“编辑” */
  editUntil?: number
  /** 可撤回截止时间（毫秒时间戳），0 表示不可撤回；同样在菜单打开时判断 */
  recallUntil?: number
  align?: 'start' | 'end'
}>(), {
  canReply: true,
  canCopy: true,
  canSaveEmoji: false,
  canForward: false,
  canSelect: false,
  editUntil: 0,
  recallUntil: 0,
  align: 'start',
})

const emit = defineEmits<{
  reply: []
  copy: []
  saveEmoji: []
  forward: []
  select: []
  edit: []
  recall: []
}>()

const openedAt = ref(Date.now())
const canEdit = computed(() => props.editUntil > 0 && openedAt.value <= props.editUntil)
const canRecall = computed(() => props.recallUntil > 0 && openedAt.value <= props.recallUntil)

function handleOpenChange(open: boolean) {
  if (open) {
    openedAt.value = Date.now()
    movesFocus = false
  }
}

/** 选了“编辑”/“回复”后焦点要留在输入框，不让菜单关闭时把焦点还给消息气泡 */
let movesFocus = false
function selectMovingFocus(action: 'edit' | 'reply') {
  movesFocus = true
  if (action === 'edit') emit('edit')
  else emit('reply')
}

function handleCloseAutoFocus(event: Event) {
  if (movesFocus) event.preventDefault()
  movesFocus = false
}
</script>

<template>
  <ContextMenu @update:open="handleOpenChange">
    <ContextMenuTrigger class="block min-w-0" :disabled="!canCopy && !canReply && !canSaveEmoji && !canForward && !canSelect && !recallUntil">
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
    <ContextMenuContent class="w-40" @close-auto-focus="handleCloseAutoFocus">
      <ContextMenuItem v-if="canCopy" class="gap-2" @select="emit('copy')">
        <Copy class="h-4 w-4" aria-hidden="true" />
        复制
      </ContextMenuItem>
      <ContextMenuItem v-if="canEdit" class="gap-2" data-testid="edit-action" @select="selectMovingFocus('edit')">
        <Pencil class="h-4 w-4" aria-hidden="true" />
        编辑
      </ContextMenuItem>
      <ContextMenuItem v-if="canReply" class="gap-2" @select="selectMovingFocus('reply')">
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
      <ContextMenuItem v-if="canRecall" class="gap-2 text-red-600 focus:text-red-600" data-testid="recall-action" @select="emit('recall')">
        <Undo2 class="h-4 w-4" aria-hidden="true" />
        撤回
      </ContextMenuItem>
    </ContextMenuContent>
  </ContextMenu>
</template>
