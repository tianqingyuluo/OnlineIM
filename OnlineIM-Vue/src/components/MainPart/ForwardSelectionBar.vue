<script setup lang="ts">
import { Forward, MessagesSquare, X } from 'lucide-vue-next'

defineProps<{
  count: number
}>()

const emit = defineEmits<{
  separate: []
  merged: []
  cancel: []
}>()
</script>

<template>
  <div
    data-testid="forward-selection-bar"
    class="flex items-center justify-between gap-2 border-t bg-white px-3 py-3 sm:px-4"
  >
    <span class="shrink-0 text-sm text-slate-600" data-testid="forward-selected-count">已选 {{ count }} 条</span>
    <div class="flex items-center gap-1.5 sm:gap-2">
      <button
        type="button"
        data-testid="forward-separate"
        :disabled="!count"
        class="flex items-center gap-1 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700 hover:bg-slate-200 disabled:opacity-50"
        @click="emit('separate')"
      >
        <Forward class="h-4 w-4" aria-hidden="true" />
        逐条转发
      </button>
      <button
        type="button"
        data-testid="forward-merged"
        :disabled="!count"
        class="flex items-center gap-1 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700 hover:bg-slate-200 disabled:opacity-50"
        @click="emit('merged')"
      >
        <MessagesSquare class="h-4 w-4" aria-hidden="true" />
        合并转发
      </button>
      <button
        type="button"
        data-testid="forward-cancel-select"
        aria-label="取消多选"
        class="flex h-9 w-9 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100"
        @click="emit('cancel')"
      >
        <X class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>
