// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const getAnnouncementsByGroupId = vi.fn()
const markAnnouncementRead = vi.fn()
const getAnnouncementReads = vi.fn()

vi.mock('@/services/announcement.service', () => ({
  groupAnnouncementService: { getAnnouncementsByGroupId, markAnnouncementRead, getAnnouncementReads },
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ loggedInUser: { user_id: 'usr_member' } }) }))
vi.mock('vue-sonner', () => ({ toast: { error: vi.fn() } }))

const announcement = {
  announcement_id: 'ann_1',
  group_id: 'grp_1',
  publisher_info: { user_id: 'usr_admin', username: 'admin', nickname: '管', avatar_url: '' },
  title: '周五开会',
  content: '三点，别迟到',
  pinned: true,
  created_at: '2026-10-21 10:00:00',
  updated_at: '2026-10-21 10:00:00',
  read_by_me: false,
  read_count: 1,
  unread_count: 2,
}

describe('group announcement bar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAnnouncementsByGroupId.mockResolvedValue([announcement])
    markAnnouncementRead.mockResolvedValue({ ...announcement, read_by_me: true })
    getAnnouncementReads.mockResolvedValue({
      announcement_id: 'ann_1',
      group_id: 'grp_1',
      read_count: 1,
      unread_count: 2,
      readers: [{ user_id: 'usr_admin', nickname: '管' }],
      unread_users: [{ user_id: 'usr_member', nickname: '我' }, { user_id: 'usr_c', nickname: '丙' }],
    })
  })

  it('stays until the member confirms, and shows the read list to a manager', async () => {
    const { default: Bar } = await import('@/components/MainPart/GroupAnnouncementBar.vue')
    const wrapper = mount(Bar, { props: { groupId: 'grp_1', myRole: '2' } })
    await flushPromises()
    expect(wrapper.get('[data-testid="announcement-bar"]').text()).toContain('周五开会')

    await wrapper.get('[data-testid="announcement-bar-open"]').trigger('click')
    await flushPromises()
    expect(markAnnouncementRead).toHaveBeenCalledWith('grp_1', 'ann_1')
    expect(wrapper.get('[data-testid="announcement-popup-title"]').text()).toBe('周五开会')
    expect(wrapper.get('[data-testid="announcement-read-lists"]').text()).toContain('丙')
    expect(wrapper.find('[data-testid="announcement-bar"]').exists()).toBe(true)

    await wrapper.get('[data-testid="announcement-confirm"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="announcement-bar"]').exists()).toBe(false)
    expect(markAnnouncementRead).toHaveBeenCalledWith('grp_1', 'ann_1')
  })
})
