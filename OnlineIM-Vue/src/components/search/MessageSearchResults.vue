<script setup lang="ts">
import { toRef } from 'vue'
import type { MessageSearchItem } from '@/type/message'
import { useMessageSearch } from '@/composables/useMessageSearch'
import { searchSenderName, splitSnippet } from '@/utils/message-search'
import { formatConversationTime } from '@/utils/conversation-time'
import { useUserStore } from '@/stores/user'

/**
 * 消息搜索结果列表：全局（侧栏搜索框）与会话内（聊天窗口的搜索面板）共用。
 * query 变化后自动防抖搜索；点击结果 emit select，由父组件负责跳转。
 */
const props = withDefaults(defineProps<{
  query: string
  conversationId?: string
  /** 全局搜索显示结果所属会话名 */
  showConversation?: boolean
}>(), {
  conversationId: undefined,
  showConversation: false,
})

const emit = defineEmits<{
  select: [item: MessageSearchItem]
}>()

const userStore = useUserStore()
const search = useMessageSearch(toRef(props, 'query'), () => props.conversationId)

function title(item: MessageSearchItem): string {
  const sender = searchSenderName(item, userStore.loggedInUser?.user_id)
  if (!props.showConversation) return sender
  const name = item.conversation?.name || ''
  return item.conversation?.type === 'group' && sender ? `${name} · ${sender}` : name
}
</script>

<template>
  <div v-if="search.keyword.value || search.loading.value" class="w-full" data-testid="message-search-results">
    <div class="px-4 py-2 text-xs font-semibold text-gray-500">聊天记录</div>
    <div v-if="search.loading.value && !search.results.value.length" class="px-4 py-3 text-sm text-gray-500" data-testid="message-search-loading">
      搜索中...
    </div>
    <div v-else-if="search.error.value" class="px-4 py-3 text-sm text-red-500" data-testid="message-search-error">
      {{ search.error.value }}
    </div>
    <div
      v-else-if="search.searched.value && !search.results.value.length"
      class="px-4 py-3 text-sm text-gray-500"
      data-testid="message-search-empty"
    >
      没有找到相关聊天记录
    </div>
    <ul v-else class="w-full">
      <li v-for="item in search.results.value" :key="item.message.message_id">
        <button
          type="button"
          data-testid="message-search-result"
          :data-result-message-id="item.message.message_id"
          :data-conversation-id="item.conversation?.conversation_id || item.message.conversation_id"
          class="w-full min-w-0 px-4 py-2 text-left hover:bg-gray-50 focus-visible:bg-gray-50 focus-visible:outline-none"
          @click="emit('select', item)"
        >
          <div class="flex min-w-0 items-center justify-between gap-2 text-xs text-gray-500">
            <span class="truncate" data-testid="message-search-title">{{ title(item) }}</span>
            <span class="shrink-0">{{ formatConversationTime(item.message.timestamp) }}</span>
          </div>
          <div class="mt-0.5 line-clamp-2 break-all text-sm text-gray-800" data-testid="message-search-snippet">
            <template v-for="(segment, index) in splitSnippet(item.snippet, item.highlights)" :key="index">
              <mark v-if="segment.hit" data-testid="search-hit" class="rounded-sm bg-amber-200/80 px-0.5 text-inherit">{{ segment.text }}</mark>
              <span v-else>{{ segment.text }}</span>
            </template>
          </div>
        </button>
      </li>
    </ul>
    <div v-if="search.hasMore.value && !search.error.value" class="flex justify-center py-2">
      <button
        type="button"
        data-testid="message-search-more"
        class="text-sm text-blue-600 hover:underline disabled:text-gray-400"
        :disabled="search.loadingMore.value"
        @click="search.loadMore()"
      >
        {{ search.loadingMore.value ? '加载中...' : '加载更多' }}
      </button>
    </div>
  </div>
</template>
