<script setup lang="ts">
import { computed } from 'vue'
import { CornerUpLeft, X } from 'lucide-vue-next'
import type { ReplyReference } from '@/type/message'

const props = defineProps<{
  reference: ReplyReference
}>()

const emit = defineEmits<{
  cancel: []
}>()

const previewText = computed(() => {
  if (props.reference.state === 'recalled') return '原消息已撤回'
  if (props.reference.state === 'unavailable') return '原消息不可用'
  return props.reference.preview_text || '[消息]'
})
</script>

<template>
  <div class="flex min-h-14 items-center gap-3 border-t border-slate-200 bg-slate-50/85 px-4 py-2 backdrop-blur-sm">
    <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-900 text-white shadow-sm">
      <CornerUpLeft class="h-4 w-4" aria-hidden="true" />
    </div>
    <div class="min-w-0 flex-1">
      <div class="flex items-center gap-2 text-xs font-semibold text-slate-700">
        <span class="truncate">回复 {{ reference.sender_display_name }}</span>
        <span v-if="reference.state === 'edited'" class="font-normal text-amber-700">原消息已编辑</span>
      </div>
      <p class="mt-0.5 line-clamp-2 text-xs leading-4 text-slate-500">{{ previewText }}</p>
    </div>
    <button
      type="button"
      data-testid="cancel-reply"
      aria-label="取消回复"
      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition hover:bg-slate-200 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400/40"
      @click="emit('cancel')"
    >
      <X class="h-4 w-4" aria-hidden="true" />
    </button>
  </div>
</template>
