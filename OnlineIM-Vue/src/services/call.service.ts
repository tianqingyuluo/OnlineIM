import api from '@/services/api.service'
import type { IceServersResponse } from '@/type/call'

export async function fetchIceServers(): Promise<IceServersResponse> {
  const response = await api.get<IceServersResponse>('/calls/ice-servers')
  return response.data
}
