import { describe, expect, it } from 'vitest'
import {
  chatRecordSummaryLines,
  filterForwardTargets,
  formatRecordTime,
  isForwardable,
  parseChatRecord,
  previewOfRecordItem,
  type ForwardTargetOption,
} from '@/utils/chat-record'
import { previewOfMessage } from '@/stores/conversation-live'
import { getReplyPreviewText } from '@/utils/reply-preview'
import type { MessageResponse } from '@/type/message'

const record = {
  title: '小明和小红的聊天记录',
  count: 4,
  items: [
    { sender_id: 'usr_a', sender_name: '小明', message_type: 'text', content: '你好\n世界', timestamp: '2026-10-21T16:05:00.000Z' },
    { sender_id: 'usr_b', sender_name: '小红', message_type: 'image', content: '{"url":"/a.png"}' },
    { sender_id: 'usr_a', sender_name: '小明', message_type: 'file', content: '{"name":"报告.pdf"}' },
    { sender_id: 'usr_b', sender_name: '小红', message_type: 'chat_record', content: '{"title":"x","items":[]}' },
  ],
}

describe('chat record utils', () => {
  it('parses server content and tolerates broken input', () => {
    const parsed = parseChatRecord(JSON.stringify(record))
    expect(parsed?.title).toBe('小明和小红的聊天记录')
    expect(parsed?.items).toHaveLength(4)
    expect(parseChatRecord({ items: [{ message_type: 'text', content: 'x' }, null] })).toMatchObject({ title: '聊天记录', count: 1 })
    expect(parseChatRecord('{broken')).toBeNull()
    expect(parseChatRecord('{"title":"x"}')).toBeNull()
  })

  it('builds the three summary lines shown on the card', () => {
    const lines = chatRecordSummaryLines(parseChatRecord(record)!)
    expect(lines).toEqual(['小明: 你好 世界', '小红: [图片]', '小明: [文件] 报告.pdf'])
    expect(previewOfRecordItem({ message_type: 'chat_record', content: '' })).toBe('[聊天记录]')
    expect(previewOfRecordItem({ message_type: 'voice', content: '' })).toBe('[语音]')
    expect(previewOfRecordItem({ message_type: 'emoji', content: '' })).toBe('[表情]')
  })

  it('previews chat_record in the conversation list and reply quote', () => {
    const message = { message_type: 'chat_record', content: '{}', status: 0, is_recalled: false } as unknown as MessageResponse
    expect(previewOfMessage(message)).toBe('[聊天记录]')
    expect(getReplyPreviewText('chat_record', '{}')).toBe('[聊天记录]')
  })

  it('only allows forwarding delivered, non-recalled, supported messages', () => {
    const base = { message_id: 'msg_1', message_type: 'text', is_recalled: false, status: 0, delivery_state: 'sent' as const }
    expect(isForwardable(base)).toBe(true)
    expect(isForwardable({ ...base, message_type: 'chat_record' })).toBe(true)
    expect(isForwardable({ ...base, is_recalled: true })).toBe(false)
    expect(isForwardable({ ...base, status: '3' })).toBe(false)
    expect(isForwardable({ ...base, delivery_state: 'sending' })).toBe(false)
    expect(isForwardable({ ...base, delivery_state: 'failed' })).toBe(false)
    expect(isForwardable({ ...base, message_id: 'local_abc' })).toBe(false)
    expect(isForwardable({ ...base, message_type: 'system_notice' })).toBe(false)
  })

  it('searches targets by remark, nickname, username or group name', () => {
    const options: ForwardTargetOption[] = [
      { id: 'usr_1', kind: 'friend', label: '老王', keywords: '老王 wang laowang' },
      { id: 'grp_1', kind: 'group', label: '家人群', keywords: '家人群' },
    ]
    expect(filterForwardTargets(options, '  ').map(o => o.id)).toEqual(['usr_1', 'grp_1'])
    expect(filterForwardTargets(options, 'WANG').map(o => o.id)).toEqual(['usr_1'])
    expect(filterForwardTargets(options, '家人').map(o => o.id)).toEqual(['grp_1'])
    expect(filterForwardTargets(options, 'zzz')).toEqual([])
  })

  it('formats record timestamps and keeps unknown text', () => {
    expect(formatRecordTime(undefined)).toBe('')
    expect(formatRecordTime('not a date')).toBe('not a date')
    expect(formatRecordTime('2026-10-21T16:05:00.000Z')).toMatch(/^\d{2}-\d{2} \d{2}:\d{2}$/)
  })
})
