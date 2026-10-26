// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

describe('call bubble', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('shows the call preview and redials with the same type', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const content = JSON.stringify({
      call_id: 'call_abcdefgh',
      call_type: 'video',
      call_status: 'completed',
      duration_seconds: 83,
    })
    const wrapper = mount(UserTextArea, {
      props: { message: content, messageType: 'call', isSelf: true },
      attachTo: document.body,
    })
    expect(wrapper.get('[data-testid="call-bubble-text"]').text()).toBe('[视频通话] 01:23')
    await wrapper.get('[data-testid="call-bubble"]').trigger('click')
    expect(wrapper.emitted('redial')?.[0]).toEqual(['video'])

    const missed = mount(UserTextArea, {
      props: {
        message: JSON.stringify({ call_type: 'audio', call_status: 'missed', duration_seconds: 0 }),
        messageType: 'call',
      },
    })
    expect(missed.get('[data-testid="call-bubble-text"]').text()).toBe('[未接语音通话]')
  })
})

describe('webrtc candidate queue', () => {
  beforeEach(() => {
    vi.stubGlobal('MediaStream', class {
      tracks: { id: string, kind: string, enabled: boolean, stop: () => void }[] = []
      getTracks() { return this.tracks }
      getAudioTracks() { return this.tracks.filter(track => track.kind === 'audio') }
      getVideoTracks() { return this.tracks.filter(track => track.kind === 'video') }
      addTrack(track: { id: string, kind: string, enabled: boolean, stop: () => void }) { this.tracks.push(track) }
      removeTrack(track: { id: string }) { this.tracks = this.tracks.filter(item => item.id !== track.id) }
    })
  })

  it('queues ICE candidates until the remote description is set', async () => {
    const added: string[] = []
    let remote = ''
    vi.stubGlobal('RTCPeerConnection', class {
      onicecandidate: ((event: { candidate: null }) => void) | null = null
      ontrack: ((event: { streams: MediaStream[], track: MediaStreamTrack }) => void) | null = null
      onconnectionstatechange: (() => void) | null = null
      connectionState = 'new'
      addTrack() {}
      createOffer() { return Promise.resolve({ type: 'offer', sdp: 'local-offer' }) }
      createAnswer() { return Promise.resolve({ type: 'answer', sdp: 'local-answer' }) }
      setLocalDescription() { return Promise.resolve() }
      setRemoteDescription(description: { sdp: string }) {
        remote = description.sdp
        return Promise.resolve()
      }
      addIceCandidate(candidate: { candidate: string }) {
        added.push(candidate.candidate)
        return Promise.resolve()
      }
      close() {}
    })
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn(async () => ({
          getTracks: () => [{ id: 'mic', kind: 'audio', enabled: true, stop() {} }],
        })),
      },
    })

    const { BrowserCallMedia } = await import('@/services/webrtc-session')
    const media = new BrowserCallMedia([{ urls: 'stun:stun.example' }])
    await media.open('audio')
    await media.addRemoteCandidate({ candidate: 'early', sdpMid: '0', sdpMLineIndex: 0 })
    expect(added).toEqual([])
    expect(remote).toBe('')
    await media.acceptAnswer('remote-answer')
    expect(remote).toBe('remote-answer')
    expect(added).toEqual(['early'])
  })

  it('reports a missing camera as a readable error', async () => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn(async () => {
          const error = new Error('missing')
          error.name = 'NotFoundError'
          throw error
        }),
      },
    })
    const { BrowserCallMedia } = await import('@/services/webrtc-session')
    const media = new BrowserCallMedia([])
    await expect(media.open('video')).rejects.toThrow('未找到可用的麦克风或摄像头')
  })
})
