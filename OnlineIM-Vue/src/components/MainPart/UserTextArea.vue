<script setup lang="ts">
import MessageActions from '@/components/MainPart/MessageActions.vue'
import MessageReplyPreview from '@/components/MainPart/MessageReplyPreview.vue'
import MediaMessage from '@/components/MainPart/MediaMessage.vue'
import StickerMessage from '@/components/MainPart/StickerMessage.vue'
import type { ReplyReference } from '@/type/message'
import { computed } from 'vue'
import { isMediaMessageType } from '@/utils/media-message'
import { canSaveAsEmoji, isStickerMessageType } from '@/utils/sticker-message'
import { splitMentionSegments } from '@/utils/mention'

const props = withDefaults(defineProps<{
  message: string
  isSelf?: boolean
  canReply?: boolean
  recalled?: boolean
  replyTo?: ReplyReference
  messageType?: string
  /** 群聊 @提及：被提及成员 ID、成员名字表与当前用户 ID（用于高亮 @我） */
  atUsers?: string[] | null
  mentionNames?: Record<string, string[]>
  currentUserId?: string
}>(), {
  isSelf: false,
  canReply: true,
  recalled: false,
  replyTo: undefined,
  messageType: 'text',
  atUsers: undefined,
  mentionNames: () => ({}),
  currentUserId: undefined,
})

const isMedia = computed(() => isMediaMessageType(props.messageType))
/** 表情消息不套气泡，直接显示较大的图片（GIF 原样播放） */
const isSticker = computed(() => isStickerMessageType(props.messageType) && !props.recalled)
const canSaveEmoji = computed(() => !props.recalled && canSaveAsEmoji(props.messageType))

const segments = computed(() =>
  splitMentionSegments(props.message, props.atUsers, props.mentionNames))
const mentionsMe = computed(() =>
  !!props.currentUserId && !!props.atUsers?.includes(props.currentUserId))

const emit = defineEmits<{
  reply: []
  navigate: [reference: ReplyReference]
  saveEmoji: []
}>()

async function copyMessage() {
  if (props.recalled || !props.message || isMedia.value || isStickerMessageType(props.messageType)) return
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
    :can-copy="!isMedia && !isStickerMessageType(messageType) && !recalled"
    :can-save-emoji="canSaveEmoji"
    @reply="emit('reply')"
    @copy="copyMessage"
    @save-emoji="emit('saveEmoji')"
  >
    <div
      v-if="isSticker"
      :class="['flex min-w-0 max-w-full flex-col gap-1.5', isSelf ? 'items-end' : 'items-start']"
    >
      <MessageReplyPreview
        v-if="replyTo"
        :reference="replyTo"
        class="max-w-full rounded-lg bg-slate-50 px-2 py-1.5 ring-1 ring-black/[0.04]"
        @navigate="emit('navigate', $event)"
      />
      <StickerMessage :message-type="messageType" :content="message" />
    </div>
    <div
      v-else
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
        :is-self="isSelf"
      />
      <div
        v-else
        class="select-text whitespace-pre-wrap break-words text-[15px] leading-6 text-slate-800"
        :data-mentions-me="mentionsMe ? 'true' : undefined"
      ><template v-for="(segment, index) in segments" :key="index"><span
        v-if="segment.userId"
        :data-testid="segment.userId === currentUserId ? 'mention-me' : 'mention'"
        :data-user-id="segment.userId"
        :class="[
          'rounded px-0.5 font-medium',
          segment.userId === currentUserId ? 'bg-amber-100 text-amber-700' : 'text-blue-600',
        ]"
      >{{ segment.text }}</span><template v-else>{{ segment.text }}</template></template></div>
    </div>
  </MessageActions>
</template>
