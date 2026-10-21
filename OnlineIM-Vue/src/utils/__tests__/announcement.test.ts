import { describe, expect, it } from 'vitest'
import { applyAnnouncementFrame, pickUnreadAnnouncement } from '@/utils/announcement'
import type { GroupAnnouncement } from '@/type/groupAnnouncement'

function announcement(id: string, extra: Partial<GroupAnnouncement> = {}): GroupAnnouncement {
  return {
    announcement_id: id,
    group_id: 'grp_1',
    publisher_info: { user_id: 'usr_admin', username: 'admin', nickname: '管', avatar_url: '' },
    title: id,
    content: '正文',
    pinned: false,
    created_at: '2026-10-21 10:00:00',
    updated_at: '2026-10-21 10:00:00',
    read_by_me: false,
    ...extra,
  }
}

describe('announcement read state', () => {
  it('picks the pinned unread announcement before a newer one', () => {
    const items = [
      announcement('new', { updated_at: '2026-10-21 12:00:00' }),
      announcement('pin', { pinned: true, updated_at: '2026-10-21 09:00:00' }),
      announcement('read', { read_by_me: true }),
    ]
    expect(pickUnreadAnnouncement(items)?.announcement_id).toBe('pin')
    expect(pickUnreadAnnouncement([announcement('read', { read_by_me: true })])).toBeNull()
  })

  it('keeps the bar until this user confirms, and opens the popup for someone else', () => {
    const published = applyAnnouncementFrame([], {
      type: 'GROUP_ANNOUNCEMENT',
      message: {
        action: 'published',
        group_id: 'grp_1',
        actor_id: 'usr_admin',
        announcement: announcement('ann_1'),
      },
    }, 'usr_member')
    expect(published.openPopup).toBe(true)
    expect(published.items[0].read_by_me).toBe(false)

    const self = applyAnnouncementFrame([], {
      type: 'GROUP_ANNOUNCEMENT',
      message: { action: 'published', actor_id: 'usr_member', announcement: announcement('ann_1', { read_by_me: false }) },
    }, 'usr_member')
    expect(self.openPopup).toBe(false)
    expect(self.items[0].read_by_me).toBe(true)

    const read = applyAnnouncementFrame(published.items, {
      type: 'GROUP_ANNOUNCEMENT_READ',
      message: { announcement_id: 'ann_1', user_id: 'usr_member', read_count: 2, unread_count: 1 },
    }, 'usr_member')
    expect(read.openPopup).toBe(false)
    expect(read.items[0].read_by_me).toBe(true)
    expect(read.items[0].read_count).toBe(2)
    expect(pickUnreadAnnouncement(read.items)).toBeNull()

    const pinned = applyAnnouncementFrame(
      [announcement('ann_1', { read_by_me: true })],
      {
        type: 'GROUP_ANNOUNCEMENT',
        message: {
          action: 'pinned',
          actor_id: 'usr_admin',
          announcement: announcement('ann_1', { pinned: true, read_by_me: false }),
        },
      },
      'usr_member',
    )
    expect(pinned.openPopup).toBe(false)
    expect(pinned.items[0].pinned).toBe(true)
    expect(pinned.items[0].read_by_me).toBe(true)

    const removed = applyAnnouncementFrame(published.items, {
      type: 'GROUP_ANNOUNCEMENT',
      message: { action: 'deleted', announcement_id: 'ann_1' },
    }, 'usr_member')
    expect(removed.items).toEqual([])
  })
})
