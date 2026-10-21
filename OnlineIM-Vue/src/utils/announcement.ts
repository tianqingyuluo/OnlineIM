import type { GroupAnnouncement } from '@/type/groupAnnouncement'

export interface AnnouncementFrameMessage {
  action?: 'published' | 'updated' | 'pinned' | 'deleted'
  group_id?: string
  announcement_id?: string
  actor_id?: string
  user_id?: string
  username?: string
  nickname?: string
  avatar_url?: string
  read_at?: string
  read_count?: number
  unread_count?: number
  announcement?: GroupAnnouncement
}

export interface AnnouncementFrame {
  type: string
  message: AnnouncementFrameMessage
}

type Listener = (frame: AnnouncementFrame) => void
const listeners = new Set<Listener>()

export function emitAnnouncementFrame(frame: AnnouncementFrame): void {
  listeners.forEach(listener => listener(frame))
}

export function onAnnouncementFrame(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** 未确认的公告：置顶优先，否则最近更新的一条。 */
export function pickUnreadAnnouncement(items: GroupAnnouncement[] | undefined | null): GroupAnnouncement | null {
  const unread = (items || []).filter(item => item && item.read_by_me === false)
  if (!unread.length) return null
  const pinned = unread.find(item => item.pinned)
  if (pinned) return pinned
  return [...unread].sort((a, b) => Date.parse(b.updated_at || '') - Date.parse(a.updated_at || ''))[0]
}

function sameId(item: GroupAnnouncement, id?: string): boolean {
  return !!id && item.announcement_id === id
}

/**
 * 把一条公告帧并进当前列表。
 * 返回是否应该把弹层打开（新公告或正文更新，且不是自己发的）。
 */
export function applyAnnouncementFrame(
  items: GroupAnnouncement[],
  frame: AnnouncementFrame,
  selfId: string | undefined,
): { items: GroupAnnouncement[]; openPopup: boolean } {
  const message = frame.message || {}
  if (frame.type === 'GROUP_ANNOUNCEMENT_READ') {
    const next = items.map(item => {
      if (!sameId(item, message.announcement_id)) return item
      const mine = message.user_id === selfId
      return {
        ...item,
        read_by_me: mine ? true : item.read_by_me,
        read_count: message.read_count ?? item.read_count,
        unread_count: message.unread_count ?? item.unread_count,
      }
    })
    return { items: next, openPopup: false }
  }
  if (frame.type !== 'GROUP_ANNOUNCEMENT') return { items, openPopup: false }
  if (message.action === 'deleted') {
    return { items: items.filter(item => !sameId(item, message.announcement_id)), openPopup: false }
  }
  const incoming = message.announcement
  if (!incoming?.announcement_id) return { items, openPopup: false }
  const mine = !!selfId && message.actor_id === selfId
  const previous = items.find(item => item.announcement_id === incoming.announcement_id)
  const pinOnly = message.action === 'pinned'
  const merged: GroupAnnouncement = {
    ...incoming,
    read_by_me: pinOnly
      ? (previous?.read_by_me ?? (mine ? true : incoming.read_by_me === true))
      : (mine ? true : false),
    read_count: incoming.read_count ?? previous?.read_count,
    unread_count: incoming.unread_count ?? previous?.unread_count,
  }
  const exists = !!previous
  // 一个群只有一条置顶：服务端置顶新公告时会悄悄取消旧的置顶，不单独推帧，这里同步取消
  const base = merged.pinned
    ? items.map(item => item.pinned && item.announcement_id !== merged.announcement_id ? { ...item, pinned: false } : item)
    : items
  const next = exists
    ? base.map(item => item.announcement_id === merged.announcement_id ? { ...item, ...merged } : item)
    : [merged, ...base]
  const openPopup = !pinOnly && !mine && merged.read_by_me === false
  return { items: next, openPopup }
}
