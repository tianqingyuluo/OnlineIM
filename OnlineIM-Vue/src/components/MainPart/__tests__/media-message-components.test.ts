// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const uploadChatMedia = vi.fn()
const toastError = vi.fn()
vi.mock('@/services/upload.service', () => ({ uploadChatMedia }))
vi.mock('vue-sonner', () => ({ toast: { error: toastError, info: vi.fn() } }))
vi.mock('@/components/independent/emojiArea/EmojiArea.vue', () => ({
  default: { name: 'EmojiArea', template: '<div />', methods: { toggleEmojiArea() {} } },
}))

const image = {
  file_id: 'upl_0123456789abcdef0123456789abcdef',
  type: 'image',
  url: '/onlineim/chats/images/202610/a.png',
  thumbnail_url: '/onlineim/chats/images/202610/a_thumb.jpg',
  name: 'a.png',
  size: 2048,
  mime: 'image/png',
  width: 1200,
  height: 800,
  thumbnail_width: 320,
  thumbnail_height: 213,
}

describe('media message components', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('renders image thumbnail and opens the original on click', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const wrapper = mount(UserTextArea, {
      props: { message: JSON.stringify(image), messageType: 'image' },
      attachTo: document.body,
    })
    const thumb = wrapper.get('[data-testid="media-thumbnail"]')
    expect(thumb.attributes('src')).toBe(image.thumbnail_url)
    await thumb.trigger('click')
    const original = document.querySelector('[data-testid="media-original"]') as HTMLImageElement
    expect(original.getAttribute('src')).toBe(image.url)
    wrapper.unmount()
  })

  it('renders file name, size and download link', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const file = { ...image, type: 'file', name: '报告.pdf', size: 1536, url: '/onlineim/chats/files/202610/b.pdf' }
    delete (file as Partial<typeof image>).thumbnail_url
    const wrapper = mount(UserTextArea, { props: { message: JSON.stringify(file), messageType: 'file' } })
    const link = wrapper.get('[data-testid="media-file"]')
    expect(link.attributes('href')).toBe(file.url)
    expect(link.attributes('download')).toBe('报告.pdf')
    expect(wrapper.get('[data-testid="media-file-size"]').text()).toBe('1.5 KB')
  })

  it('keeps text messages as text', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const wrapper = mount(UserTextArea, { props: { message: '{"url":"/x"}' } })
    expect(wrapper.find('[data-testid="media-image"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('{"url":"/x"}')
  })

  it('uploads a picked file with progress and emits the result', async () => {
    let resolveUpload: (value: unknown) => void = () => {}
    uploadChatMedia.mockImplementation((_kind, _file, options) => {
      options.onProgress(40)
      return new Promise(resolve => { resolveUpload = resolve })
    })
    const { default: Tools } = await import('@/components/MainPart/tools.vue')
    const wrapper = mount(Tools)
    const input = wrapper.get('[data-testid="file-input"]')
    const file = new File(['hello'], 'a.txt', { type: 'text/plain' })
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    expect(wrapper.get('[data-testid="upload-progress"]').text()).toContain('40%')
    expect(wrapper.get('[data-testid="send-file-button"]').attributes('disabled')).toBeDefined()
    resolveUpload({ ...image, type: 'file', name: 'a.txt' })
    await flushPromises()
    expect(uploadChatMedia).toHaveBeenCalledWith('file', file, expect.anything())
    expect(wrapper.emitted('uploaded')?.[0]).toEqual(['file', { ...image, type: 'file', name: 'a.txt' }])
    expect(wrapper.find('[data-testid="upload-progress"]').exists()).toBe(false)
  })

  it('shows upload errors and rejects invalid images before uploading', async () => {
    uploadChatMedia.mockRejectedValue({ status: 413, message: '文件超过大小上限 20MB' })
    const { default: Tools } = await import('@/components/MainPart/tools.vue')
    const wrapper = mount(Tools)
    const input = wrapper.get('[data-testid="file-input"]')
    Object.defineProperty(input.element, 'files', { value: [new File(['x'], 'a.txt')], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(toastError).toHaveBeenCalledWith('文件超过大小上限 20MB')
    expect(wrapper.emitted('uploaded')).toBeUndefined()

    const imageInput = wrapper.get('[data-testid="image-input"]')
    Object.defineProperty(imageInput.element, 'files', { value: [new File(['x'], 'a.svg', { type: 'image/svg+xml' })], configurable: true })
    await imageInput.trigger('change')
    expect(uploadChatMedia).toHaveBeenCalledTimes(1)
  })
})
