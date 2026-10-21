<script setup lang="ts">
import { computed } from 'vue'
import { recallNoticeText } from '@/utils/message-recall'

const props = withDefaults(defineProps<{
  isSelf: boolean
  senderName?: string | null
  /** 自己撤回的文本在本页面还留有原文时显示“重新编辑” */
  canReedit?: boolean
  timestamp?: string
}>(), {
  senderName: '',
  canReedit: false,
  timestamp: '',
})

const emit = defineEmits<{
  reedit: []
}>()

const text = computed(() => recallNoticeText(props.isSelf, props.senderName))
</script>

<template>
  <div class="flex justify-center px-4" data-testid="recall-notice" :title="timestamp || undefined">
    <span class="rounded-full bg-slate-100 px-3 py-1 text-xs leading-5 text-slate-500 select-none">
      <span data-testid="recall-notice-text">{{ text }}</span>
      <button
        v-if="canReedit"
        type="button"
        data-testid="recall-reedit"
        class="ml-1.5 font-medium text-blue-600 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-400/40 rounded"
        @click="emit('reedit')"
      >重新编辑</button>
    </span>
  </div>
</template>
