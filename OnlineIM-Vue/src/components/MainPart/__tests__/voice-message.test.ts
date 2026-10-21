// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const toastError = vi.fn()
vi.mock('vue-sonner', () => ({ toast: { error: toastError, info: vi.fn(), warning: vi.fn() } }))

const voice = {
  file_id: 'upl_0123456789abcdef0123456789abcdef',
  type: 'voice',
  url: '/onlineim/chats/voices/202610/a.webm',
  name: 'voice.webm',
  size: 5000,
  mime: 'audio/webm',
  duration_ms: 12_300,
  duration: 12,
}

const played: string[] = []
class FakeAudio extends EventTarget {
  src = ''
  preload = ''
  currentTime = 0
  paused = true
  play() {
    played.push(this.src)
    this.paused = false
    this.dispatchEvent(new Event('playing'))
    return Promise.resolve()
  }
  pause() {
    this.paused = true
    this.dispatchEvent(new Event('pause'))
  }
  load() {}
  removeAttribute() {}
}

describe('voice message bubble', () => {
  beforeEach(() => {
    played.length = 0
    vi.stubGlobal('Audio', FakeAudio)
    document.body.innerHTML = ''
  })

  it('renders duration, plays/pauses, tracks progress and only one plays at a time', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const first = mount(UserTextArea, { props: { message: JSON.stringify(voice), messageType: 'voice' }, attachTo: document.body })
    const second = mount(UserTextArea, {
      props: { message: JSON.stringify({ ...voice, url: '/onlineim/chats/voices/202610/b.webm' }), messageType: 'voice', isSelf: true },
      attachTo: document.body,
    })
    expect(first.get('[data-testid="voice-duration"]').text()).toBe('12″')
    expect(first.get('[data-testid="media-voice"]').attributes('data-state')).toBe('paused')

    await first.get('[data-testid="voice-play"]').trigger('click')
    await flushPromises()
    expect(played).toEqual(['/onlineim/chats/voices/202610/a.webm'])
    expect(first.get('[data-testid="media-voice"]').attributes('data-state')).toBe('playing')

    await second.get('[data-testid="voice-play"]').trigger('click')
    await flushPromises()
    expect(first.get('[data-testid="media-voice"]').attributes('data-state')).toBe('paused')
    expect(second.get('[data-testid="media-voice"]').attributes('data-state')).toBe('playing')

    await second.get('[data-testid="voice-play"]').trigger('click')
    expect(second.get('[data-testid="media-voice"]').attributes('data-state')).toBe('paused')
  })

  it('shows placeholder for invalid voice content', async () => {
    const { default: MediaMessage } = await import('@/components/MainPart/MediaMessage.vue')
    const wrapper = mount(MediaMessage, { props: { messageType: 'voice', content: '{"url":"javascript:alert(1)"}' } })
    expect(wrapper.get('[data-testid="media-invalid"]').text()).toBe('[语音]')
  })

  it('composer exposes a record button that reports microphone denial', async () => {
    vi.doMock('@/components/independent/emojiArea/EmojiArea.vue', () => ({
      default: { name: 'EmojiArea', template: '<div />', methods: { toggleEmojiArea() {} } },
    }))
    Object.defineProperty(window, 'MediaRecorder', { value: class { static isTypeSupported() { return true } }, configurable: true })
    Object.defineProperty(navigator, 'mediaDevices', {
      value: { getUserMedia: () => Promise.reject(Object.assign(new Error('no'), { name: 'NotAllowedError' })) },
      configurable: true,
    })
    const { default: Tools } = await import('@/components/MainPart/tools.vue')
    const wrapper = mount(Tools, { attachTo: document.body })
    const button = wrapper.get('[data-testid="voice-record-button"]')
    await button.trigger('pointerdown')
    await flushPromises()
    await button.trigger('pointerup')
    expect(toastError).toHaveBeenCalledWith(expect.stringContaining('麦克风'))
    expect(wrapper.find('[data-testid="voice-recording-panel"]').exists()).toBe(false)
  })
})
