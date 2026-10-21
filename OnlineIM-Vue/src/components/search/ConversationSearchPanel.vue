<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'
import MessageSearchResults from '@/components/search/MessageSearchResults.vue'
import type { MessageSearchItem } from '@/type/message'
import { SEARCH_MAX_KEYWORD_LENGTH } from '@/utils/message-search'

/** 会话内搜索面板：聊天窗口右上角的放大镜打开，只搜当前会话；点击结果跳到该消息 */
const props = defineProps<{
  open: boolean
  conversationId: string
}>()

const emit = defineEmits<{
  close: []
  select: [item: MessageSearchItem]
}>()

const query = ref('')
const inputRef = ref<InstanceType<typeof Input> | null>(null)

function focusInput() {
  const element = (inputRef.value as unknown as { $el?: HTMLElement } | null)?.$el
  const input = element instanceof HTMLInputElement ? element : element?.querySelector?.('input')
  input?.focus()
  input?.select()
}

watch(() => props.open, async open => {
  if (!open) return
  await nextTick()
  focusInput()
})

watch(() => props.conversationId, () => {
  query.value = ''
})

function handleKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('close')
  }
}
</script>

<template>
  <div
    v-show="open"
    data-testid="conversation-search-panel"
    class="absolute right-0 top-full z-40 flex h-[calc(100vh-60px)] w-full flex-col border-l bg-white shadow-lg sm:w-80"
    @keydown="handleKeyDown"
  >
    <div class="flex items-center gap-2 border-b p-3">
      <div class="relative min-w-0 flex-1">
        <Input
          ref="inputRef"
          v-model="query"
          data-testid="conversation-search-input"
          type="search"
          placeholder="搜索本会话的聊天记录"
          :maxlength="SEARCH_MAX_KEYWORD_LENGTH"
          class="w-full pl-9"
        />
        <Search class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      </div>
      <button
        type="button"
        data-testid="close-conversation-search"
        aria-label="关闭搜索"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-gray-500 hover:bg-gray-100 hover:text-gray-900"
        @click="emit('close')"
      >
        <X class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
    <div class="min-h-0 flex-1 overflow-y-auto">
      <div v-if="!query.trim()" class="px-4 py-6 text-center text-sm text-gray-400">输入关键字搜索文本消息</div>
      <MessageSearchResults :query="query" :conversation-id="conversationId" @select="emit('select', $event)" />
    </div>
  </div>
</template>
