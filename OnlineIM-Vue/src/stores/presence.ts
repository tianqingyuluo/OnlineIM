import { defineStore } from 'pinia'
import { effectScope, watch, type EffectScope } from 'vue'
import { presenceService } from '@/services/presence.service'
import type { PresenceRecord, PresenceUpdate } from '@/utils/presence'
import { useListStore } from '@/stores/list'

const BATCH = 100

interface ContactLists {
  friends?: Array<{ friend_info?: { user_id?: string | null } | null }> | null
  userGroups?: Array<{ friends?: Array<{ user_id?: string | null }> | null }> | null
}

function collectContactIds(listStore: ContactLists): string[] {
  const ids = new Set<string>()
  for (const friend of listStore.friends || []) {
    const userId = friend.friend_info?.user_id
    if (userId) ids.add(userId)
  }
  for (const group of listStore.userGroups || []) {
    for (const friend of group.friends || []) {
      if (friend.user_id) ids.add(friend.user_id)
    }
  }
  return [...ids]
}

let contactScope: EffectScope | null = null

/**
 * 好友第一次出现在列表里时补拉在线状态。登录时已经在列表里的人由 refreshContactPresence 负责。
 * 脱离组件生命周期，避免侧栏卸掉后把监听一起卸掉。
 */
export function watchContactPresence() {
  contactScope?.stop()
  const listStore = useListStore()
  const presenceStore = usePresenceStore()
  let seen = new Set(collectContactIds(listStore))
  contactScope = effectScope(true)
  contactScope.run(() => {
    watch(
      () => collectContactIds(listStore).join('\0'),
      (joined) => {
        const ids = joined ? joined.split('\0') : []
        const fresh = ids.filter(id => !seen.has(id) && presenceStore.byUserId[id] == null)
        seen = new Set(ids)
        if (fresh.length === 0) return
        void presenceStore.refresh(fresh).catch(error => console.error('加载在线状态失败:', error))
      },
    )
  })
}

export function stopWatchingContactPresence() {
  contactScope?.stop()
  contactScope = null
}

export const usePresenceStore = defineStore('presence', {
  state: () => ({
    byUserId: {} as Record<string, PresenceRecord>,
    hideOnlineStatus: false,
    /** 当前登录用户，以及本机 WebSocket 是否连着。服务端不给自己推 PRESENCE_UPDATE，自己的在线状态以本机连接为准。 */
    selfId: '' as string,
    selfOnline: false,
  }),
  actions: {
    async refresh(userIds: string[]) {
      const unique = [...new Set(userIds.filter(id => !!id))]
      for (let index = 0; index < unique.length; index += BATCH) {
        const batch = unique.slice(index, index + BATCH)
        const response = await presenceService.query(batch)
        for (const item of response.presences || []) {
          // 连接建立前发出的查询可能晚于连上才返回，不能把“自己”覆盖成离线
          if (item.user_id === this.selfId && this.selfOnline) continue
          this.byUserId[item.user_id] = item
        }
      }
    },
    applyUpdate(update: PresenceUpdate) {
      if (!update?.user_id || (update.status !== 'online' && update.status !== 'offline')) return
      this.byUserId[update.user_id] = {
        user_id: update.user_id,
        status: update.status,
        last_seen_at: update.last_seen_at ?? null,
        visible: true,
      }
    },
    /**
     * 本机 WebSocket 连上（含重连）：自己记为在线；已经展示过的其他人重新拉一次，
     * 补上连接建立前、或断线期间错过的 PRESENCE_UPDATE。
     */
    async onConnected(selfId: string) {
      this.setSelf(selfId, true)
      const ids = Object.keys(this.byUserId).filter(id => id !== selfId)
      if (ids.length > 0) await this.refresh(ids)
    },
    setSelf(selfId: string, online: boolean) {
      if (!selfId) return
      this.selfId = selfId
      this.selfOnline = online
      this.byUserId[selfId] = { user_id: selfId, status: online ? 'online' : 'offline', last_seen_at: null, visible: true }
    },
    async loadSettings() {
      const settings = await presenceService.getSettings()
      this.hideOnlineStatus = settings.hide_online_status === true
    },
    async setHidden(hide: boolean) {
      const settings = await presenceService.updateSettings(hide)
      this.hideOnlineStatus = settings.hide_online_status === true
    },
  },
})

/** 登录后拉一次好友的在线状态，作为 WebSocket 推送之前的初始值，并开始监听之后新出现的好友。 */
export async function refreshContactPresence() {
  const listStore = useListStore()
  const presenceStore = usePresenceStore()
  watchContactPresence()
  const ids = collectContactIds(listStore)
  if (ids.length > 0) {
    await presenceStore.refresh(ids)
  }
}
