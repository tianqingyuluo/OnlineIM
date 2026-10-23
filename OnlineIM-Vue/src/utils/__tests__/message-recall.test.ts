import { describe, expect, it } from 'vitest'
import { toServerTimestamp } from '@/utils/conversation-time'
import {
  RECALL_WINDOW_MS,
  conversationPreviewText,
  isRecallable,
  recallErrorText,
  recallNoticeText,
  recallUntil,
} from '@/utils/message-recall'

const base = (extra: Record<string, unknown> = {}) => ({
  message_id: 'msg_1',
  sender_info: { user_id: 'usr_me' },
  status: 1,
  delivery_state: 'sent' as const,
  is_recalled: false,
  timestamp: toServerTimestamp(Date.now() - 30_000),
  ...extra,
})

describe('message recall rules', () => {
  it('only own, delivered, unrecalled messages inside the 2-minute window are recallable (any type)', () => {
    expect(isRecallable(base() as never, 'usr_me')).toBe(true)
    expect(isRecallable(base({ message_type: 'image' }) as never, 'usr_me')).toBe(true)
    expect(isRecallable(base() as never, 'usr_other')).toBe(false)
    expect(isRecallable(base() as never, 'usr_owner', Date.now(), { allowOthers: true })).toBe(true)
    expect(isRecallable(base({ timestamp: toServerTimestamp(Date.now() - RECALL_WINDOW_MS - 5_000) }) as never, 'usr_owner', Date.now(), { allowOthers: true })).toBe(false)
    expect(isRecallable(base({ status: 3 }) as never, 'usr_me')).toBe(false)
    expect(isRecallable(base({ is_recalled: true }) as never, 'usr_me')).toBe(false)
    expect(isRecallable(base({ delivery_state: 'sending' }) as never, 'usr_me')).toBe(false)
    expect(isRecallable(base({ delivery_state: 'failed' }) as never, 'usr_me')).toBe(false)
    expect(isRecallable(base({ message_id: 'local_abc' }) as never, 'usr_me')).toBe(false)
    expect(isRecallable(base({ timestamp: toServerTimestamp(Date.now() - RECALL_WINDOW_MS - 5_000) }) as never, 'usr_me')).toBe(false)
  })

  it('recallUntil is the send time plus the window, 0 when not recallable at all', () => {
    const sentAt = Date.now() - 10_000
    const until = recallUntil(base({ timestamp: toServerTimestamp(sentAt) }) as never, 'usr_me')
    expect(Math.abs(until - (sentAt + RECALL_WINDOW_MS))).toBeLessThan(1000)
    expect(recallUntil(base() as never, 'usr_other')).toBe(0)
    expect(recallUntil(base({ is_recalled: true }) as never, 'usr_me')).toBe(0)
  })

  it('notice and error texts', () => {
    expect(recallNoticeText(true, 'Alice')).toBe('你撤回了一条消息')
    expect(recallNoticeText(false, 'Alice')).toBe('Alice 撤回了一条消息')
    expect(recallNoticeText(false, '  ')).toBe('对方 撤回了一条消息')
    expect(recallNoticeText(false, 'Alice', 'owner')).toBe('群主撤回了一条消息')
    expect(recallNoticeText(false, 'Alice', 'admin')).toBe('管理员撤回了一条消息')
    expect(recallNoticeText(true, 'Alice', 'admin')).toBe('你撤回了一条消息')
    expect(recallErrorText({ status: 400, message: '消息发送已超过2分钟，无法撤回' })).toBeNull()
    expect(recallErrorText({ status: 403 })).toBeNull()
    expect(recallErrorText({ status: 409, code: 'MESSAGE_ALREADY_RECALLED' })).toBe('消息已撤回')
    expect(recallErrorText({ status: 404, code: 'MESSAGE_NOT_FOUND' })).toBe('消息不存在或已被删除')
    expect(recallErrorText({ status: 500, message: 'boom' })).toBe('撤回失败，请重试')
  })

  it('conversation preview: recall wording and group sender prefix', () => {
    const priv = (last: Record<string, unknown>) => ({ type: 'private' as const, target_info: { id: 'usr_b', name: '老王' }, last_message: { message_id: 'm', timestamp: '', is_recalled: false, content_preview: '', ...last } })
    const grp = (last: Record<string, unknown>) => ({ type: 'group' as const, target_info: { id: 'grp_1', name: '群' }, last_message: { message_id: 'm', timestamp: '', is_recalled: false, content_preview: '', ...last } })
    expect(conversationPreviewText(priv({ is_recalled: true, sender_id: 'usr_me', content_preview: '[消息已撤回]' }) as never, 'usr_me')).toBe('你撤回了一条消息')
    expect(conversationPreviewText(priv({ message_type: 'recalled', sender_id: 'usr_b', sender_nickname: 'Bob', content_preview: '[消息已撤回]' }) as never, 'usr_me')).toBe('老王 撤回了一条消息')
    expect(conversationPreviewText(grp({ is_recalled: true, sender_id: 'usr_c', sender_nickname: 'Cid', content_preview: '[消息已撤回]' }) as never, 'usr_me')).toBe('Cid 撤回了一条消息')
    expect(conversationPreviewText(grp({ content_preview: '[消息已撤回]' }) as never, 'usr_me')).toBe('[消息已撤回]')
    expect(conversationPreviewText(grp({ sender_id: 'usr_c', sender_nickname: 'Cid', content_preview: '大家好' }) as never, 'usr_me')).toBe('Cid: 大家好')
    expect(conversationPreviewText(grp({ sender_id: 'usr_me', sender_nickname: '我自己', content_preview: '大家好' }) as never, 'usr_me')).toBe('大家好')
    expect(conversationPreviewText(priv({ sender_id: 'usr_b', sender_nickname: 'Bob', content_preview: '在吗' }) as never, 'usr_me')).toBe('在吗')
  })
})
