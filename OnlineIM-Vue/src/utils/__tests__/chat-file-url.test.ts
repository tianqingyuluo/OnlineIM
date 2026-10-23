import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { authorizeChatFileUrl, isProtectedChatFileUrl } from '@/utils/chat-file-url'
import { useUserStore } from '@/stores/user'

describe('authorizeChatFileUrl', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('leaves avatars and logged-out chat urls unchanged', () => {
    expect(isProtectedChatFileUrl('/onlineim/avatars/a.png')).toBe(false)
    expect(authorizeChatFileUrl('/onlineim/avatars/a.png')).toBe('/onlineim/avatars/a.png')
    expect(authorizeChatFileUrl('/onlineim/chats/images/a.png')).toBe('/onlineim/chats/images/a.png')
  })

  it('appends the current token for chat files and replaces a stale one', () => {
    useUserStore().token = 'tok en+'
    expect(authorizeChatFileUrl('/onlineim/chats/images/a.png')).toBe(
      '/onlineim/chats/images/a.png?access_token=tok+en%2B',
    )
    expect(authorizeChatFileUrl('https://im.example.com/onlineim/chats/files/a.pdf?access_token=old')).toBe(
      'https://im.example.com/onlineim/chats/files/a.pdf?access_token=tok+en%2B',
    )
    expect(authorizeChatFileUrl('/onlineim/groups/avatars/g.png')).toBe('/onlineim/groups/avatars/g.png')
  })
})
