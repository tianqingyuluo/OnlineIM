// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

const names = { usr_me: ['我自己', 'me'], usr_b: ['鲍勃'] }

describe('mention rendering', () => {
  it('highlights @mentions in text bubbles and marks the ones that mention me', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const wrapper = mount(UserTextArea, {
      props: {
        message: '@我自己 和 @鲍勃 看一下，@路人 不算',
        atUsers: ['usr_me', 'usr_b'],
        mentionNames: names,
        currentUserId: 'usr_me',
      },
    })
    expect(wrapper.get('[data-testid="mention-me"]').text()).toBe('@我自己')
    expect(wrapper.get('[data-testid="mention"]').text()).toBe('@鲍勃')
    expect(wrapper.findAll('[data-testid^="mention"]')).toHaveLength(2)
    expect(wrapper.text()).toBe('@我自己 和 @鲍勃 看一下，@路人 不算')
    expect(wrapper.find('[data-mentions-me="true"]').exists()).toBe(true)
  })

  it('renders plain text without at_users and ignores mentions on recalled messages', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const plain = mount(UserTextArea, { props: { message: '@鲍勃 hi', mentionNames: names, currentUserId: 'usr_me' } })
    expect(plain.find('[data-testid^="mention"]').exists()).toBe(false)
    expect(plain.text()).toBe('@鲍勃 hi')
    const recalled = mount(UserTextArea, {
      props: { message: '@鲍勃 hi', recalled: true, atUsers: ['usr_b'], mentionNames: names, currentUserId: 'usr_me' },
    })
    expect(recalled.find('[data-testid^="mention"]').exists()).toBe(false)
  })

  it('shows [有人@我] before the preview while at_me is set', async () => {
    const { default: Item } = await import('@/components/AppSideBar/right/ConversationListItem.vue')
    const conversation = {
      conversation_id: 'grp_1',
      type: 'group',
      target_info: { id: 'grp_1', name: 'G' },
      last_message: { message_id: 'm1', content_preview: '@我自己 看一下', timestamp: '2026-10-21 08:09:00', is_recalled: false },
      unread_count: 1,
      last_activity_time: '2026-10-21 08:09:00',
      at_me: true,
    }
    const wrapper = mount(Item, { props: { conversation: conversation as never } })
    expect(wrapper.get('[data-testid="conversation-preview"]').text()).toBe('[有人@我] @我自己 看一下')
    await wrapper.setProps({ conversation: { ...conversation, at_me: false } as never })
    expect(wrapper.find('[data-testid="conversation-at-me"]').exists()).toBe(false)
  })

  it('shows [@所有人] and keeps [有人@我] only when there is also a personal mention', async () => {
    const { default: Item } = await import('@/components/AppSideBar/right/ConversationListItem.vue')
    const conversation = {
      conversation_id: 'grp_1',
      type: 'group',
      target_info: { id: 'grp_1', name: 'G' },
      last_message: { message_id: 'm1', content_preview: '@所有人 开会', timestamp: '2026-10-21 08:09:00', is_recalled: false },
      unread_count: 1,
      last_activity_time: '2026-10-21 08:09:00',
      at_me: true,
      at_me_count: 1,
      at_all: true,
      at_all_count: 1,
    }
    const wrapper = mount(Item, { props: { conversation: conversation as never } })
    expect(wrapper.get('[data-testid="conversation-at-all"]').text()).toBe('[@所有人]')
    expect(wrapper.find('[data-testid="conversation-at-me"]').exists()).toBe(false)
    await wrapper.setProps({ conversation: { ...conversation, at_personal: true } as never })
    expect(wrapper.get('[data-testid="conversation-preview"]').text()).toContain('[@所有人]')
    expect(wrapper.get('[data-testid="conversation-at-me"]').text()).toBe('[有人@我]')
    await wrapper.setProps({ conversation: { ...conversation, at_personal: false, at_me_count: 2 } as never })
    expect(wrapper.get('[data-testid="conversation-at-me"]').text()).toBe('[有人@我]')
  })

  it('highlights @所有人 for other people', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const wrapper = mount(UserTextArea, {
      props: {
        message: '@所有人 开会',
        atUsers: ['all'],
        mentionNames: {},
        currentUserId: 'usr_me',
      },
    })
    expect(wrapper.get('[data-testid="mention-me"]').text()).toBe('@所有人')
  })
})
