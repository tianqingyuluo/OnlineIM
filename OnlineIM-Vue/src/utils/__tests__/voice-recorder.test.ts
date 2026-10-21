import { describe, expect, it, vi } from 'vitest'
import { microphoneErrorMessage, VoiceRecorder } from '@/utils/voice-recorder'
import { formatVoiceClock, formatVoiceSeconds, pickRecorderMimeType, validateBeforeUpload, voiceFileExtension } from '@/utils/media-message'
import { getReplyPreviewText } from '@/utils/reply-preview'

class FakeRecorder {
  static isTypeSupported = (type: string) => type === 'audio/webm;codecs=opus'
  mimeType: string
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  state = 'inactive'
  constructor(_stream: unknown, options?: { mimeType?: string }) {
    this.mimeType = options?.mimeType || 'audio/webm'
  }
  start() { this.state = 'recording' }
  stop() {
    this.state = 'inactive'
    this.ondataavailable?.({ data: new Blob(['abc'], { type: this.mimeType }) })
    queueMicrotask(() => this.onstop?.())
  }
}

function fakeStream() {
  const stop = vi.fn()
  return { stream: { getTracks: () => [{ stop }] } as unknown as MediaStream, stop }
}

function makeRecorder(now: { t: number }, options: { deny?: string } = {}) {
  const { stream, stop } = fakeStream()
  let tick: (() => void) | null = null
  const onAutoStop = vi.fn()
  const recorder = new VoiceRecorder(() => {}, onAutoStop, {
    getUserMedia: options.deny
      ? () => Promise.reject(Object.assign(new Error('denied'), { name: options.deny }))
      : () => Promise.resolve(stream),
    MediaRecorderCtor: FakeRecorder as unknown as typeof MediaRecorder,
    now: () => now.t,
    setTimer: (fn) => { tick = fn; return 1 as unknown as ReturnType<typeof setInterval> },
    clearTimer: () => { tick = null },
  })
  return { recorder, stopTracks: stop, onAutoStop, tick: () => tick?.() }
}

describe('voice recorder', () => {
  it('records, stops and returns a file with measured duration, releasing the microphone', async () => {
    const now = { t: 1000 }
    const { recorder, stopTracks } = makeRecorder(now)
    await recorder.start()
    expect(recorder.state).toBe('recording')
    now.t = 3500
    const voice = await recorder.stop()
    expect(voice?.durationMs).toBe(2500)
    expect(voice?.file.type).toBe('audio/webm')
    expect(voice?.file.name).toMatch(/^voice-\d+\.webm$/)
    expect(stopTracks).toHaveBeenCalled()
    expect(recorder.state).toBe('idle')
  })

  it('rejects too-short recordings and supports cancel', async () => {
    const now = { t: 0 }
    const a = makeRecorder(now)
    await a.recorder.start()
    now.t = 400
    await expect(a.recorder.stop()).rejects.toThrow('说话时间太短')

    const b = makeRecorder(now)
    await b.recorder.start()
    b.recorder.cancel()
    await Promise.resolve()
    await Promise.resolve()
    expect(b.recorder.state).toBe('idle')
    expect(b.stopTracks).toHaveBeenCalled()
  })

  it('auto-stops at 60 seconds', async () => {
    const now = { t: 0 }
    const r = makeRecorder(now)
    await r.recorder.start()
    now.t = 59_000
    r.tick()
    expect(r.onAutoStop).not.toHaveBeenCalled()
    now.t = 61_000
    r.tick()
    expect(r.onAutoStop).toHaveBeenCalledTimes(1)
    const voice = await r.recorder.stop()
    expect(voice?.durationMs).toBe(60_000)
  })

  it('surfaces permission errors with friendly messages', async () => {
    const r = makeRecorder({ t: 0 }, { deny: 'NotAllowedError' })
    const error = await r.recorder.start().catch(e => e)
    expect(r.recorder.state).toBe('idle')
    expect(microphoneErrorMessage(error)).toContain('允许本网站使用麦克风')
    expect(microphoneErrorMessage({ name: 'NotFoundError' })).toBe('未检测到可用的麦克风')
    expect(microphoneErrorMessage({ name: 'NotReadableError' })).toContain('占用')
    expect(microphoneErrorMessage({ name: 'UnsupportedError' })).toContain('HTTPS')
    expect(microphoneErrorMessage({ name: 'NotSupportedError' })).toContain('麦克风')
  })
})

describe('voice helpers', () => {
  it('formats durations and picks recorder formats', () => {
    expect(formatVoiceClock(12_300)).toBe('00:12')
    expect(formatVoiceClock(60_000)).toBe('01:00')
    expect(formatVoiceClock(0)).toBe('00:00')
    expect(formatVoiceSeconds(2_400)).toBe('2″')
    expect(formatVoiceSeconds(300)).toBe('1″')
    expect(pickRecorderMimeType(t => t === 'audio/mp4')).toBe('audio/mp4')
    expect(pickRecorderMimeType(t => t.startsWith('audio/webm'))).toBe('audio/webm;codecs=opus')
    expect(pickRecorderMimeType(() => false)).toBe('')
    expect(voiceFileExtension('audio/ogg;codecs=opus')).toBe('ogg')
    expect(voiceFileExtension('audio/mp4')).toBe('m4a')
    expect(voiceFileExtension('audio/webm;codecs=opus')).toBe('webm')
  })

  it('validates voice size and builds reply preview with duration', () => {
    expect(validateBeforeUpload('voice', new File([new Uint8Array(10)], 'v.webm'))).toBeNull()
    expect(validateBeforeUpload('voice', new File([new Uint8Array(6 * 1024 * 1024)], 'v.webm'))).toContain('语音不能超过')
    expect(getReplyPreviewText('voice', '', undefined, 12_300)).toBe('[语音 00:12]')
    expect(getReplyPreviewText('voice', '')).toBe('[语音]')
  })
})
