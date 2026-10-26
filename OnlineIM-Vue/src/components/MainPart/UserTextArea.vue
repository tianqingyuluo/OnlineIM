<script setup lang="ts">
import CallBubble from '@/components/call/CallBubble.vue'
import MessageActions from '@/components/MainPart/MessageActions.vue'
import MessageReplyPreview from '@/components/MainPart/MessageReplyPreview.vue'
import MediaMessage from '@/components/MainPart/MediaMessage.vue'
import StickerMessage from '@/components/MainPart/StickerMessage.vue'
import ChatRecordCard from '@/components/MainPart/ChatRecordCard.vue'
import type { ForwardedFrom, ReplyReference } from '@/type/message'
import { isChatRecordType } from '@/utils/chat-record'
import { computed } from 'vue'
import { isMediaMessageType } from '@/utils/media-message'
import { canSaveAsEmoji, isStickerMessageType } from '@/utils/sticker-message'
import { MENTION_ALL_ID, splitMentionSegments } from '@/utils/mention'
import { editedTitle } from '@/utils/message-edit'

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
  /** 逐条转发的消息显示“转发自 原作者” */
  forwardedFrom?: ForwardedFrom | null
  canForward?: boolean
  canSelect?: boolean
  /** 文本消息被编辑过：气泡里显示“已编辑” */
  edited?: boolean
  editedAt?: string | null
  /** 自己的文本消息可编辑截止时间（毫秒），0 不可编辑 */
  editUntil?: number
  /** 自己的消息可撤回截止时间（毫秒），0 不可撤回 */
  recallUntil?: number
}>(), {
  isSelf: false,
  canReply: true,
  recalled: false,
  replyTo: undefined,
  messageType: 'text',
  atUsers: undefined,
  mentionNames: () => ({}),
  currentUserId: undefined,
  forwardedFrom: undefined,
  canForward: false,
  canSelect: false,
  edited: false,
  editedAt: undefined,
  editUntil: 0,
  recallUntil: 0,
})

const isMedia = computed(() => isMediaMessageType(props.messageType))
const isCall = computed(() => props.messageType === 'call' && !props.recalled)
/** 表情消息不套气泡，直接显示较大的图片（GIF 原样播放） */
const isSticker = computed(() => isStickerMessageType(props.messageType) && !props.recalled)
/** 合并转发的聊天记录：卡片，点开查看 */
const isChatRecord = computed(() => isChatRecordType(props.messageType) && !props.recalled)
const forwardLabel = computed(() =>
  !props.recalled && props.forwardedFrom?.display_name ? `转发自 ${props.forwardedFrom.display_name}` : '')
const showEdited = computed(() => props.edited && !props.recalled && !isMedia.value)
const canSaveEmoji = computed(() => !props.recalled && canSaveAsEmoji(props.messageType))

const segments = computed(() =>
  splitMentionSegments(props.message, props.atUsers, props.mentionNames))
const mentionsMe = computed(() =>
  !!props.currentUserId && !!props.atUsers && (
    props.atUsers.includes(props.currentUserId)
    || (props.atUsers.includes(MENTION_ALL_ID) && !props.isSelf)
  ))
function segmentMentionsMe(userId?: string): boolean {
  if (!userId) return false
  if (userId === props.currentUserId) return true
  return userId === MENTION_ALL_ID && !props.isSelf
}

const emit = defineEmits<{
  reply: []
  navigate: [reference: ReplyReference]
  saveEmoji: []
  forward: []
  select: []
  edit: []
  recall: []
  redial: [callType: 'video' | 'audio']
}>()

async function copyMessage() {
  if (props.recalled || !props.message || isMedia.value || isStickerMessageType(props.messageType)
    || isChatRecordType(props.messageType)) return
  try {
    await navigator.clipboard.writeText(props.message)
  } catch (error) {
    console.error('复制消息失败:', error)
  }
}
</script>

<template>
  <MessageActions
    :can-reply="canReply && !recalled && !isCall"
    :align="isSelf ? 'end' : 'start'"
    :can-copy="!isMedia && !isCall && !isStickerMessageType(messageType) && !isChatRecordType(messageType) && !recalled"
    :can-save-emoji="canSaveEmoji"
    :can-forward="canForward && !recalled && !isCall"
    :can-select="canSelect"
    :edit-until="recalled || isCall ? 0 : editUntil"
    :recall-until="recalled || isCall ? 0 : recallUntil"
    @edit="emit('edit')"
    @recall="emit('recall')"
    @reply="emit('reply')"
    @copy="copyMessage"
    @save-emoji="emit('saveEmoji')"
    @forward="emit('forward')"
    @select="emit('select')"
  >
    <div
      v-if="isSticker || isChatRecord"
      :class="['flex min-w-0 max-w-full flex-col gap-1.5', isSelf ? 'items-end' : 'items-start']"
    >
      <span v-if="forwardLabel" data-testid="forwarded-label" class="text-[11px] text-slate-400">{{ forwardLabel }}</span>
      <MessageReplyPreview
        v-if="replyTo"
        :reference="replyTo"
        class="max-w-full rounded-lg bg-slate-50 px-2 py-1.5 ring-1 ring-black/[0.04]"
        @navigate="emit('navigate', $event)"
      />
      <ChatRecordCard v-if="isChatRecord" :content="message" :is-self="isSelf" />
      <StickerMessage v-else :message-type="messageType" :content="message" />
    </div>
    <div
      v-else
      :class="[
        'min-w-24 max-w-full rounded-xl px-3 py-2.5 shadow-sm ring-1 ring-black/[0.04]',
        isSelf ? 'rounded-tr-sm bg-blue-50' : 'rounded-tl-sm bg-slate-50',
      ]"
    >
      <div v-if="forwardLabel" data-testid="forwarded-label" class="mb-1 text-[11px] text-slate-400">{{ forwardLabel }}</div>
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
      <CallBubble
        v-else-if="isCall"
        :content="message"
        @redial="emit('redial', $event)"
      />
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
        :data-testid="segmentMentionsMe(segment.userId) ? 'mention-me' : 'mention'"
        :data-user-id="segment.userId"
        :class="[
          'rounded px-0.5 font-medium',
          segmentMentionsMe(segment.userId) ? 'bg-amber-100 text-amber-700' : 'text-blue-600',
        ]"
      >{{ segment.text }}</span><template v-else>{{ segment.text }}</template></template></div>
      <div
        v-if="showEdited"
        data-testid="edited-marker"
        :title="editedTitle(editedAt)"
        :class="['mt-0.5 select-none text-[11px] leading-4 text-slate-400', isSelf ? 'text-right' : 'text-left']"
      >已编辑</div>
    </div>
  </MessageActions>
</template>
