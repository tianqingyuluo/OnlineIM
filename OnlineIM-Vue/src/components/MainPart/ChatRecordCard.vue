<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { MessagesSquare, X } from 'lucide-vue-next'
import MediaMessage from '@/components/MainPart/MediaMessage.vue'
import StickerMessage from '@/components/MainPart/StickerMessage.vue'
import { isMediaMessageType } from '@/utils/media-message'
import { isStickerMessageType } from '@/utils/sticker-message'
import {
  chatRecordSummaryLines,
  formatRecordTime,
  isChatRecordType,
  parseChatRecord,
  previewOfRecordItem,
} from '@/utils/chat-record'

defineOptions({ name: 'ChatRecordCard' })

const props = withDefaults(defineProps<{
  content: unknown
  isSelf?: boolean
  /** 嵌套层级：记录中的记录仍可打开，层级只用于叠放顺序 */
  depth?: number
}>(), {
  isSelf: false,
  depth: 0,
})

const record = computed(() => parseChatRecord(props.content))
const lines = computed(() => (record.value ? chatRecordSummaryLines(record.value, 3) : []))
const open = ref(false)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    event.stopPropagation()
    open.value = false
  }
}

watch(open, value => {
  if (typeof window === 'undefined') return
  if (value) window.addEventListener('keydown', onKeydown, true)
  else window.removeEventListener('keydown', onKeydown, true)
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKeydown, true)
})
</script>

<template>
  <button
    type="button"
    data-testid="chat-record-card"
    :class="[
      'flex w-64 max-w-full flex-col gap-1 rounded-xl border bg-white px-3 py-2.5 text-left shadow-sm transition-colors hover:bg-slate-50',
      isSelf ? 'border-blue-100' : 'border-slate-200',
    ]"
    @click.stop="open = true"
  >
    <span data-testid="chat-record-title" class="truncate text-sm font-medium text-slate-800">
      {{ record?.title || '聊天记录' }}
    </span>
    <span
      v-for="(line, index) in lines"
      :key="index"
      data-testid="chat-record-line"
      class="truncate text-xs text-slate-500"
    >{{ line }}</span>
    <span class="mt-1 flex items-center gap-1 border-t pt-1.5 text-[11px] text-slate-400">
      <MessagesSquare class="h-3 w-3" aria-hidden="true" />
      聊天记录 · {{ record?.count ?? 0 }} 条
    </span>
  </button>

  <Teleport to="body">
    <div
      v-if="open"
      data-testid="chat-record-viewer"
      class="fixed inset-0 flex items-stretch justify-center bg-black/40 sm:items-center"
      :style="{ zIndex: 60 + depth * 2 }"
      @click.self="open = false"
    >
      <div
        role="dialog"
        aria-modal="true"
        :aria-label="record?.title || '聊天记录'"
        class="flex h-full w-full flex-col bg-white sm:h-auto sm:max-h-[80vh] sm:max-w-lg sm:rounded-xl sm:shadow-xl"
      >
        <div class="flex items-center justify-between border-b px-4 py-3">
          <span data-testid="chat-record-viewer-title" class="truncate font-medium">{{ record?.title || '聊天记录' }}</span>
          <button
            type="button"
            data-testid="chat-record-close"
            aria-label="关闭聊天记录"
            class="flex h-8 w-8 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100"
            @click="open = false"
          >
            <X class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        <div class="flex-1 space-y-4 overflow-y-auto px-4 py-3">
          <div v-if="!record || record.items.length === 0" class="py-8 text-center text-sm text-slate-400">
            聊天记录为空或无法解析
          </div>
          <div
            v-for="(item, index) in record?.items || []"
            :key="index"
            data-testid="chat-record-item"
            :data-message-type="item.message_type"
            class="flex items-start gap-2"
          >
            <img
              :src="item.sender_avatar || '/images/default-avatar.png'"
              alt=""
              class="h-8 w-8 shrink-0 rounded-full object-cover"
            />
            <div class="min-w-0 flex-1">
              <div class="flex items-baseline gap-2 text-xs text-slate-400">
                <span class="truncate text-slate-600" data-testid="chat-record-sender">{{ item.sender_name || '未知用户' }}</span>
                <span class="shrink-0">{{ formatRecordTime(item.timestamp) }}</span>
              </div>
              <div v-if="item.forwarded_from" class="text-[11px] text-slate-400">
                转发自 {{ item.forwarded_from.display_name }}
              </div>
              <div class="mt-1 min-w-0">
                <ChatRecordCard
                  v-if="isChatRecordType(item.message_type)"
                  :content="item.content"
                  :depth="depth + 1"
                />
                <StickerMessage
                  v-else-if="isStickerMessageType(item.message_type)"
                  :message-type="item.message_type"
                  :content="item.content"
                />
                <MediaMessage
                  v-else-if="isMediaMessageType(item.message_type)"
                  :message-type="item.message_type"
                  :content="item.content"
                />
                <div
                  v-else-if="item.message_type === 'text'"
                  class="whitespace-pre-wrap break-words text-sm text-slate-800"
                >{{ item.content }}</div>
                <div v-else class="text-sm text-slate-500">{{ previewOfRecordItem(item) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
