// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const push = vi.fn()
vi.mock('@/router', () => ({ default: { push } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/main/chat', params: {} }) }))
vi.mock('@/utils/indexedDB', () => ({ dbService: {}, STORES: {} }))
vi.mock('@/components/MainPart/UserMainPart.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/MainPart/GroupMainPart.vue', () => ({ default: { template: '<div />' } }))

const base = {
  conversation_id: 'conv_a',
  type: 'private',
  target_info: { id: 'usr_b', name: 'Bob' },
  last_message: { message_id: 'm1', content_preview: '你好', timestamp: '2026-01-02 08:09:00', is_recalled: false },
  unread_count: 3,
  last_activity_time: '2026-01-02 08:09:00',
}

describe('conversation list', () => {
  it('shows name, preview, time and unread badge (grey when muted)', async () => {
    const { default: Item } = await import('@/components/AppSideBar/right/ConversationListItem.vue')
    const wrapper = mount(Item, { props: { conversation: base as never } })
    expect(wrapper.get('[data-testid="conversation-name"]').text()).toBe('Bob')
    expect(wrapper.get('[data-testid="conversation-preview"]').text()).toBe('你好')
    expect(wrapper.get('[data-testid="conversation-time"]').text()).not.toBe('')
    expect(wrapper.get('[data-testid="conversation-unread"]').text()).toBe('3')
    await wrapper.setProps({ conversation: { ...base, unread_count: 150, is_muted: true, last_message: null } as never })
    expect(wrapper.get('[data-testid="conversation-unread"]').text()).toBe('99+')
    expect(wrapper.get('[data-testid="conversation-unread"]').classes()).toContain('bg-gray-400')
    expect(wrapper.get('[data-testid="conversation-preview"]').text()).toBe('无消息')
    await wrapper.setProps({ conversation: { ...base, unread_count: 0 } as never })
    expect(wrapper.find('[data-testid="conversation-unread"]').exists()).toBe(false)
  })

  it('phone list opens the chat route when a conversation is tapped', async () => {
    setActivePinia(createPinia())
    const { useListStore } = await import('@/stores/list')
    useListStore().conversations = [base, { ...base, conversation_id: 'grp_1', type: 'group', target_info: { id: 'grp_1', name: 'G' }, last_message: null }] as never
    const { default: PhoneTalks } = await import('@/components/phone/PhoneSidebarTalks.vue')
    const wrapper = mount(PhoneTalks, {
      global: { stubs: {
        Sidebar: { template: '<div><slot /></div>' }, SidebarContent: { template: '<div><slot /></div>' },
        SidebarGroup: { template: '<div><slot /></div>' }, SidebarGroupContent: { template: '<div><slot /></div>' },
        SidebarMenu: { template: '<div><slot /></div>' }, SidebarMenuItem: { template: '<div><slot /></div>' },
        SidebarMenuButton: { template: '<div v-bind="$attrs"><slot /></div>' }, sidebarGroupLabel: { template: '<div><slot /></div>' },
      } },
    })
    const items = wrapper.findAll('[data-testid="conversation-item"]')
    expect(items).toHaveLength(2)
    await items[0].trigger('click')
    expect(push).toHaveBeenCalledWith('/main/chat/private/conv_a')
    await items[1].trigger('click')
    expect(push).toHaveBeenCalledWith('/main/chat/group/grp_1')
  })
})
