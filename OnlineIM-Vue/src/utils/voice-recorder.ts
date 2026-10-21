import { pickRecorderMimeType, voiceFileExtension, VOICE_MAX_MS, VOICE_MIN_MS } from '@/utils/media-message'

export type VoiceRecorderState = 'idle' | 'requesting' | 'recording'

export interface RecordedVoice {
  file: File
  durationMs: number
}

/** 把 getUserMedia / MediaRecorder 的异常转成提示文案 */
export function microphoneErrorMessage(error: unknown): string {
  const name = (error as { name?: string })?.name || ''
  if (name === 'NotAllowedError' || name === 'SecurityError' || name === 'PermissionDeniedError') {
    return '无法使用麦克风：请在浏览器地址栏或系统设置中允许本网站使用麦克风'
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError') {
    return '未检测到可用的麦克风'
  }
  if (name === 'NotReadableError' || name === 'TrackStartError' || name === 'AbortError') {
    return '麦克风被其他程序占用，请关闭后重试'
  }
  if (name === 'NotSupportedError') {
    return '无法使用麦克风：浏览器不支持录音或未授予麦克风权限'
  }
  if (name === 'UnsupportedError') {
    return '当前浏览器不支持录音（需要 HTTPS 或 localhost 访问）'
  }
  return '无法开始录音，请重试'
}

export interface VoiceRecorderDeps {
  getUserMedia?: (constraints: MediaStreamConstraints) => Promise<MediaStream>
  MediaRecorderCtor?: typeof MediaRecorder
  now?: () => number
  setTimer?: (fn: () => void, ms: number) => ReturnType<typeof setInterval>
  clearTimer?: (id: ReturnType<typeof setInterval>) => void
}

/**
 * 录音控制器（与 Vue 无关，便于单测）：start → stop(send) / cancel。
 * - 最长 VOICE_MAX_MS，到时自动结束并交给 onAutoStop；
 * - 短于 VOICE_MIN_MS 的录音丢弃并抛出“说话时间太短”。
 */
export class VoiceRecorder {
  state: VoiceRecorderState = 'idle'
  elapsedMs = 0
  private stream: MediaStream | null = null
  private recorder: MediaRecorder | null = null
  private chunks: Blob[] = []
  private startedAt = 0
  private ticker: ReturnType<typeof setInterval> | null = null
  private stopResolver: ((voice: RecordedVoice | null) => void) | null = null
  private cancelled = false

  constructor(
    private readonly onTick: (elapsedMs: number) => void,
    private readonly onAutoStop: () => void,
    private readonly deps: VoiceRecorderDeps = {},
  ) {}

  static isSupported(): boolean {
    return typeof window !== 'undefined'
      && typeof window.MediaRecorder !== 'undefined'
      && !!navigator.mediaDevices?.getUserMedia
  }

  private now(): number {
    return this.deps.now ? this.deps.now() : performance.now()
  }

  async start(): Promise<void> {
    if (this.state !== 'idle') return
    const getUserMedia = this.deps.getUserMedia
      || (navigator.mediaDevices?.getUserMedia ? navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices) : undefined)
    const Recorder = this.deps.MediaRecorderCtor || (typeof MediaRecorder !== 'undefined' ? MediaRecorder : undefined)
    if (!getUserMedia || !Recorder) {
      const error = new Error('unsupported')
      error.name = 'UnsupportedError'
      throw error
    }
    this.state = 'requesting'
    try {
      this.stream = await getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
    } catch (error) {
      this.state = 'idle'
      throw error
    }
    if (this.cancelled) {
      // 等待授权期间已取消
      this.releaseStream()
      this.cancelled = false
      this.state = 'idle'
      return
    }
    const mimeType = pickRecorderMimeType(type => Recorder.isTypeSupported?.(type) ?? false)
    try {
      this.recorder = mimeType ? new Recorder(this.stream, { mimeType, audioBitsPerSecond: 32000 }) : new Recorder(this.stream)
    } catch (error) {
      this.releaseStream()
      this.state = 'idle'
      throw error
    }
    this.chunks = []
    this.recorder.ondataavailable = event => {
      if (event.data && event.data.size > 0) this.chunks.push(event.data)
    }
    this.recorder.onstop = () => this.finish()
    this.recorder.start(250)
    this.startedAt = this.now()
    this.elapsedMs = 0
    this.state = 'recording'
    const setTimer = this.deps.setTimer || ((fn, ms) => setInterval(fn, ms))
    this.ticker = setTimer(() => {
      this.elapsedMs = Math.min(VOICE_MAX_MS, this.now() - this.startedAt)
      this.onTick(this.elapsedMs)
      if (this.elapsedMs >= VOICE_MAX_MS) this.onAutoStop()
    }, 100)
  }

  /** 结束录音并返回文件；太短时抛错，未在录音时返回 null */
  stop(): Promise<RecordedVoice | null> {
    if (this.state === 'requesting') {
      this.cancelled = true
      return Promise.resolve(null)
    }
    if (this.state !== 'recording' || !this.recorder) return Promise.resolve(null)
    this.elapsedMs = Math.min(VOICE_MAX_MS, this.now() - this.startedAt)
    return new Promise<RecordedVoice | null>((resolve, reject) => {
      this.stopResolver = voice => {
        if (voice && voice.durationMs < VOICE_MIN_MS) reject(new Error('说话时间太短'))
        else resolve(voice)
      }
      this.cancelled = false
      this.recorder!.stop()
    })
  }

  cancel(): void {
    if (this.state === 'requesting') {
      this.cancelled = true
      return
    }
    if (this.state !== 'recording') return
    this.cancelled = true
    this.stopResolver = null
    try {
      this.recorder?.stop()
    } catch {
      this.finish()
    }
  }

  private finish(): void {
    const durationMs = this.elapsedMs
    const type = this.recorder?.mimeType || this.chunks[0]?.type || 'audio/webm'
    const chunks = this.chunks
    this.cleanup()
    const resolver = this.stopResolver
    this.stopResolver = null
    if (this.cancelled || !resolver) {
      this.cancelled = false
      resolver?.(null)
      return
    }
    const blob = new Blob(chunks, { type })
    const file = new File([blob], `voice-${Date.now()}.${voiceFileExtension(type)}`, { type: type.split(';')[0] })
    resolver({ file, durationMs })
  }

  private cleanup(): void {
    if (this.ticker) {
      const clearTimer = this.deps.clearTimer || (id => clearInterval(id))
      clearTimer(this.ticker)
      this.ticker = null
    }
    this.releaseStream()
    this.recorder = null
    this.chunks = []
    this.state = 'idle'
  }

  private releaseStream(): void {
    this.stream?.getTracks().forEach(track => track.stop())
    this.stream = null
  }

  dispose(): void {
    this.cancel()
    this.cleanup()
  }
}
