// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MessageReplyPreview from '@/components/MainPart/MessageReplyPreview.vue'
import ReplyComposerBar from '@/components/MainPart/ReplyComposerBar.vue'
import MessageActions from '@/components/MainPart/MessageActions.vue'

const activeReference = {
  message_id: 'msg_target',
  seq_id: '100',
  sender_id: 'usr_peer',
  sender_display_name: '小明',
  message_type: 'text',
  preview_text: '原消息内容',
  state: 'active' as const,
}

describe('message reply components', () => {
  it('renders an active preview and emits navigation', async () => {
    const wrapper = mount(MessageReplyPreview, {
      props: { reference: activeReference },
    })

    expect(wrapper.text()).toContain('小明')
    expect(wrapper.text()).toContain('原消息内容')
    await wrapper.get('[data-testid="reply-preview"]').trigger('click')
    expect(wrapper.emitted('navigate')).toHaveLength(1)
  })

  it('never renders the old preview for recalled references', () => {
    const wrapper = mount(MessageReplyPreview, {
      props: {
        reference: {
          ...activeReference,
          state: 'recalled',
          preview_text: '不能泄露的旧内容',
        },
      },
    })

    expect(wrapper.text()).toContain('原消息已撤回')
    expect(wrapper.text()).not.toContain('不能泄露的旧内容')
  })

  it('lets the composer cancel a selected reply', async () => {
    const wrapper = mount(ReplyComposerBar, {
      props: { reference: activeReference },
    })

    await wrapper.get('[data-testid="cancel-reply"]').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('offers a hover reply action only for available messages', async () => {
    const wrapper = mount(MessageActions, {
      props: { canReply: true, align: 'start' },
      slots: { default: '<div>正文</div>' },
    })

    await wrapper.get('[data-testid="quick-reply"]').trigger('click')
    expect(wrapper.emitted('reply')).toHaveLength(1)

    await wrapper.setProps({ canReply: false })
    expect(wrapper.find('[data-testid="quick-reply"]').exists()).toBe(false)
  })
})
