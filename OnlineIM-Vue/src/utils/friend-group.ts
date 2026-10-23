const GENERIC_MESSAGES = new Set([
  '啊哦，发生了些小错误',
  '啊哦，发生了些问题',
  '请求处理失败',
  '网络错误',
])

/** 把移动好友分组的失败转成可读中文；登录时的静默迁移不要用这个弹窗。 */
export function friendMoveErrorText(error: unknown): string {
  const message = error && typeof error === 'object' && 'message' in error
    ? (error as { message?: unknown }).message
    : undefined
  if (typeof message === 'string') {
    const text = message.trim()
    if (text && /[\u4e00-\u9fa5]/.test(text) && !GENERIC_MESSAGES.has(text)) {
      return text
    }
  }
  return '移动好友到分组失败'
}
