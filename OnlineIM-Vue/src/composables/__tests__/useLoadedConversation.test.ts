import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { computed, ref } from 'vue'

vi.mock('@/utils/indexedDB', () => ({ dbService: {}, STORES: {} }))

import { useHistoryStore } from '@/stores/history'
import { useLoadedConversation } from '@/composables/useLoadedConversation'

describe('useLoadedConversation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('路由切走后卸载，仍然离开实际加载的会话，新消息恢复计未读', () => {
    const history = useHistoryStore()
    const routeId = ref<string | undefined>('conv_a')
    const conversationId = computed(() => routeId.value)
    const lease = useLoadedConversation(id => history.leaveConversation(id))

    history.activeConversationId = conversationId.value!
    lease.enter(conversationId.value!)
    // 手机返回列表：路由先变成 /main/chat，再卸载聊天窗口
    routeId.value = undefined
    lease.leave(conversationId.value)

    expect(history.activeConversationId).toBeNull()
    expect(lease.current()).toBe('')
  })

  it('同一个窗口换会话时离开上一个；没加载过时退回到路由给的 ID', () => {
    const leave = vi.fn()
    const lease = useLoadedConversation(leave)
    lease.enter('conv_a')
    lease.enter('conv_a')
    expect(leave).not.toHaveBeenCalled()
    lease.enter('conv_b')
    expect(leave).toHaveBeenCalledWith('conv_a')
    lease.leave()
    expect(leave).toHaveBeenLastCalledWith('conv_b')

    leave.mockClear()
    useLoadedConversation(leave).leave('conv_route')
    expect(leave).toHaveBeenCalledWith('conv_route')
    useLoadedConversation(leave).leave(undefined)
    expect(leave).toHaveBeenCalledTimes(1)
  })
})
