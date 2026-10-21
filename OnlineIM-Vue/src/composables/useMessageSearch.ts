import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { MessageService } from '@/services/message.service'
import type { MessageSearchItem } from '@/type/message'
import { normalizeSearchKeyword, SEARCH_DEBOUNCE_MS, SEARCH_PAGE_SIZE } from '@/utils/message-search'

/**
 * 消息搜索状态：输入停止 300ms 后请求第一页，“加载更多”按 next_cursor 翻页。
 * 新的关键字会取消还没返回的旧请求，旧请求的结果也不会覆盖新结果。
 */
export function useMessageSearch(query: Ref<string>, conversationId?: () => string | undefined) {
  const keyword = ref('')
  const results = ref<MessageSearchItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const hasMore = ref(false)
  const searched = ref(false)
  let cursor: string | null = null
  let timer: ReturnType<typeof setTimeout> | null = null
  let controller: AbortController | null = null
  let generation = 0

  function reset() {
    generation++
    controller?.abort()
    controller = null
    keyword.value = ''
    results.value = []
    loading.value = false
    loadingMore.value = false
    error.value = ''
    hasMore.value = false
    searched.value = false
    cursor = null
  }

  async function fetchPage(more: boolean) {
    const q = keyword.value
    if (!q) return
    const current = ++generation
    controller?.abort()
    controller = new AbortController()
    if (more) loadingMore.value = true
    else loading.value = true
    error.value = ''
    try {
      const response = await MessageService.searchMessages({
        q,
        conversation_id: conversationId?.() || undefined,
        cursor: more ? cursor || undefined : undefined,
        size: SEARCH_PAGE_SIZE,
      }, controller.signal)
      if (current !== generation) return
      const seen = new Set(more ? results.value.map(item => item.message.message_id) : [])
      const page = (response.results || []).filter(item => !seen.has(item.message.message_id))
      results.value = more ? [...results.value, ...page] : page
      cursor = response.next_cursor
      hasMore.value = !!response.has_more && !!response.next_cursor
      searched.value = true
    } catch (err: unknown) {
      if (current !== generation) return
      const code = (err as { code?: string })?.code
      if (code === 'REQUEST_CANCELLED') return
      error.value = (err as { message?: string })?.message || '搜索失败'
      searched.value = true
      if (!more) results.value = []
    } finally {
      if (current === generation) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  function searchNow() {
    if (timer) clearTimeout(timer)
    timer = null
    const next = normalizeSearchKeyword(query.value)
    if (!next) {
      reset()
      return
    }
    keyword.value = next
    cursor = null
    void fetchPage(false)
  }

  function loadMore() {
    if (!hasMore.value || loading.value || loadingMore.value) return
    void fetchPage(true)
  }

  watch(query, value => {
    if (timer) clearTimeout(timer)
    if (!normalizeSearchKeyword(value)) {
      reset()
      return
    }
    loading.value = true
    timer = setTimeout(searchNow, SEARCH_DEBOUNCE_MS)
  })

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
    controller?.abort()
  })

  return { keyword, results, loading, loadingMore, error, hasMore, searched, searchNow, loadMore, reset }
}
