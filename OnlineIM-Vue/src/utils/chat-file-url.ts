import { useUserStore } from '@/stores/user'

/** 聊天图片/文件/语音/表情走 /onlineim/chats/，需要带上当前登录 token 才能通过后端校验。 */
export function isProtectedChatFileUrl(url: string): boolean {
  return url.startsWith('/onlineim/chats/') || /^(https?:)\/\/[^/]+\/onlineim\/chats\//i.test(url)
}

/**
 * 给聊天文件 URL 加上 access_token，供 img / audio / 下载链接使用。
 * 头像和其他地址原样返回。没有登录态时也原样返回（请求会由服务端拒绝）。
 */
export function authorizeChatFileUrl(url: string | null | undefined): string {
  if (!url || !isProtectedChatFileUrl(url)) return url || ''
  const token = readToken()
  if (!token) return url
  return appendAccessToken(url, token)
}

function readToken(): string {
  try {
    return useUserStore().token || ''
  } catch {
    return ''
  }
}

export function appendAccessToken(url: string, token: string): string {
  const hashIndex = url.indexOf('#')
  const hash = hashIndex >= 0 ? url.slice(hashIndex) : ''
  const withoutHash = hashIndex >= 0 ? url.slice(0, hashIndex) : url
  const queryIndex = withoutHash.indexOf('?')
  const path = queryIndex >= 0 ? withoutHash.slice(0, queryIndex) : withoutHash
  const query = queryIndex >= 0 ? withoutHash.slice(queryIndex + 1) : ''
  const params = new URLSearchParams(query)
  params.set('access_token', token)
  return `${path}?${params.toString()}${hash}`
}
