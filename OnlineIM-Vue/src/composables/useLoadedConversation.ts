/**
 * 记住聊天窗口实际加载的会话。
 * 窗口卸载时路由已经切走（例如手机返回列表、桌面切到好友页），按路由算出的会话 ID 已经是空的，
 * 不能再用它去“离开会话”，否则这个会话一直被当成正在查看：新消息不计未读。
 */
export function useLoadedConversation(leave: (conversationId: string) => void) {
  let loaded = ''
  return {
    /** 会话历史加载完成后调用 */
    enter(conversationId: string) {
      if (loaded && loaded !== conversationId) leave(loaded)
      loaded = conversationId
    },
    /** 窗口卸载时调用；fallback 是此刻按路由算出的 ID（可能为空） */
    leave(fallback?: string) {
      const id = loaded || fallback
      loaded = ''
      if (id) leave(id)
    },
    current: () => loaded,
  }
}
