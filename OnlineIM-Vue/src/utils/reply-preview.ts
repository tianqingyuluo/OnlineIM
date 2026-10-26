import type { MessageResponse, ReplyReference } from '@/type/message'
import { callPreviewText } from '@/utils/call-message'
import { formatVoiceClock, parseMediaContent } from '@/utils/media-message'
import { ENCRYPTED_PREVIEW, looksLikeEnvelope } from '@/utils/e2ee-message'

const MAX_PREVIEW_CODE_POINTS = 80

export function getReplyPreviewText(
  messageType: string,
  content: string,
  fileName?: string,
  durationMs?: number,
): string {
  switch (messageType.toLowerCase()) {
    case 'text':
      return truncatePreview(content.trim().replace(/\s+/gu, ' '))
    case 'image':
      return '[图片]'
    case 'voice':
    case 'audio':
      return durationMs && durationMs > 0 ? `[语音 ${formatVoiceClock(durationMs)}]` : '[语音]'
    case 'video':
      return '[视频]'
    case 'emoji':
    case 'sticker':
      return '[表情]'
    case 'file':
      return fileName?.trim() ? `[文件] ${truncatePreview(fileName.trim())}` : '[文件]'
    case 'chat_record':
      return '[聊天记录]'
    case 'call':
      return callPreviewText(content)
    default:
      return '[消息]'
  }
}

export function buildReplyReference(message: MessageResponse): ReplyReference | null {
  if (message.is_recalled || String(message.status) === '3') return null
  return {
    message_id: message.message_id,
    seq_id: message.seq_id,
    sender_id: message.sender_info.user_id,
    sender_display_name:
      message.sender_info.nickname
      || message.sender_info.username
      || message.sender_info.user_id,
    message_type: message.message_type,
    preview_text: replyPreviewOf(message),
    state: 'active',
  }
}

function replyPreviewOf(message: MessageResponse): string {
  const content = typeof message.content === 'string' ? message.content : ''
  if (message.e2ee_status === 'decrypted' && content && !looksLikeEnvelope(content)) {
    return getReplyPreviewText(message.message_type, content)
  }
  if (message.e2ee_status === 'locked' || message.is_encrypted || looksLikeEnvelope(content) || looksLikeEnvelope(message.e2ee_envelope)) {
    return ENCRYPTED_PREVIEW
  }
  return getReplyPreviewText(
    message.message_type,
    content,
    parseMediaContent(message.message_type, message.content)?.name,
    parseMediaContent(message.message_type, message.content)?.duration_ms,
  )
}

function truncatePreview(value: string): string {
  const codePoints = [...value]
  if (codePoints.length <= MAX_PREVIEW_CODE_POINTS) return value
  return `${codePoints.slice(0, MAX_PREVIEW_CODE_POINTS).join('')}…`
}
