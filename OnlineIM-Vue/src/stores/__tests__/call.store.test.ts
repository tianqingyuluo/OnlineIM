import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { CallDeviceError, type CallMediaSession, type RemoteIceCandidate } from '@/services/webrtc-session'

const ws = vi.hoisted(() => ({
  sendMessage: vi.fn(),
  onMessage: vi.fn(() => () => {}),
}))

vi.mock('@/services/websocket.service', () => ({ websocketService: ws }))
vi.mock('@/services/call.service', () => ({
  fetchIceServers: vi.fn(async () => ({
    ice_servers: [{ urls: ['stun:stun.example'] }],
    call_timeout_seconds: 45,
  })),
}))
vi.mock('vue-sonner', () => ({
  toast: { error: vi.fn(), info: vi.fn(), success: vi.fn(), warning: vi.fn() },
}))

class FakeMedia implements CallMediaSession {
  localStream: MediaStream | null = null
  remoteStream = { getTracks: () => [] } as unknown as MediaStream
  onLocalCandidate: ((candidate: RemoteIceCandidate) => void) | null = null
  onConnectionFailed: (() => void) | null = null
  seen: RemoteIceCandidate[] = []
  failOpen = false

  async open() {
    if (this.failOpen) throw new CallDeviceError('无法使用麦克风或摄像头：权限被拒绝')
  }

  async createOffer() {
    return 'offer-sdp'
  }

  async acceptOffer() {
    return 'answer-sdp'
  }

  async acceptAnswer() {}

  async addRemoteCandidate(candidate: RemoteIceCandidate) {
    this.seen.push(candidate)
  }

  setMicEnabled() {}
  setCameraEnabled() {}
  close() {}
}

let media: FakeMedia

describe('call store signaling', () => {
  beforeEach(async () => {
    vi.useRealTimers()
    vi.clearAllMocks()
    setActivePinia(createPinia())
    media = new FakeMedia()
    const session = await import('@/services/webrtc-session')
    session.setCallMediaFactory(() => media)
  })

  it('places an invite and sends the local offer', async () => {
    const { useCallStore } = await import('@/stores/call.store')
    const store = useCallStore()
    await store.startCall({
      peerId: 'usr_peer',
      peerName: '小明',
      peerAvatar: '/a.png',
      conversationId: 'conv_1',
      callType: 'video',
    })
    expect(store.phase).toBe('outgoing')
    expect(ws.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      type: 'CALL_INVITE',
      message: expect.objectContaining({
        target_id: 'usr_peer',
        conversation_id: 'conv_1',
        call_type: 'video',
        sdp: 'offer-sdp',
      }),
    }), { persist: false })
  })

  it('does not invite when the microphone permission is denied', async () => {
    media.failOpen = true
    const { useCallStore } = await import('@/stores/call.store')
    const { toast } = await import('vue-sonner')
    const store = useCallStore()
    await store.startCall({
      peerId: 'usr_peer',
      peerName: '小明',
      conversationId: 'conv_1',
      callType: 'audio',
    })
    expect(store.phase).toBe('idle')
    expect(ws.sendMessage).not.toHaveBeenCalled()
    expect(toast.error).toHaveBeenCalledWith('无法使用麦克风或摄像头：权限被拒绝')
  })

  it('rings, accepts, and ignores a call handled on another device', async () => {
    const { useCallStore } = await import('@/stores/call.store')
    const store = useCallStore()
    store.handleFrame('CALL_INVITE', {
      call_id: 'call_abcdefgh',
      caller_id: 'usr_peer',
      conversation_id: 'conv_1',
      call_type: 'audio',
      sdp: 'remote-offer',
    })
    expect(store.phase).toBe('incoming')
    expect(ws.sendMessage).toHaveBeenCalledWith(expect.objectContaining({ type: 'CALL_RINGING' }), { persist: false })

    store.handleFrame('CALL_CANDIDATE', {
      call_id: 'call_abcdefgh',
      candidate: 'cand-1',
      sdp_mid: '0',
      sdp_m_line_index: 0,
    })
    expect(store.earlyCandidates).toHaveLength(1)

    await store.acceptIncoming()
    expect(store.phase).toBe('active')
    expect(media.seen.map(item => item.candidate)).toContain('cand-1')
    expect(ws.sendMessage).toHaveBeenCalledWith(expect.objectContaining({
      type: 'CALL_ANSWER',
      message: expect.objectContaining({ sdp: 'answer-sdp' }),
    }), { persist: false })
  })

  it('stops ringing when another device already answered', async () => {
    const { useCallStore } = await import('@/stores/call.store')
    const store = useCallStore()
    store.handleFrame('CALL_INVITE', {
      call_id: 'call_abcdefgh',
      caller_id: 'usr_peer',
      call_type: 'video',
      sdp: 'remote-offer',
    })
    store.handleFrame('CALL_HANDLED_ELSEWHERE', { call_id: 'call_abcdefgh', call_status: 'answered' })
    expect(store.phase).toBe('idle')
  })

  it('clears an outgoing call when the peer is busy and when the ring times out', async () => {
    vi.useFakeTimers()
    const { useCallStore } = await import('@/stores/call.store')
    const store = useCallStore()
    await store.startCall({
      peerId: 'usr_peer',
      peerName: '小明',
      conversationId: 'conv_1',
      callType: 'video',
    })
    store.handleFrame('CALL_BUSY', { call_id: store.callId, call_status: 'busy' })
    expect(store.phase).toBe('idle')

    await store.startCall({
      peerId: 'usr_peer',
      peerName: '小明',
      conversationId: 'conv_1',
      callType: 'audio',
    })
    await vi.advanceTimersByTimeAsync(47_000)
    expect(store.phase).toBe('idle')
  })
})
