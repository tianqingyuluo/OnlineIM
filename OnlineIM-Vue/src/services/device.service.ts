import api from './api.service'

export interface DeviceSession {
  device_id: string
  device_type: string
  device_name?: string | null
  online: boolean
  current: boolean
  login_at?: number | null
  active_at?: number | null
}

export const deviceService = {
  async list(): Promise<DeviceSession[]> {
    const response = await api.get('/devices')
    const devices = response.data?.devices
    return Array.isArray(devices) ? devices : []
  },

  async logout(deviceId: string): Promise<void> {
    await api.post(`/devices/${encodeURIComponent(deviceId)}/logout`)
  },
}
