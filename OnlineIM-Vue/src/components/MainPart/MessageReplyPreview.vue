<script setup lang="ts">
import { computed } from 'vue'
import { CornerUpLeft } from 'lucide-vue-next'
import type { ReplyReference } from '@/type/message'

const props = defineProps<{
  reference: ReplyReference
}>()

const emit = defineEmits<{
  navigate: [reference: ReplyReference]
}>()

const statusText = computed(() => {
  if (props.reference.state === 'recalled') return '原消息已撤回'
  if (props.reference.state === 'unavailable') return '原消息不可用'
  return props.reference.preview_text || '[消息]'
})

const showsPreview = computed(() =>
  props.reference.state === 'active' || props.reference.state === 'edited',
)
</script>

<template>
  <button
    type="button"
    data-testid="reply-preview"
    class="reply-preview group/reply w-full min-w-0 rounded-md border border-slate-200/80 bg-white/65 px-2.5 py-2 text-left shadow-[inset_3px_0_0_0_rgba(71,85,105,0.45)] transition hover:border-slate-300 hover:bg-white/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400/40"
    @click.stop="emit('navigate', reference)"
  >
    <span class="flex items-center gap-1.5 text-[11px] font-medium tracking-wide text-slate-500">
      <CornerUpLeft class="h-3 w-3 shrink-0" aria-hidden="true" />
      <span class="truncate">{{ reference.sender_display_name }}</span>
      <span
        v-if="reference.state === 'edited'"
        class="shrink-0 rounded-full bg-amber-50 px-1.5 py-0.5 text-[10px] text-amber-700"
      >
        原消息已编辑
      </span>
    </span>
    <span
      :class="[
        'mt-1 block text-xs leading-4 line-clamp-2',
        showsPreview ? 'text-slate-600' : 'italic text-slate-400',
      ]"
    >
      {{ statusText }}
    </span>
  </button>
</template>
