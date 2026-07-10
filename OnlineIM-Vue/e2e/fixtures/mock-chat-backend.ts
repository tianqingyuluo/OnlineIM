import type { Page, WebSocketRoute } from '@playwright/test'
import type {
  MessageAckPayload,
  MessageResponse,
  ServerErrorPayload,
} from '../../src/type/message'
import type { Conversation } from '../../src/type/Conversation'
import type { User } from '../../src/type/User'

export interface OutboundFrame {
  type: string
  message: Record<string, unknown>
}

interface MockChatOptions {
  conversationType?: 'private' | 'group'
  history: MessageResponse[]
  contextMessages?: MessageResponse[]
  contextTargetMessageId?: string
  contextUnavailable?: boolean
}

export interface MockChatBackend {
  conversationId: string
  ownUser: User
  peerUser: User
  outboundFrames: OutboundFrame[]
  contextRequestCount: () => number
  sendServerFrame: (type: string, message: Record<string, unknown>) => Promise<void>
  rejectNextMessage: (error: ServerErrorPayload) => void
}

const now = '2026-07-10T08:00:00.000Z'

export function makeMessage(overrides: Partial<MessageResponse> = {}): MessageResponse {
  return {
    message_id: 'msg_default',
    conversation_id: 'conv_reply_e2e',
    sender_info: {
      user_id: 'usr_peer',
      username: 'peer',
      nickname: '小明',
      avatar_url: '',
    },
    message_type: 'text',
    seq_id: '100',
    content: '需要回复的原消息',
    status: 1,
    delivery_state: 'sent',
    timestamp: now,
    is_recalled: false,
    client_message_id: 'client_default',
    ...overrides,
  }
}

export async function installMockChatBackend(
  page: Page,
  options: MockChatOptions,
): Promise<MockChatBackend> {
  const conversationType = options.conversationType ?? 'private'
  const conversationId = conversationType === 'group' ? 'grp_reply_e2e' : 'conv_reply_e2e'
  const ownUser: User = {
    user_id: 'usr_self',
    username: 'self',
    nickname: '小雨',
    avatar_url: '',
  }
  const peerUser: User = {
    user_id: 'usr_peer',
    username: 'peer',
    nickname: '小明',
    avatar_url: '',
  }
  const conversation: Conversation = {
    conversation_id: conversationId,
    type: conversationType,
    target_info: {
      id: conversationType === 'group' ? conversationId : peerUser.user_id,
      name: conversationType === 'group' ? '产品讨论群' : peerUser.nickname,
      avatar_url: '',
    },
    last_message: {
      message_id: options.history.at(-1)?.message_id ?? '',
      content_preview: options.history.at(-1)?.content ?? '',
      timestamp: now,
      is_recalled: false,
    },
    unread_count: 0,
    delivered_seq: '0',
    read_seq: '0',
    latest_seq: options.history.at(-1)?.seq_id ?? '0',
    last_activity_time: now,
  }

  await page.addInitScript(({ user }) => {
    localStorage.setItem('user', JSON.stringify({
      loggedInUser: user,
      token: 'mock-token',
    }))
  }, { user: ownUser })

  let contextRequests = 0
  let socketRoute: WebSocketRoute | null = null
  let nextError: ServerErrorPayload | null = null
  let generatedSequence = 10_000
  const outboundFrames: OutboundFrame[] = []

  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const ok = (body: unknown) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })

    if (path === '/auth/refresh') {
      await ok({ access_token: 'mock-token', expires_in: 3600 })
      return
    }
    if (path === '/users/me') {
      await ok(ownUser)
      return
    }
    if (path === `/users/${peerUser.user_id}`) {
      await ok(peerUser)
      return
    }
    if (path === '/friends/requests/received') {
      await ok([])
      return
    }
    if (path === '/friend-groups') {
      await ok([{ group_id: 'fg_default', name: '我的好友', sort: 0, createdAt: now, friends: [] }])
      return
    }
    if (path === '/friends') {
      await ok({ friends: [], total: 0 })
      return
    }
    if (path === '/groups/joined') {
      await ok(conversationType === 'group' ? [{
        group_id: conversationId,
        name: '产品讨论群',
        owner_id: ownUser.user_id,
        description: '',
        member_count: 2,
        my_role: 'member',
        create_at: now,
      }] : [])
      return
    }
    if (path === '/friends/blacklist' || path === 'friends/blacklist') {
      await ok({ blacklist: [], total: 0 })
      return
    }
    if (path === '/conversations') {
      await ok([conversation])
      return
    }
    if (path === `/conversations/${conversationId}`) {
      await ok(conversation)
      return
    }
    if (path === `/conversations/${conversationId}/read-state`) {
      await ok({
        conversation_id: conversationId,
        delivered_seq: '0',
        read_seq: '0',
        latest_seq: conversation.latest_seq ?? '0',
        unread_count: 0,
      })
      return
    }
    if (path.startsWith(`/conversations/${conversationId}/messages/`) && path.endsWith('/readers')) {
      const messageId = path.split('/')[3]
      await ok({ conversation_id: conversationId, message_id: messageId, seq_id: '0', readers: [] })
      return
    }
    if (path === `/groups/${conversationId}`) {
      await ok({
        group_id: conversationId,
        name: '产品讨论群',
        owner_id: ownUser.user_id,
        description: '',
        member_count: 2,
        my_role: 'member',
        create_at: now,
      })
      return
    }
    if (path === `/groups/${conversationId}/settings`) {
      await ok({
        groupId: conversationId,
        allow_member_invite: true,
        allow_member_modify_name: true,
        allow_member_upload_file: true,
        allow_member_at_all: false,
        allow_view_history_message: true,
        updated_at: now,
      })
      return
    }
    if (path.startsWith(`/messages/${conversationId}/`) && path.endsWith('/context')) {
      contextRequests += 1
      if (options.contextUnavailable) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 'MESSAGE_CONTEXT_UNAVAILABLE',
            message: '原消息不可用',
          }),
        })
        return
      }
      await ok({
        target_message_id: options.contextTargetMessageId ?? '',
        messages: options.contextMessages ?? [],
        has_more_before: false,
        has_more_after: false,
      })
      return
    }
    if (path === `/messages/${conversationId}`) {
      await ok({
        messages: options.history.map(message => ({ ...message, conversation_id: conversationId })),
        has_more_before: false,
        has_more_after: false,
      })
      return
    }
    if (path.startsWith('/messages/sync/')) {
      await ok([])
      return
    }

    await ok({})
  })

  await page.routeWebSocket('ws://localhost:8081/**', ws => {
    socketRoute = ws
    ws.onMessage(rawMessage => {
      const parsed = JSON.parse(String(rawMessage)) as OutboundFrame
      outboundFrames.push(parsed)
      if (parsed.type === 'HEARTBEAT') {
        ws.send(JSON.stringify({ type: 'HEARTBEAT_ACK', message: {} }))
        return
      }
      if (parsed.type !== 'PRIVATE_MESSAGE_REQUEST' && parsed.type !== 'GROUP_MESSAGE_REQUEST') return

      const clientMessageId = String(parsed.message.client_message_id ?? '')
      if (nextError) {
        ws.send(JSON.stringify({
          type: 'ERROR',
          message: {
            ...nextError,
            client_message_id: clientMessageId,
          },
        }))
        nextError = null
        return
      }

      generatedSequence += 1
      const ack: MessageAckPayload = {
        client_message_id: clientMessageId,
        message_id: `msg_sent_${generatedSequence}`,
        conversation_id: conversationId,
        seq_id: String(generatedSequence),
        delivery_state: 'sent',
        server_time: Date.now(),
      }
      ws.send(JSON.stringify({ type: 'MESSAGE_ACK', message: ack }))
    })
  })

  return {
    conversationId,
    ownUser,
    peerUser,
    outboundFrames,
    contextRequestCount: () => contextRequests,
    sendServerFrame: async (type, message) => {
      await page.waitForFunction(() => document.readyState === 'complete')
      if (!socketRoute) throw new Error('WebSocket 尚未连接')
      socketRoute.send(JSON.stringify({ type, message }))
    },
    rejectNextMessage: error => {
      nextError = error
    },
  }
}
