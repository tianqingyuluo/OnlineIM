import { describe, expect, it } from 'vitest'
import { buildReplyReference, getReplyPreviewText } from '@/utils/reply-preview'
import type { MessageResponse } from '@/type/message'

function message(overrides: Partial<MessageResponse> = {}): MessageResponse {
  return {
    message_id: 'msg_1',
    conversation_id: 'conv_1',
    sender_info: {
      user_id: 'usr_peer',
      username: 'peer',
      nickname: '小明',
    },
    message_type: 'text',
    seq_id: '100',
    content: 'hello',
    status: 1,
    timestamp: '2026-07-10 18:00:00',
    is_recalled: false,
    client_message_id: 'client_1',
    ...overrides,
  }
}

describe('reply preview', () => {
  it('normalizes whitespace and truncates text by unicode code points', () => {
    const preview = getReplyPreviewText('text', ` 第一行\n\n${'好'.repeat(85)} `)

    expect([...preview]).toHaveLength(81)
    expect(preview.endsWith('…')).toBe(true)
    expect(preview).not.toContain('\n')
  })

  it.each([
    ['image', '[图片]'],
    ['voice', '[语音]'],
    ['audio', '[语音]'],
    ['video', '[视频]'],
    ['emoji', '[表情]'],
    ['sticker', '[表情]'],
    ['unknown', '[消息]'],
  ])('uses stable label for %s', (type, expected) => {
    expect(getReplyPreviewText(type, 'ignored')).toBe(expected)
  })

  it('builds a direct one-level reply reference', () => {
    const reply = buildReplyReference(message({
      content: '正文',
      reply_to: {
        message_id: 'msg_parent',
        seq_id: '99',
        sender_id: 'usr_parent',
        sender_display_name: '上一级',
        message_type: 'text',
        preview_text: '不应递归复制',
        state: 'active',
      },
    }))

    expect(reply).toEqual({
      message_id: 'msg_1',
      seq_id: '100',
      sender_id: 'usr_peer',
      sender_display_name: '小明',
      message_type: 'text',
      preview_text: '正文',
      state: 'active',
    })
    expect(reply).not.toHaveProperty('reply_to')
  })

  it('does not build a reply target from recalled messages', () => {
    expect(buildReplyReference(message({ is_recalled: true, status: 3 }))).toBeNull()
  })
})
