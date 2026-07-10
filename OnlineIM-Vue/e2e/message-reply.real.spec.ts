import { expect, test, type APIRequestContext } from '@playwright/test'

interface AuthSession {
  token: string
  userId: string
  username: string
}

interface ProtocolFrame {
  type: string
  message?: Record<string, unknown>
  [key: string]: unknown
}

class RealSocketClient {
  private readonly socket: WebSocket
  readonly frames: ProtocolFrame[] = []

  private constructor(socket: WebSocket) {
    this.socket = socket
    socket.addEventListener('message', event => {
      this.frames.push(JSON.parse(String(event.data)) as ProtocolFrame)
    })
  }

  static async connect(token: string): Promise<RealSocketClient> {
    const socket = new WebSocket(`ws://127.0.0.1:8081/api/v1/chat?token=Bearer%20${token}`)
    const client = new RealSocketClient(socket)
    await new Promise<void>((resolve, reject) => {
      const timeoutId = setTimeout(() => reject(new Error('WebSocket 连接超时')), 8_000)
      socket.addEventListener('open', () => {
        clearTimeout(timeoutId)
        resolve()
      }, { once: true })
      socket.addEventListener('error', () => {
        clearTimeout(timeoutId)
        reject(new Error('WebSocket 连接失败'))
      }, { once: true })
    })
    return client
  }

  checkpoint(): number {
    return this.frames.length
  }

  send(type: string, message: Record<string, unknown>): void {
    this.socket.send(JSON.stringify({ type, message }))
  }

  async waitFor(
    type: string,
    predicate: (frame: ProtocolFrame) => boolean = () => true,
    startIndex = 0,
  ): Promise<ProtocolFrame> {
    const deadline = Date.now() + 10_000
    while (Date.now() < deadline) {
      const frame = this.frames.slice(startIndex)
        .find(candidate => candidate.type === type && predicate(candidate))
      if (frame) return frame
      await new Promise(resolve => setTimeout(resolve, 25))
    }
    throw new Error(`等待 WebSocket 帧超时: ${type}`)
  }

  close(): void {
    this.socket.close()
  }
}

async function registerAndLogin(
  request: APIRequestContext,
  prefix: string,
): Promise<AuthSession> {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 10_000)}`
  const username = `${prefix}${suffix}`.slice(0, 16)
  const password = 'reply123'
  const register = await request.post('http://127.0.0.1:8080/api/v1/auth/register', {
    data: { username, password, nickname: prefix },
  })
  expect(register.status()).toBe(201)

  const login = await request.post('http://127.0.0.1:8080/api/v1/auth/login', {
    data: { username, password, device_id: `e2e-${prefix}` },
  })
  expect(login.ok()).toBe(true)
  const body = await login.json() as {
    access_token: string
    user_info: { user_id: string }
  }
  return { token: body.access_token, userId: body.user_info.user_id, username }
}

function authHeaders(session: AuthSession): Record<string, string> {
  return { Authorization: `Bearer ${session.token}` }
}

function framePayload(frame: ProtocolFrame): Record<string, unknown> {
  return frame.message && typeof frame.message === 'object' ? frame.message : frame
}

const runRealIntegration = process.env.ONLINEIM_E2E_REAL === '1'

test.describe('消息回复真实中间件与 WebSocket 集成', () => {
  test.skip(!runRealIntegration, '设置 ONLINEIM_E2E_REAL=1 并启动 MySQL/MongoDB/Redis/后端后运行')

  test('Mongo 快照、Redis 实时投递、上下文、撤回竞态和幂等重试保持一致', async ({ request }) => {
    const userA = await registerAndLogin(request, 'rpa')
    const userB = await registerAndLogin(request, 'rpb')
    const createConversation = await request.post(
      'http://127.0.0.1:8080/api/v1/conversations/create',
      {
        headers: authHeaders(userA),
        data: { target_id: userB.userId, type: 'private' },
      },
    )
    expect(createConversation.ok()).toBe(true)
    const conversation = await createConversation.json() as { conversation_id: string }
    const conversationId = conversation.conversation_id
    expect(conversationId).toMatch(/^conv_/)

    let socketA = await RealSocketClient.connect(userA.token)
    const socketB = await RealSocketClient.connect(userB.token)

    try {
      const targetClientId = `target-${Date.now()}`
      const aTargetStart = socketA.checkpoint()
      const bTargetStart = socketB.checkpoint()
      socketB.send('PRIVATE_MESSAGE_REQUEST', {
        conversation_id: conversationId,
        receiver_id: userA.userId,
        message_type: 'text',
        content: '真实集成中的引用目标',
        client_message_id: targetClientId,
      })

      const targetAck = framePayload(await socketB.waitFor(
        'MESSAGE_ACK',
        frame => framePayload(frame).client_message_id === targetClientId,
        bTargetStart,
      ))
      const targetMessageId = String(targetAck.message_id)
      expect(targetMessageId).toMatch(/^msg_/)

      const targetRealtime = framePayload(await socketA.waitFor(
        'PRIVATE_MESSAGE_RESPONSE',
        frame => framePayload(frame).message_id === targetMessageId,
        aTargetStart,
      ))
      expect(targetRealtime.content).toBe('真实集成中的引用目标')

      const replyClientId = `reply-${Date.now()}`
      const aReplyStart = socketA.checkpoint()
      const bReplyStart = socketB.checkpoint()
      socketA.send('PRIVATE_MESSAGE_REQUEST', {
        conversation_id: conversationId,
        receiver_id: userB.userId,
        message_type: 'text',
        content: '真实集成中的引用回复',
        client_message_id: replyClientId,
        reply_to_message_id: targetMessageId,
      })

      const replyAck = framePayload(await socketA.waitFor(
        'MESSAGE_ACK',
        frame => framePayload(frame).client_message_id === replyClientId,
        aReplyStart,
      ))
      const replyMessageId = String(replyAck.message_id)
      const replyRealtime = framePayload(await socketB.waitFor(
        'PRIVATE_MESSAGE_RESPONSE',
        frame => framePayload(frame).message_id === replyMessageId,
        bReplyStart,
      ))
      expect(replyRealtime.reply_to).toMatchObject({
        message_id: targetMessageId,
        preview_text: '真实集成中的引用目标',
        state: 'active',
      })

      const httpReplyClientId = `http-reply-${Date.now()}`
      const bHttpReplyStart = socketB.checkpoint()
      const httpReplyRequest = {
        target_id: userB.userId,
        message_type: 'text',
        content: '真实 HTTP 引用回复',
        client_message_id: httpReplyClientId,
        reply_to_message_id: targetMessageId,
      }
      const httpReply = await request.post(
        'http://127.0.0.1:8080/api/v1/messages/send',
        { headers: authHeaders(userA), data: httpReplyRequest },
      )
      expect(httpReply.ok()).toBe(true)
      const httpReplyBody = await httpReply.json() as Record<string, unknown>
      const httpReplyMessageId = String(httpReplyBody.message_id)
      expect(httpReplyBody).toMatchObject({
        conversation_id: conversationId,
        client_message_id: httpReplyClientId,
        reply_to: {
          message_id: targetMessageId,
          preview_text: '真实集成中的引用目标',
          state: 'active',
        },
      })
      const httpReplyRealtime = framePayload(await socketB.waitFor(
        'PRIVATE_MESSAGE_RESPONSE',
        frame => framePayload(frame).message_id === httpReplyMessageId,
        bHttpReplyStart,
      ))
      expect(httpReplyRealtime.client_message_id).toBe(httpReplyClientId)

      const duplicateHttpReply = await request.post(
        'http://127.0.0.1:8080/api/v1/messages/send',
        { headers: authHeaders(userA), data: httpReplyRequest },
      )
      expect(duplicateHttpReply.ok()).toBe(true)
      expect((await duplicateHttpReply.json() as Record<string, unknown>).message_id)
        .toBe(httpReplyMessageId)

      const historyBeforeRecall = await request.get(
        `http://127.0.0.1:8080/api/v1/messages/${conversationId}?size=50`,
        { headers: authHeaders(userB) },
      )
      expect(historyBeforeRecall.ok()).toBe(true)
      const historyBeforeBody = await historyBeforeRecall.json() as { messages: Array<Record<string, unknown>> }
      expect(historyBeforeBody.messages.find(message => message.message_id === replyMessageId)?.reply_to)
        .toMatchObject({ message_id: targetMessageId, state: 'active' })
      expect(historyBeforeBody.messages.filter(message => message.client_message_id === httpReplyClientId))
        .toHaveLength(1)

      const context = await request.get(
        `http://127.0.0.1:8080/api/v1/messages/${conversationId}/${targetMessageId}/context?before=20&after=20`,
        { headers: authHeaders(userA) },
      )
      expect(context.ok()).toBe(true)
      const contextBody = await context.json() as {
        target_message_id: string
        messages: Array<Record<string, unknown>>
      }
      expect(contextBody.target_message_id).toBe(targetMessageId)
      expect(contextBody.messages.some(message => message.message_id === targetMessageId)).toBe(true)
      expect(contextBody.messages.some(message => message.message_id === replyMessageId)).toBe(true)

      socketA.close()
      socketA = await RealSocketClient.connect(userA.token)
      const duplicateStart = socketA.checkpoint()
      socketA.send('PRIVATE_MESSAGE_REQUEST', {
        conversation_id: conversationId,
        receiver_id: userB.userId,
        message_type: 'text',
        content: '真实集成中的引用回复',
        client_message_id: replyClientId,
        reply_to_message_id: targetMessageId,
      })
      const duplicateAck = framePayload(await socketA.waitFor(
        'MESSAGE_ACK',
        frame => framePayload(frame).client_message_id === replyClientId,
        duplicateStart,
      ))
      expect(duplicateAck.message_id).toBe(replyMessageId)

      const recall = await request.delete(
        `http://127.0.0.1:8080/api/v1/messages/${targetMessageId}`,
        { headers: authHeaders(userB) },
      )
      expect(recall.ok()).toBe(true)

      const historyAfterRecall = await request.get(
        `http://127.0.0.1:8080/api/v1/messages/${conversationId}?size=50`,
        { headers: authHeaders(userA) },
      )
      expect(historyAfterRecall.ok()).toBe(true)
      const historyAfterBody = await historyAfterRecall.json() as { messages: Array<Record<string, unknown>> }
      const storedReply = historyAfterBody.messages.find(message => message.message_id === replyMessageId)
      expect(storedReply?.reply_to).toMatchObject({
        message_id: targetMessageId,
        preview_text: null,
        state: 'recalled',
      })
      expect(historyAfterBody.messages.find(message => message.message_id === httpReplyMessageId)?.reply_to)
        .toMatchObject({
          message_id: targetMessageId,
          preview_text: null,
          state: 'recalled',
        })

      const historyCountBeforeReject = historyAfterBody.messages.length
      const rejectedHttpClientId = `http-race-${Date.now()}`
      const rejectedHttpReply = await request.post(
        'http://127.0.0.1:8080/api/v1/messages/send',
        {
          headers: authHeaders(userA),
          data: {
            target_id: userB.userId,
            message_type: 'text',
            content: '不应落库的 HTTP 竞态回复',
            client_message_id: rejectedHttpClientId,
            reply_to_message_id: targetMessageId,
          },
        },
      )
      expect(rejectedHttpReply.status()).toBe(409)
      expect(await rejectedHttpReply.json()).toMatchObject({ code: 'REPLY_TARGET_UNAVAILABLE' })

      const raceClientId = `race-${Date.now()}`
      const raceStart = socketA.checkpoint()
      socketA.send('PRIVATE_MESSAGE_REQUEST', {
        conversation_id: conversationId,
        receiver_id: userB.userId,
        message_type: 'text',
        content: '不应落库的竞态回复',
        client_message_id: raceClientId,
        reply_to_message_id: targetMessageId,
      })
      const raceError = framePayload(await socketA.waitFor(
        'ERROR',
        frame => framePayload(frame).client_message_id === raceClientId,
        raceStart,
      ))
      expect(raceError.code).toBe('REPLY_TARGET_UNAVAILABLE')
      expect(socketA.frames.slice(raceStart).some(frame =>
        frame.type === 'MESSAGE_ACK' && framePayload(frame).client_message_id === raceClientId,
      )).toBe(false)

      const historyAfterReject = await request.get(
        `http://127.0.0.1:8080/api/v1/messages/${conversationId}?size=50`,
        { headers: authHeaders(userA) },
      )
      const historyAfterRejectBody = await historyAfterReject.json() as { messages: Array<Record<string, unknown>> }
      expect(historyAfterRejectBody.messages).toHaveLength(historyCountBeforeReject)
      expect(historyAfterRejectBody.messages.some(message => message.client_message_id === rejectedHttpClientId)).toBe(false)
      expect(historyAfterRejectBody.messages.some(message => message.client_message_id === raceClientId)).toBe(false)

      const createGroup = await request.post('http://127.0.0.1:8080/api/v1/groups', {
        headers: authHeaders(userA),
        data: {
          name: `真实回复群${Date.now()}`.slice(0, 32),
          description: '消息回复真实集成测试',
          initial_members: [userA.userId, userB.userId],
          max_members: 10,
        },
      })
      expect(createGroup.ok()).toBe(true)
      const group = await createGroup.json() as { group_id: string }
      const groupId = group.group_id
      expect(groupId).toMatch(/^grp_/)

      const groupTargetClientId = `group-target-${Date.now()}`
      const aGroupTargetStart = socketA.checkpoint()
      const bGroupTargetStart = socketB.checkpoint()
      socketB.send('GROUP_MESSAGE_REQUEST', {
        group_id: groupId,
        message_type: 'text',
        content: '真实群聊引用目标',
        client_message_id: groupTargetClientId,
      })
      const groupTargetAck = framePayload(await socketB.waitFor(
        'MESSAGE_ACK',
        frame => framePayload(frame).client_message_id === groupTargetClientId,
        bGroupTargetStart,
      ))
      const groupTargetMessageId = String(groupTargetAck.message_id)
      await socketA.waitFor(
        'GROUP_MESSAGE_RESPONSE',
        frame => framePayload(frame).message_id === groupTargetMessageId,
        aGroupTargetStart,
      )

      const groupReplyClientId = `group-reply-${Date.now()}`
      const aGroupReplyStart = socketA.checkpoint()
      const bGroupReplyStart = socketB.checkpoint()
      socketA.send('GROUP_MESSAGE_REQUEST', {
        group_id: groupId,
        message_type: 'text',
        content: '真实群聊引用回复',
        client_message_id: groupReplyClientId,
        reply_to_message_id: groupTargetMessageId,
      })
      const groupReplyAck = framePayload(await socketA.waitFor(
        'MESSAGE_ACK',
        frame => framePayload(frame).client_message_id === groupReplyClientId,
        aGroupReplyStart,
      ))
      const groupReplyMessageId = String(groupReplyAck.message_id)
      const groupReplyRealtime = framePayload(await socketB.waitFor(
        'GROUP_MESSAGE_RESPONSE',
        frame => framePayload(frame).message_id === groupReplyMessageId,
        bGroupReplyStart,
      ))
      expect(groupReplyRealtime.reply_to).toMatchObject({
        message_id: groupTargetMessageId,
        preview_text: '真实群聊引用目标',
        state: 'active',
      })
    } finally {
      socketA.close()
      socketB.close()
    }
  })
})
