<script setup lang="ts">
import MessageActions from '@/components/MainPart/MessageActions.vue'
import MessageReplyPreview from '@/components/MainPart/MessageReplyPreview.vue'
import MediaMessage from '@/components/MainPart/MediaMessage.vue'
import type { ReplyReference } from '@/type/message'
import { computed } from 'vue'
import { isMediaMessageType } from '@/utils/media-message'

const props = withDefaults(defineProps<{
  message: string
  isSelf?: boolean
  canReply?: boolean
  recalled?: boolean
  replyTo?: ReplyReference
  messageType?: string
}>(), {
  isSelf: false,
  canReply: true,
  recalled: false,
  replyTo: undefined,
  messageType: 'text',
})

const isMedia = computed(() => isMediaMessageType(props.messageType))

const emit = defineEmits<{
  reply: []
  navigate: [reference: ReplyReference]
}>()

async function copyMessage() {
  if (props.recalled || !props.message || isMedia.value) return
  try {
    await navigator.clipboard.writeText(props.message)
  } catch (error) {
    console.error('复制消息失败:', error)
  }
}
</script>

<template>
  <MessageActions
    :can-reply="canReply && !recalled"
    :align="isSelf ? 'end' : 'start'"
    @reply="emit('reply')"
    @copy="copyMessage"
  >
    <div
      :class="[
        'min-w-24 max-w-full rounded-xl px-3 py-2.5 shadow-sm ring-1 ring-black/[0.04]',
        isSelf ? 'rounded-tr-sm bg-blue-50' : 'rounded-tl-sm bg-slate-50',
      ]"
    >
      <MessageReplyPreview
        v-if="replyTo"
        :reference="replyTo"
        class="mb-2"
        @navigate="emit('navigate', $event)"
      />
      <div
        v-if="recalled"
        class="select-none text-sm italic text-slate-400"
      >
        消息已撤回
      </div>
      <MediaMessage
        v-else-if="isMedia"
        :message-type="messageType"
        :content="message"
      />
      <div
        v-else
        class="select-text whitespace-pre-wrap break-words text-[15px] leading-6 text-slate-800"
      >
        {{ message }}
      </div>
    </div>
  </MessageActions>
</template>
