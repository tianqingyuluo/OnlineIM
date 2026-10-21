<script setup lang="ts">
import { Pencil, X } from 'lucide-vue-next'

defineProps<{
  original: string
}>()

const emit = defineEmits<{
  cancel: []
}>()
</script>

<template>
  <div data-testid="edit-composer-bar" class="flex min-h-14 items-center gap-3 border-t border-slate-200 bg-amber-50/70 px-4 py-2 backdrop-blur-sm">
    <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber-500 text-white shadow-sm">
      <Pencil class="h-4 w-4" aria-hidden="true" />
    </div>
    <div class="min-w-0 flex-1">
      <div class="text-xs font-semibold text-slate-700">编辑消息</div>
      <p class="mt-0.5 line-clamp-2 whitespace-pre-wrap break-words text-xs leading-4 text-slate-500">{{ original }}</p>
    </div>
    <button
      type="button"
      data-testid="cancel-edit"
      aria-label="取消编辑"
      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-slate-400 transition hover:bg-amber-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400/40"
      @click="emit('cancel')"
    >
      <X class="h-4 w-4" aria-hidden="true" />
    </button>
  </div>
</template>
