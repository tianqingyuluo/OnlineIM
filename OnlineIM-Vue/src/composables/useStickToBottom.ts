import { nextTick, watch, type Ref } from 'vue'

export interface StickMessage {
  message_id: string
}

/**
 * 新消息到达时自动滚到底部：
 * - 自己发出的消息总是滚到底部；
 * - 别人的消息只有在用户本来就停在底部附近（threshold 像素内）时才滚动，避免打断正在翻看历史的用户。
 * 只在“最后一条消息”变化时触发；向上加载更早的历史（在顶部插入）不会触发。
 */
export function useStickToBottom<T extends StickMessage>(
  container: Ref<HTMLElement | null>,
  messages: () => T[],
  isOwn: (message: T) => boolean,
  threshold = 160,
) {
  function isNearBottom(): boolean {
    const element = container.value
    if (!element) return true
    return element.scrollHeight - element.scrollTop - element.clientHeight <= threshold
  }

  function scrollToBottom() {
    const element = container.value
    if (element) element.scrollTop = element.scrollHeight
  }

  watch(
    () => {
      const list = messages()
      return list.length ? list[list.length - 1] : undefined
    },
    (last, previous) => {
      if (!last || last.message_id === previous?.message_id) return
      // flush: 'pre' 时 DOM 还是旧的，此时判断用户是否停在底部
      const shouldStick = isOwn(last) || isNearBottom()
      if (!shouldStick) return
      void nextTick(() => {
        scrollToBottom()
        // 图片/语音等异步内容撑高后再补一次
        setTimeout(scrollToBottom, 120)
      })
    },
    { flush: 'pre' },
  )

  return { scrollToBottom, isNearBottom }
}
