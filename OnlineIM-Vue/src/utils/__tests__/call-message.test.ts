import { describe, expect, it } from 'vitest'
import { callPreviewText, formatCallDuration } from '@/utils/call-message'
import { getReplyPreviewText } from '@/utils/reply-preview'
import { previewOfMessage } from '@/stores/conversation-live'
import type { MessageResponse } from '@/type/message'

describe('call preview text', () => {
  it('matches the server preview labels', () => {
    expect(formatCallDuration(83)).toBe('01:23')
    expect(callPreviewText({ call_type: 'video', call_status: 'completed', duration_seconds: 83 })).toBe('[视频通话] 01:23')
    expect(callPreviewText({ call_type: 'audio', call_status: 'completed', duration_seconds: 45 })).toBe('[语音通话] 00:45')
    expect(callPreviewText({ call_type: 'video', call_status: 'missed' })).toBe('[未接视频通话]')
    expect(callPreviewText({ call_type: 'audio', call_status: 'rejected' })).toBe('[已拒接]')
    expect(callPreviewText({ call_type: 'audio', call_status: 'cancelled' })).toBe('[已取消]')
    expect(callPreviewText({ call_type: 'video', call_status: 'busy' })).toBe('[对方忙线]')
    expect(getReplyPreviewText('call', '{"call_type":"audio","call_status":"missed"}')).toBe('[未接语音通话]')
  })

  it('updates the conversation preview for a call message', () => {
    const message = {
      message_id: 'msg_1',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_a', username: 'a' },
      message_type: 'call',
      content: '{"call_type":"video","call_status":"completed","duration_seconds":83}',
      seq_id: '1',
      status: 1,
      timestamp: '2026-10-26 10:00:00',
      is_recalled: false,
    } as MessageResponse
    expect(previewOfMessage(message)).toBe('[视频通话] 01:23')
  })
})
