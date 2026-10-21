import { describe, expect, it } from 'vitest'
import {
  canSaveAsEmoji,
  isStickerMessageType,
  moveToFront,
  parseStickerContent,
  stickerDisplaySize,
  validateEmojiFile,
} from '@/utils/sticker-message'
import { previewOfMessage } from '@/stores/conversation-live'
import { getReplyPreviewText } from '@/utils/reply-preview'

const emoji = {
  emoji_id: 'emo_0123456789abcdef0123456789abcdef',
  type: 'emoji',
  url: '/onlineim/chats/emojis/202610/0123456789abcdef0123456789abcdef.gif',
  name: '跳舞',
  mime: 'image/gif',
  size: 2048,
  width: 240,
  height: 120,
  animated: true,
}

function file(name: string, type: string, size: number): File {
  const f = new File(['x'], name, { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

describe('sticker message utils', () => {
  it('recognises emoji/sticker types and which messages can be saved', () => {
    expect(isStickerMessageType('emoji')).toBe(true)
    expect(isStickerMessageType('sticker')).toBe(true)
    expect(isStickerMessageType('image')).toBe(false)
    expect(canSaveAsEmoji('image')).toBe(true)
    expect(canSaveAsEmoji('emoji')).toBe(true)
    expect(canSaveAsEmoji('file')).toBe(false)
    expect(canSaveAsEmoji('text')).toBe(false)
  })

  it('parses JSON content and rejects unsafe urls', () => {
    expect(parseStickerContent('emoji', JSON.stringify(emoji))?.emoji_id).toBe(emoji.emoji_id)
    expect(parseStickerContent('sticker', emoji)?.url).toBe(emoji.url)
    expect(parseStickerContent('emoji', JSON.stringify({ ...emoji, url: 'javascript:alert(1)' }))).toBeNull()
    expect(parseStickerContent('emoji', 'emo_x')).toBeNull()
    expect(parseStickerContent('emoji', '{bad')).toBeNull()
    expect(parseStickerContent('image', JSON.stringify(emoji))).toBeNull()
  })

  it('keeps aspect ratio within 140px and never upscales', () => {
    expect(stickerDisplaySize(240, 120)).toEqual({ width: 140, height: 70 })
    expect(stickerDisplaySize(64, 64)).toEqual({ width: 64, height: 64 })
    expect(stickerDisplaySize()).toEqual({ width: 140, height: 140 })
  })

  it('validates files before upload', () => {
    expect(validateEmojiFile(file('a.png', 'image/png', 1000))).toBeNull()
    expect(validateEmojiFile(file('a.jpg', 'image/jpeg', 5 * 1024 * 1024))).toBeNull()
    expect(validateEmojiFile(file('a.gif', 'image/gif', 3 * 1024 * 1024))).toContain('动图')
    expect(validateEmojiFile(file('a.webp', 'image/webp', 1000))).toContain('PNG')
    expect(validateEmojiFile(file('a.png', 'image/png', 0))).toContain('空')
    expect(validateEmojiFile(file('a.png', 'image/png', 11 * 1024 * 1024))).toContain('10MB')
  })

  it('moves an emoji to the front', () => {
    expect(moveToFront(['a', 'b', 'c'], 'c')).toEqual(['c', 'a', 'b'])
    expect(moveToFront(['a', 'b'], 'x')).toEqual(['a', 'b'])
  })

  it('previews emoji messages as [表情] in conversations and quotes', () => {
    const message = { message_id: 'm', message_type: 'emoji', content: JSON.stringify(emoji) } as never
    expect(previewOfMessage(message)).toBe('[表情]')
    expect(previewOfMessage({ message_id: 'm', message_type: 'sticker', content: '{}' } as never)).toBe('[表情]')
    expect(getReplyPreviewText('emoji', JSON.stringify(emoji))).toBe('[表情]')
  })
})
