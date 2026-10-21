import api from '@/services/api.service'
import type { PresenceRecord } from '@/utils/presence'

export interface PresenceQueryResponse {
  presences: PresenceRecord[]
}

export interface PresenceSettings {
  hide_online_status: boolean
}

export const presenceService = {
  async query(userIds: string[]): Promise<PresenceQueryResponse> {
    const response = await api.get<PresenceQueryResponse>('/presence', {
      params: { user_ids: userIds.join(',') },
    })
    return response.data
  },

  async getSettings(): Promise<PresenceSettings> {
    const response = await api.get<PresenceSettings>('/presence/settings')
    return response.data
  },

  async updateSettings(hideOnlineStatus: boolean): Promise<PresenceSettings> {
    const response = await api.put<PresenceSettings>('/presence/settings', {
      hide_online_status: hideOnlineStatus,
    })
    return response.data
  },
}
