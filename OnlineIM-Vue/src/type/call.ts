export type CallType = 'video' | 'audio'

export type CallPhase = 'idle' | 'outgoing' | 'incoming' | 'connecting' | 'active'

export type CallStatus = 'completed' | 'rejected' | 'cancelled' | 'missed' | 'busy'

export interface CallRecordContent {
  call_id?: string
  call_type?: CallType | string
  call_status?: CallStatus | string
  duration_seconds?: number
}

export interface IceServer {
  urls: string | string[]
  username?: string
  credential?: string
}

export interface IceServersResponse {
  ice_servers: IceServer[]
  call_timeout_seconds: number
}

export interface CallSignalPayload {
  call_id?: string
  target_id?: string
  caller_id?: string
  conversation_id?: string
  call_type?: string
  sdp?: string
  candidate?: string
  sdp_mid?: string | null
  sdp_m_line_index?: number | null
  duration_seconds?: number
  reason?: string
  call_status?: string
  code?: string
  message?: string
}

export interface StartCallInput {
  peerId: string
  peerName: string
  peerAvatar?: string
  conversationId: string
  callType: CallType
}
