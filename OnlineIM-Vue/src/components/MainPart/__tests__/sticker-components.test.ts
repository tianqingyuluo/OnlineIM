// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const listEmojis = vi.fn()
const uploadEmoji = vi.fn()
const deleteEmoji = vi.fn()
const reorderEmojis = vi.fn()
const saveEmojiFromMessage = vi.fn()
const toastError = vi.fn()
const toastSuccess = vi.fn()
const toastInfo = vi.fn()
vi.mock('@/services/emoji.service', () => ({ listEmojis, uploadEmoji, deleteEmoji, reorderEmojis, saveEmojiFromMessage }))
vi.mock('vue-sonner', () => ({ toast: { error: toastError, success: toastSuccess, info: toastInfo } }))

const emoji = (n: number, extra: Record<string, unknown> = {}) => ({
  emoji_id: `emo_${String(n).padStart(32, '0')}`,
  type: 'emoji',
  url: `/onlineim/chats/emojis/202610/${String(n).padStart(32, 'a')}.png`,
  name: `表情${n}`,
  mime: 'image/png',
  size: 100,
  width: 64,
  height: 64,
  animated: false,
  ...extra,
})

function listResponse(emojis: unknown[], maxCount = 100) {
  return { emojis, total: emojis.length, max_count: maxCount, max_size: 2 * 1024 * 1024, max_dimension: 1024, formats: ['png', 'jpeg', 'gif'] }
}

describe('sticker components', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  it('renders an emoji message as a bubble-less image that keeps gif animation', async () => {
    const { default: UserTextArea } = await import('@/components/MainPart/UserTextArea.vue')
    const gif = emoji(1, { mime: 'image/gif', animated: true, width: 280, height: 140, url: '/onlineim/chats/emojis/202610/x.gif' })
    const wrapper = mount(UserTextArea, { props: { message: JSON.stringify(gif), messageType: 'emoji' } })
    const img = wrapper.get('[data-testid="sticker-message"]')
    expect(img.attributes('src')).toBe(gif.url)
    expect(img.attributes('data-animated')).toBe('true')
    expect(img.attributes('style')).toContain('width: 140px')
    expect(wrapper.find('.bg-slate-50').exists()).toBe(false)

    const broken = mount(UserTextArea, { props: { message: 'emo_x', messageType: 'emoji' } })
    expect(broken.get('[data-testid="sticker-invalid"]').text()).toBe('[表情]')
    const recalled = mount(UserTextArea, { props: { message: JSON.stringify(gif), messageType: 'emoji', recalled: true } })
    expect(recalled.text()).toContain('消息已撤回')
    expect(recalled.find('[data-testid="sticker-message"]').exists()).toBe(false)
  })

  it('lists my emojis, sends on click and manages delete / move to front', async () => {
    listEmojis.mockResolvedValue(listResponse([emoji(1), emoji(2)]))
    deleteEmoji.mockResolvedValue(undefined)
    reorderEmojis.mockImplementation(async (ids: string[]) => listResponse(ids.map(id => emoji(Number(id.slice(4))))))
    const { default: CustomEmojiArea } = await import('@/components/independent/emojiArea/CustomEmojiArea.vue')
    const wrapper = mount(CustomEmojiArea)
    await flushPromises()

    expect(wrapper.get('[data-testid="emoji-count"]').text()).toBe('2/100')
    const tiles = wrapper.findAll('[data-testid="custom-emoji"]')
    expect(tiles).toHaveLength(2)
    await tiles[1].trigger('click')
    expect((wrapper.emitted('send') as unknown[][])[0][0]).toMatchObject({ emoji_id: emoji(2).emoji_id })

    await wrapper.get('[data-testid="emoji-manage-toggle"]').trigger('click')
    expect(wrapper.find('[data-testid="emoji-add-button"]').exists()).toBe(false)
    await wrapper.findAll('[data-testid="custom-emoji"]')[0].trigger('click')
    expect(wrapper.emitted('send')).toHaveLength(1)

    await wrapper.get('[data-testid="emoji-move-front"]').trigger('click')
    await flushPromises()
    expect(reorderEmojis).toHaveBeenCalledWith([emoji(2).emoji_id, emoji(1).emoji_id])
    expect(wrapper.findAll('[data-testid="custom-emoji"]')[0].attributes('data-emoji-id')).toBe(emoji(2).emoji_id)

    await wrapper.findAll('[data-testid="emoji-delete"]')[0].trigger('click')
    await flushPromises()
    expect(deleteEmoji).toHaveBeenCalledWith(emoji(2).emoji_id)
    expect(wrapper.findAll('[data-testid="custom-emoji"]')).toHaveLength(1)
  })

  it('uploads a picked file and blocks adding when the collection is full', async () => {
    listEmojis.mockResolvedValue(listResponse([emoji(1)], 2))
    uploadEmoji.mockResolvedValue(emoji(3))
    const { default: CustomEmojiArea } = await import('@/components/independent/emojiArea/CustomEmojiArea.vue')
    const wrapper = mount(CustomEmojiArea)
    await flushPromises()

    const input = wrapper.get('[data-testid="emoji-upload-input"]')
    const png = new File(['png'], 'a.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { value: [png], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(uploadEmoji).toHaveBeenCalledWith(png)
    expect(wrapper.get('[data-testid="emoji-count"]').text()).toBe('2/2')
    expect(wrapper.findAll('[data-testid="custom-emoji"]')[0].attributes('data-emoji-id')).toBe(emoji(3).emoji_id)

    await input.trigger('change')
    await flushPromises()
    expect(uploadEmoji).toHaveBeenCalledTimes(1)
    expect(toastError).toHaveBeenCalledWith(expect.stringContaining('上限（2 个）'))

    const webp = new File(['w'], 'a.webp', { type: 'image/webp' })
    Object.defineProperty(input.element, 'files', { value: [webp], configurable: true })
    await input.trigger('change')
    expect(toastError).toHaveBeenLastCalledWith(expect.stringContaining('PNG'))
  })

  it('saves a received message to the collection and reports server errors', async () => {
    const { useEmojiStore } = await import('@/stores/emoji')
    const store = useEmojiStore()
    saveEmojiFromMessage.mockResolvedValueOnce(emoji(5))
    await store.saveFromMessage('grp_1', 'msg_1')
    expect(saveEmojiFromMessage).toHaveBeenCalledWith('grp_1', 'msg_1')
    expect(toastSuccess).toHaveBeenCalledWith('已添加到表情')

    saveEmojiFromMessage.mockRejectedValueOnce({ status: 409, message: '表情数量已达上限（100 个），请先删除一些再添加' })
    await store.saveFromMessage('grp_1', 'msg_2')
    expect(toastError).toHaveBeenLastCalledWith('表情数量已达上限（100 个），请先删除一些再添加')

    await store.saveFromMessage('grp_1', 'local_abc')
    expect(saveEmojiFromMessage).toHaveBeenCalledTimes(2)
    expect(toastInfo).toHaveBeenCalled()
  })
})
