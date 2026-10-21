// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { nextTick, reactive, ref } from 'vue'
import { useStickToBottom } from '@/composables/useStickToBottom'

function container(scrollHeight: number, scrollTop: number, clientHeight: number) {
  const el = document.createElement('div')
  Object.defineProperty(el, 'scrollHeight', { get: () => scrollHeight, configurable: true })
  Object.defineProperty(el, 'clientHeight', { get: () => clientHeight, configurable: true })
  el.scrollTop = scrollTop
  return el
}

describe('useStickToBottom', () => {
  it('scrolls on new messages only when near bottom or own message, not on prepended history', async () => {
    const el = container(2000, 1500, 500) // 在底部
    const box = ref<HTMLElement | null>(el)
    const state = reactive({ list: [{ message_id: 'a', own: false }] })
    useStickToBottom(box, () => state.list, m => m.own, 300)

    // 别人发来消息，用户在底部 -> 滚动
    Object.defineProperty(el, 'scrollHeight', { get: () => 2200, configurable: true })
    state.list.push({ message_id: 'b', own: false })
    await nextTick(); await nextTick()
    expect(el.scrollTop).toBe(2200)

    // 用户翻到上面，别人的消息不打断
    el.scrollTop = 100
    state.list.push({ message_id: 'c', own: false })
    await nextTick(); await nextTick()
    expect(el.scrollTop).toBe(100)

    // 自己发的消息总是滚到底部
    state.list.push({ message_id: 'd', own: true })
    await nextTick(); await nextTick()
    expect(el.scrollTop).toBe(2200)

    // 顶部插入更早的历史（最后一条不变）不滚动
    el.scrollTop = 0
    state.list.unshift({ message_id: 'old', own: false })
    await nextTick(); await nextTick()
    expect(el.scrollTop).toBe(0)
  })
})
