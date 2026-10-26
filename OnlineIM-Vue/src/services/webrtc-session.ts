import type { CallType, IceServer } from '@/type/call'
import { deviceErrorMessage } from '@/utils/call-message'

export interface RemoteIceCandidate {
  candidate: string
  sdpMid?: string | null
  sdpMLineIndex?: number | null
}

export interface CallMediaSession {
  localStream: MediaStream | null
  remoteStream: MediaStream
  onLocalCandidate: ((candidate: RemoteIceCandidate) => void) | null
  onConnectionFailed: (() => void) | null
  open(callType: CallType): Promise<void>
  createOffer(): Promise<string>
  acceptOffer(sdp: string): Promise<string>
  acceptAnswer(sdp: string): Promise<void>
  addRemoteCandidate(candidate: RemoteIceCandidate): Promise<void>
  setMicEnabled(enabled: boolean): void
  setCameraEnabled(enabled: boolean): void
  close(): void
}

export type CallMediaFactory = (servers: IceServer[]) => CallMediaSession

export class CallDeviceError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'CallDeviceError'
  }
}

/**
 * 浏览器 WebRTC 会话。远端 SDP 设置之前到达的 ICE candidate 先排队。
 */
export class BrowserCallMedia implements CallMediaSession {
  localStream: MediaStream | null = null
  readonly remoteStream = new MediaStream()
  onLocalCandidate: ((candidate: RemoteIceCandidate) => void) | null = null
  onConnectionFailed: (() => void) | null = null

  private pc: RTCPeerConnection | null = null
  private pending: RemoteIceCandidate[] = []
  private remoteReady = false

  constructor(private readonly iceServers: IceServer[]) {}

  async open(callType: CallType): Promise<void> {
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: callType === 'video',
      })
    } catch (error) {
      throw new CallDeviceError(deviceErrorMessage(error))
    }
    this.localStream = stream
    const pc = new RTCPeerConnection({ iceServers: this.iceServers })
    this.pc = pc
    for (const track of stream.getTracks()) {
      pc.addTrack(track, stream)
    }
    pc.onicecandidate = (event) => {
      if (!event.candidate) return
      this.onLocalCandidate?.({
        candidate: event.candidate.candidate,
        sdpMid: event.candidate.sdpMid,
        sdpMLineIndex: event.candidate.sdpMLineIndex,
      })
    }
    pc.ontrack = (event) => {
      const tracks = event.streams[0]?.getTracks() ?? [event.track]
      for (const track of tracks) {
        const exists = this.remoteStream.getTracks().some(item => item.id === track.id)
        if (!exists) this.remoteStream.addTrack(track)
      }
    }
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed') this.onConnectionFailed?.()
    }
  }

  async createOffer(): Promise<string> {
    const pc = this.requirePc()
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    return offer.sdp || ''
  }

  async acceptOffer(sdp: string): Promise<string> {
    const pc = this.requirePc()
    await pc.setRemoteDescription({ type: 'offer', sdp })
    this.remoteReady = true
    await this.flush()
    const answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    return answer.sdp || ''
  }

  async acceptAnswer(sdp: string): Promise<void> {
    const pc = this.requirePc()
    await pc.setRemoteDescription({ type: 'answer', sdp })
    this.remoteReady = true
    await this.flush()
  }

  async addRemoteCandidate(candidate: RemoteIceCandidate): Promise<void> {
    if (!candidate.candidate) return
    if (!this.remoteReady || !this.pc) {
      this.pending.push(candidate)
      return
    }
    await this.pc.addIceCandidate({
      candidate: candidate.candidate,
      sdpMid: candidate.sdpMid ?? undefined,
      sdpMLineIndex: candidate.sdpMLineIndex ?? undefined,
    })
  }

  setMicEnabled(enabled: boolean): void {
    this.localStream?.getAudioTracks().forEach(track => {
      track.enabled = enabled
    })
  }

  setCameraEnabled(enabled: boolean): void {
    this.localStream?.getVideoTracks().forEach(track => {
      track.enabled = enabled
    })
  }

  close(): void {
    this.pc?.close()
    this.pc = null
    this.localStream?.getTracks().forEach(track => track.stop())
    this.localStream = null
    this.remoteStream.getTracks().forEach(track => {
      this.remoteStream.removeTrack(track)
      track.stop()
    })
    this.pending = []
    this.remoteReady = false
  }

  private async flush(): Promise<void> {
    const queued = this.pending.splice(0)
    for (const candidate of queued) {
      await this.addRemoteCandidate(candidate)
    }
  }

  private requirePc(): RTCPeerConnection {
    if (!this.pc) throw new CallDeviceError('通话媒体尚未建立')
    return this.pc
  }
}

let factory: CallMediaFactory = (servers) => new BrowserCallMedia(servers)

export function setCallMediaFactory(next: CallMediaFactory | null): void {
  factory = next ?? ((servers) => new BrowserCallMedia(servers))
}

export function createCallMedia(servers: IceServer[]): CallMediaSession {
  return factory(servers)
}
