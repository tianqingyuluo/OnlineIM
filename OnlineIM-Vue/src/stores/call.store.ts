import { defineStore } from 'pinia'
import { markRaw } from 'vue'
import { toast } from 'vue-sonner'
import { fetchIceServers } from '@/services/call.service'
import { websocketService } from '@/services/websocket.service'
import { createCallMedia, type CallMediaSession, type RemoteIceCandidate } from '@/services/webrtc-session'
import { useListStore } from '@/stores/list'
import type { CallPhase, CallSignalPayload, CallType, IceServersResponse, StartCallInput } from '@/type/call'
import { createCallId } from '@/utils/call-message'

const FALLBACK_ICE: IceServersResponse = {
  ice_servers: [{ urls: 'stun:stun.l.google.com:19302' }],
  call_timeout_seconds: 45,
}

let signalingBound = false

function lookupPeer(userId: string): { name: string, avatar: string } {
  const list = useListStore()
  for (const friend of list.friends || []) {
    if (friend.friend_info?.user_id === userId) {
      const info = friend.friend_info
      return {
        name: info.remark || info.nickname || info.username || '好友',
        avatar: info.avatar_url || '',
      }
    }
  }
  const conversation = (list.conversations || []).find(item => item.target_info?.id === userId)
  if (conversation?.target_info) {
    return {
      name: conversation.target_info.name || '好友',
      avatar: conversation.target_info.avatar_url || '',
    }
  }
  return { name: '好友', avatar: '' }
}

async function loadIce(): Promise<IceServersResponse> {
  try {
    const response = await fetchIceServers()
    if (!response?.ice_servers?.length) return FALLBACK_ICE
    return {
      ice_servers: response.ice_servers,
      call_timeout_seconds: response.call_timeout_seconds > 0 ? response.call_timeout_seconds : 45,
    }
  } catch {
    return FALLBACK_ICE
  }
}

export const useCallStore = defineStore('call', {
  state: () => ({
    phase: 'idle' as CallPhase,
    callId: '',
    callType: 'video' as CallType,
    conversationId: '',
    peerId: '',
    peerName: '',
    peerAvatar: '',
    minimized: false,
    muted: false,
    cameraOn: true,
    durationSeconds: 0,
    timeoutSeconds: 45,
    localStream: null as MediaStream | null,
    remoteStream: null as MediaStream | null,
    pendingOffer: '',
    earlyCandidates: [] as RemoteIceCandidate[],
    pendingLocalCandidates: [] as RemoteIceCandidate[],
    canTrickle: false,
    media: null as CallMediaSession | null,
    ringTimer: null as ReturnType<typeof setTimeout> | null,
    durationTimer: null as ReturnType<typeof setInterval> | null,
  }),
  actions: {
    async startCall(input: StartCallInput) {
      if (this.phase !== 'idle') {
        toast.info('当前正在通话中')
        return
      }
      if (!input.peerId || !input.conversationId) {
        toast.error('无法发起通话')
        return
      }
      const callId = createCallId()
      this.phase = 'outgoing'
      this.callId = callId
      this.callType = input.callType
      this.conversationId = input.conversationId
      this.peerId = input.peerId
      this.peerName = input.peerName || '好友'
      this.peerAvatar = input.peerAvatar || ''
      this.minimized = false
      this.muted = false
      this.cameraOn = input.callType === 'video'
      this.durationSeconds = 0
      try {
        const ice = await loadIce()
        this.timeoutSeconds = ice.call_timeout_seconds
        const media = await this.openMedia(ice, input.callType)
        const sdp = await media.createOffer()
        this.send('CALL_INVITE', {
          call_id: callId,
          target_id: input.peerId,
          conversation_id: input.conversationId,
          call_type: input.callType,
          sdp,
        })
        this.releaseTrickle()
        this.armRingTimer()
      } catch (error) {
        toast.error(error instanceof Error ? error.message : '无法发起通话')
        this.reset()
      }
    },

    cancelOutgoing() {
      if (this.phase !== 'outgoing' && this.phase !== 'connecting') return
      const callId = this.callId
      this.send('CALL_CANCEL', { call_id: callId, reason: 'user' })
      this.reset()
    },

    rejectIncoming() {
      if (this.phase !== 'incoming') return
      const callId = this.callId
      this.send('CALL_REJECT', { call_id: callId })
      this.reset()
    },

    async acceptIncoming() {
      if (this.phase !== 'incoming' || !this.pendingOffer) return
      this.phase = 'connecting'
      this.clearRingTimer()
      try {
        const ice = await loadIce()
        const media = await this.openMedia(ice, this.callType)
        for (const candidate of this.earlyCandidates) {
          await media.addRemoteCandidate(candidate)
        }
        this.earlyCandidates = []
        const answer = await media.acceptOffer(this.pendingOffer)
        this.send('CALL_ANSWER', { call_id: this.callId, sdp: answer })
        this.releaseTrickle()
        this.phase = 'active'
        this.pendingOffer = ''
        this.startDuration()
      } catch (error) {
        this.send('CALL_REJECT', { call_id: this.callId })
        toast.error(error instanceof Error ? error.message : '无法接听')
        this.reset()
      }
    },

    hangup() {
      if (this.phase !== 'active' && this.phase !== 'connecting') return
      this.send('CALL_HANGUP', { call_id: this.callId, duration_seconds: this.durationSeconds })
      this.reset()
    },

    toggleMute() {
      this.muted = !this.muted
      this.media?.setMicEnabled(!this.muted)
    },

    toggleCamera() {
      if (this.callType !== 'video') return
      this.cameraOn = !this.cameraOn
      this.media?.setCameraEnabled(this.cameraOn)
    },

    toggleMinimize() {
      if (this.phase !== 'active') return
      this.minimized = !this.minimized
    },

    handleFrame(type: string, payload: CallSignalPayload) {
      if (type === 'ERROR') {
        const code = String(payload.code || '')
        if ((this.phase === 'outgoing' || this.phase === 'connecting')
          && (code === '403' || code === '400' || code === 'MESSAGE_REJECTED')) {
          this.reset()
        }
        return
      }
      if (!type.startsWith('CALL_')) return
      if (type === 'CALL_INVITE' || type === 'CALL_OFFER') {
        this.onInvite(payload)
        return
      }
      if (payload.call_id && this.callId && payload.call_id !== this.callId) return
      switch (type) {
        case 'CALL_RINGING':
          break
        case 'CALL_ANSWER':
          void this.onAnswer(payload)
          break
        case 'CALL_CANDIDATE':
          void this.onCandidate(payload)
          break
        case 'CALL_REJECT':
          toast.info('对方已拒绝')
          this.reset()
          break
        case 'CALL_BUSY':
          toast.info('对方正在通话中')
          this.reset()
          break
        case 'CALL_CANCEL':
          this.onCancel(payload)
          break
        case 'CALL_END':
        case 'CALL_HANGUP':
          if (this.phase !== 'idle') toast.info('通话已结束')
          this.reset()
          break
        case 'CALL_HANDLED_ELSEWHERE':
          if (this.phase === 'incoming') {
            toast.info(payload.call_status === 'rejected' ? '已在其他设备拒接' : '已在其他设备接听')
            this.reset()
          }
          break
        default:
          break
      }
    },

    onInvite(payload: CallSignalPayload) {
      if (this.phase !== 'idle') return
      const callId = payload.call_id || ''
      if (!callId || !payload.sdp) return
      const peerId = payload.caller_id || ''
      const profile = peerId ? lookupPeer(peerId) : { name: '好友', avatar: '' }
      this.phase = 'incoming'
      this.callId = callId
      this.callType = payload.call_type === 'audio' ? 'audio' : 'video'
      this.conversationId = payload.conversation_id || ''
      this.peerId = peerId
      this.peerName = profile.name
      this.peerAvatar = profile.avatar
      this.pendingOffer = payload.sdp
      this.cameraOn = this.callType === 'video'
      this.minimized = false
      const callerName = profile.name || '好友'
      const callLabel = this.callType === 'audio' ? '邀请你语音通话' : '邀请你视频通话'
      void import('@/services/tauri-bridge').then(({ notifyIncomingCall }) => notifyIncomingCall({
        title: callerName,
        body: callLabel,
        conversationId: this.conversationId,
      })).catch(error => console.error('来电通知失败:', error))
      this.send('CALL_RINGING', { call_id: callId })
      this.armRingTimer()
    },

    async onAnswer(payload: CallSignalPayload) {
      if (this.phase !== 'outgoing' || !payload.sdp || !this.media) return
      try {
        await this.media.acceptAnswer(payload.sdp)
        this.phase = 'active'
        this.clearRingTimer()
        this.startDuration()
      } catch (error) {
        toast.error(error instanceof Error ? error.message : '接通失败')
        this.send('CALL_HANGUP', { call_id: this.callId, duration_seconds: 0 })
        this.reset()
      }
    },

    async onCandidate(payload: CallSignalPayload) {
      if (!payload.candidate || this.phase === 'idle') return
      const candidate: RemoteIceCandidate = {
        candidate: payload.candidate,
        sdpMid: payload.sdp_mid,
        sdpMLineIndex: payload.sdp_m_line_index,
      }
      if (!this.media) {
        this.earlyCandidates.push(candidate)
        return
      }
      await this.media.addRemoteCandidate(candidate)
    },

    onCancel(payload: CallSignalPayload) {
      if (this.phase === 'idle') return
      if (payload.reason === 'timeout') toast.info('对方未接听')
      else if (payload.reason === 'offline') toast.info('对方不在线')
      else if (this.phase === 'incoming') toast.info('对方已取消')
      this.reset()
    },

    async openMedia(ice: IceServersResponse, callType: CallType): Promise<CallMediaSession> {
      const media = markRaw(createCallMedia(ice.ice_servers))
      media.onLocalCandidate = (candidate) => {
        if (!this.callId) return
        if (!this.canTrickle) {
          this.pendingLocalCandidates.push(candidate)
          return
        }
        this.sendCandidate(candidate)
      }
      media.onConnectionFailed = () => {
        if (this.phase === 'active') {
          toast.error('网络中断，通话已结束')
          this.hangup()
        }
      }
      await media.open(callType)
      this.media = media
      this.localStream = media.localStream
      this.remoteStream = media.remoteStream
      return media
    },

    send(type: string, message: Record<string, unknown>) {
      websocketService.sendMessage({ type, message }, { persist: false })
    },

    sendCandidate(candidate: RemoteIceCandidate) {
      this.send('CALL_CANDIDATE', {
        call_id: this.callId,
        candidate: candidate.candidate,
        sdp_mid: candidate.sdpMid ?? null,
        sdp_m_line_index: candidate.sdpMLineIndex ?? null,
      })
    },

    /** 邀请或应答发出之后才开始向外发送本机 candidate，避免对端尚未建会话就丢掉。 */
    releaseTrickle() {
      this.canTrickle = true
      const queued = this.pendingLocalCandidates.splice(0)
      for (const candidate of queued) this.sendCandidate(candidate)
    },

    armRingTimer() {
      this.clearRingTimer()
      const wait = Math.max(1, this.timeoutSeconds) * 1000 + 2000
      this.ringTimer = setTimeout(() => {
        if (this.phase === 'outgoing') {
          toast.info('对方未接听')
          this.reset()
        } else if (this.phase === 'incoming') {
          toast.info('未接听')
          this.reset()
        }
      }, wait)
    },

    clearRingTimer() {
      if (this.ringTimer) clearTimeout(this.ringTimer)
      this.ringTimer = null
    },

    startDuration() {
      if (this.durationTimer) clearInterval(this.durationTimer)
      this.durationSeconds = 0
      this.durationTimer = setInterval(() => {
        this.durationSeconds += 1
      }, 1000)
    },

    reset() {
      this.clearRingTimer()
      if (this.durationTimer) clearInterval(this.durationTimer)
      this.durationTimer = null
      if (this.media) {
        this.media.onConnectionFailed = null
        this.media.onLocalCandidate = null
        this.media.close()
      }
      this.media = null
      this.localStream = null
      this.remoteStream = null
      this.phase = 'idle'
      this.callId = ''
      this.conversationId = ''
      this.peerId = ''
      this.peerName = ''
      this.peerAvatar = ''
      this.pendingOffer = ''
      this.earlyCandidates = []
      this.pendingLocalCandidates = []
      this.canTrickle = false
      this.minimized = false
      this.muted = false
      this.cameraOn = true
      this.durationSeconds = 0
    },
  },
})

/** 全局只绑定一次。通话帧不进消息出站队列。 */
export function bindCallSignaling(): void {
  if (signalingBound) return
  signalingBound = true
  websocketService.onMessage((event) => {
    try {
      const frame = JSON.parse(String(event.data)) as { type?: unknown, message?: unknown }
      if (typeof frame.type !== 'string') return
      if (!frame.type.startsWith('CALL_') && frame.type !== 'ERROR') return
      const message = frame.message && typeof frame.message === 'object'
        ? frame.message as CallSignalPayload
        : {}
      useCallStore().handleFrame(frame.type, message)
    } catch {
      // 非 JSON 帧由其他监听处理
    }
  })
}
