<script setup lang="ts">
import { computed } from 'vue'
import type { Conversation } from '@/type/Conversation'
import { formatConversationTime, formatUnread } from '@/utils/conversation-time'

const props = defineProps<{
  conversation: Conversation
}>()

const isGroup = computed(() => props.conversation.type === 'group' || props.conversation.conversation_id?.startsWith('grp_'))
const avatar = computed(() =>
  props.conversation.target_info?.avatar_url || (isGroup.value ? '/images/group.png' : '/images/help.png'))
const time = computed(() =>
  formatConversationTime(props.conversation.last_message?.timestamp || props.conversation.last_activity_time))
const unread = computed(() => formatUnread(props.conversation.unread_count ?? props.conversation.notreadednumber))
const preview = computed(() => props.conversation.last_message?.content_preview || '无消息')
</script>

<template>
  <div class="flex items-center w-full min-w-0" :data-conversation-id="conversation.conversation_id" data-testid="conversation-item">
    <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
      <img :src="avatar" :alt="conversation.target_info?.name || ''" class="w-full h-full object-cover" />
    </div>
    <div class="flex min-w-0 flex-1 flex-col gap-1">
      <div class="flex items-center gap-2">
        <span class="min-w-0 flex-1 truncate text-[18px] font-bold" data-testid="conversation-name">
          {{ conversation.target_info?.name || 'Unknown' }}
        </span>
        <span class="shrink-0 text-xs text-gray-400" data-testid="conversation-time">{{ time }}</span>
      </div>
      <div class="flex items-center gap-2">
        <span class="min-w-0 flex-1 truncate text-[13px] text-gray-500" data-testid="conversation-preview">
          <span v-if="conversation.is_at" class="text-red-500">[有人@我]</span>
          {{ preview }}
        </span>
        <span
          v-if="unread"
          data-testid="conversation-unread"
          :class="[
            'shrink-0 min-w-5 rounded-full px-1.5 py-0.5 text-center text-xs leading-4 text-white',
            conversation.is_muted ? 'bg-gray-400' : 'bg-red-500',
          ]"
        >{{ unread }}</span>
      </div>
    </div>
  </div>
</template>
