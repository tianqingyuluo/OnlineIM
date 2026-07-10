import { expect, test } from '@playwright/test'
import {
  installMockChatBackend,
  makeMessage,
  type MockChatBackend,
} from './fixtures/mock-chat-backend'

async function openConversation(
  page: Parameters<typeof installMockChatBackend>[0],
  backend: MockChatBackend,
  type: 'private' | 'group' = 'private',
) {
  await page.goto(`/main/chat/${type}/${backend.conversationId}`)
  await expect(page.getByPlaceholder('输入消息...')).toBeVisible()
}

function messageNode(page: Parameters<typeof installMockChatBackend>[0], messageId: string) {
  return page.locator(`[data-message-id="${messageId}"]`)
}

test.describe('消息回复 UI 用户旅程（Mock HTTP/WS 传输层）', () => {
  test('私聊引用回复保留本地临时态、发送目标并在 ACK 后清除引用条', async ({ page }) => {
    const target = makeMessage({ message_id: 'msg_private_target', client_message_id: 'client_target' })
    const backend = await installMockChatBackend(page, { history: [target] })
    await openConversation(page, backend)

    const targetNode = messageNode(page, target.message_id)
    await targetNode.hover()
    await targetNode.getByTestId('quick-reply').click()
    await expect(page.getByText('回复 小明')).toBeVisible()

    await page.getByPlaceholder('输入消息...').fill('这是引用回复')
    await page.getByRole('button', { name: '发送', exact: true }).click()

    const sentNode = page.locator('[data-message-id^="msg_sent_"]').filter({ hasText: '这是引用回复' })
    await expect(sentNode).toBeVisible()
    await expect(sentNode.getByTestId('reply-preview')).toContainText('需要回复的原消息')
    await expect(page.getByTestId('cancel-reply')).toHaveCount(0)

    const sentFrame = backend.outboundFrames.find(frame => frame.type === 'PRIVATE_MESSAGE_REQUEST')
    expect(sentFrame?.message.reply_to_message_id).toBe(target.message_id)
    expect(sentFrame?.message.content).toBe('这是引用回复')
  })

  test('群聊回复一条引用消息时只保留直接引用的一层', async ({ page }) => {
    const first = makeMessage({
      message_id: 'msg_group_first',
      conversation_id: 'grp_reply_e2e',
      seq_id: '101',
      content: '第一层原消息',
      client_message_id: 'client_group_first',
    })
    const second = makeMessage({
      message_id: 'msg_group_second',
      conversation_id: 'grp_reply_e2e',
      seq_id: '102',
      content: '第二层被回复消息',
      client_message_id: 'client_group_second',
      reply_to: {
        message_id: first.message_id,
        seq_id: first.seq_id,
        sender_id: first.sender_info.user_id,
        sender_display_name: '小明',
        message_type: 'text',
        preview_text: first.content,
        state: 'active',
      },
    })
    const backend = await installMockChatBackend(page, {
      conversationType: 'group',
      history: [first, second],
    })
    await openConversation(page, backend, 'group')

    const secondNode = messageNode(page, second.message_id)
    await secondNode.hover()
    await secondNode.getByTestId('quick-reply').click()
    await page.getByPlaceholder('输入消息...').fill('第三层回复')
    await page.getByRole('button', { name: '发送', exact: true }).click()

    const sentNode = page.locator('[data-message-id^="msg_sent_"]').filter({ hasText: '第三层回复' })
    await expect(sentNode.getByTestId('reply-preview')).toHaveCount(1)
    await expect(sentNode.getByTestId('reply-preview')).toContainText('第二层被回复消息')
    await expect(sentNode).not.toContainText('第一层原消息')

    const sentFrame = backend.outboundFrames.find(frame => frame.type === 'GROUP_MESSAGE_REQUEST')
    expect(sentFrame?.message.reply_to_message_id).toBe(second.message_id)
  })

  test('目标撤回竞态被服务端拒绝后恢复正文和引用上下文', async ({ page }) => {
    const target = makeMessage({ message_id: 'msg_race_target', client_message_id: 'client_race_target' })
    const backend = await installMockChatBackend(page, { history: [target] })
    await openConversation(page, backend)

    const targetNode = messageNode(page, target.message_id)
    await targetNode.hover()
    await targetNode.getByTestId('quick-reply').click()
    await backend.sendServerFrame('MESSAGE_RECALLED', {
      message_id: target.message_id,
      conversation_id: backend.conversationId,
      recall_user_id: backend.peerUser.user_id,
    })
    await expect(page.getByText('原消息已撤回')).toBeVisible()

    backend.rejectNextMessage({
      code: 'REPLY_TARGET_UNAVAILABLE',
      message: '原消息已不可用，请取消引用后重新发送',
    })
    await page.getByPlaceholder('输入消息...').fill('竞态失败后要恢复的正文')
    await page.getByRole('button', { name: '发送', exact: true }).click()

    await expect(page.getByPlaceholder('输入消息...')).toHaveValue('竞态失败后要恢复的正文')
    await expect(page.getByText('回复 小明')).toBeVisible()
    await expect(page.getByText('原消息已撤回')).toHaveCount(2)
    await expect(page.getByText('点击重试')).toBeVisible()
  })

  test('目标不在当前分页时只请求一次上下文并定位高亮', async ({ page }) => {
    const target = makeMessage({
      message_id: 'msg_context_target',
      seq_id: '10',
      content: '很久以前的目标消息',
      client_message_id: 'client_context_target',
    })
    const reply = makeMessage({
      message_id: 'msg_context_reply',
      seq_id: '200',
      content: '当前分页中的引用消息',
      client_message_id: 'client_context_reply',
      reply_to: {
        message_id: target.message_id,
        seq_id: target.seq_id,
        sender_id: target.sender_info.user_id,
        sender_display_name: '小明',
        message_type: 'text',
        preview_text: target.content,
        state: 'active',
      },
    })
    const backend = await installMockChatBackend(page, {
      history: [reply],
      contextTargetMessageId: target.message_id,
      contextMessages: [target, reply],
    })
    await openConversation(page, backend)

    await messageNode(page, reply.message_id).getByTestId('reply-preview').click()

    await expect(messageNode(page, target.message_id)).toBeVisible()
    await expect(messageNode(page, target.message_id)).toHaveClass(/reply-focus-highlight/)
    expect(backend.contextRequestCount()).toBe(1)
  })

  test('目标不存在或无权限时保持滚动位置并提示不可用', async ({ page }) => {
    const reply = makeMessage({
      message_id: 'msg_unavailable_reply',
      seq_id: '300',
      content: '引用目标已经不可访问',
      client_message_id: 'client_unavailable_reply',
      reply_to: {
        message_id: 'msg_unavailable_target',
        seq_id: '20',
        sender_id: 'usr_unknown',
        sender_display_name: '旧成员',
        message_type: 'text',
        preview_text: '不应在失败提示后继续展开的旧内容',
        state: 'active',
      },
    })
    const backend = await installMockChatBackend(page, {
      history: [reply],
      contextUnavailable: true,
    })
    await openConversation(page, backend)

    const scrollArea = page.getByTestId('message-scroll')
    const scrollTopBefore = await scrollArea.evaluate(element => element.scrollTop)
    await messageNode(page, reply.message_id).getByTestId('reply-preview').click()

    await expect(page.getByText('原消息不可用')).toBeVisible()
    expect(await scrollArea.evaluate(element => element.scrollTop)).toBe(scrollTopBefore)
    expect(backend.contextRequestCount()).toBe(1)
  })

  test('手动重试沿用同一 client_message_id 与引用目标', async ({ page }) => {
    const target = makeMessage({ message_id: 'msg_retry_target', client_message_id: 'client_retry_target' })
    const backend = await installMockChatBackend(page, { history: [target] })
    await openConversation(page, backend)

    const targetNode = messageNode(page, target.message_id)
    await targetNode.hover()
    await targetNode.getByTestId('quick-reply').click()
    backend.rejectNextMessage({ code: 'TEMPORARY_FAILURE', message: '临时失败' })
    await page.getByPlaceholder('输入消息...').fill('需要重试的引用回复')
    await page.getByRole('button', { name: '发送', exact: true }).click()
    await page.getByText('点击重试').click()

    await expect(page.getByText('已发送')).toBeVisible()
    const frames = backend.outboundFrames.filter(frame => frame.type === 'PRIVATE_MESSAGE_REQUEST')
    expect(frames).toHaveLength(2)
    expect(frames[1].message.client_message_id).toBe(frames[0].message.client_message_id)
    expect(frames[1].message.reply_to_message_id).toBe(target.message_id)
  })
})
