import api from '@/services/api.service'

export interface E2eeKeyBundle {
  user_id: string
  key_id: string
  identity_public_key: string
  signing_public_key: string
  algorithm: string
  fingerprint: string
  updated_at?: number | null
}

export interface E2eeSessionState {
  conversation_id: string
  enabled: boolean
  updated_by?: string | null
  updated_at?: number | null
}

export const e2eeService = {
  async uploadKey(body: {
    identity_public_key: string
    signing_public_key: string
    key_id: string
    algorithm: string
    fingerprint: string
  }): Promise<E2eeKeyBundle> {
    const response = await api.put<E2eeKeyBundle>('/e2ee/keys', body)
    return response.data
  },

  async getKey(userId: string): Promise<E2eeKeyBundle> {
    const response = await api.get<E2eeKeyBundle>(`/e2ee/keys/${encodeURIComponent(userId)}`, {
      skipErrorToast: true,
    })
    return response.data
  },

  async getSession(conversationId: string): Promise<E2eeSessionState> {
    const response = await api.get<E2eeSessionState>(
      `/conversations/${encodeURIComponent(conversationId)}/e2ee`,
      { skipErrorToast: true },
    )
    return response.data
  },

  async setSession(conversationId: string, enabled: boolean): Promise<E2eeSessionState> {
    const response = await api.put<E2eeSessionState>(
      `/conversations/${encodeURIComponent(conversationId)}/e2ee`,
      { enabled },
    )
    return response.data
  },
}
