import api from '@/services/api.service'
import type { CustomEmoji, EmojiLimits } from '@/utils/sticker-message'

export interface EmojiListResponse extends EmojiLimits {
  emojis: CustomEmoji[]
  total: number
}

export async function listEmojis(): Promise<EmojiListResponse> {
  const response = await api.get('/emojis')
  return response.data
}

export async function uploadEmoji(file: File, signal?: AbortSignal): Promise<CustomEmoji> {
  const form = new FormData()
  form.append('file', file, file.name)
  const response = await api.post('/emojis', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 2 * 60 * 1000,
    signal,
  })
  return response.data
}

export async function saveEmojiFromMessage(conversationId: string, messageId: string): Promise<CustomEmoji> {
  const response = await api.post('/emojis/from-message', {
    conversation_id: conversationId,
    message_id: messageId,
  })
  return response.data
}

export async function deleteEmoji(emojiId: string): Promise<void> {
  await api.delete(`/emojis/${encodeURIComponent(emojiId)}`)
}

export async function reorderEmojis(emojiIds: string[]): Promise<EmojiListResponse> {
  const response = await api.put('/emojis/order', { emoji_ids: emojiIds })
  return response.data
}
