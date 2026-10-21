export type ChatMediaKind = 'image' | 'file'

/** 与后端上传接口及图片/文件消息 content 一致的字段（snake_case） */
export interface ChatMedia {
  file_id: string
  type: ChatMediaKind
  url: string
  thumbnail_url?: string
  name: string
  size: number
  mime: string
  width?: number
  height?: number
  thumbnail_width?: number
  thumbnail_height?: number
}

/** 客户端预检上限，与后端默认 im.upload.* 保持一致；最终以服务端校验为准 */
export const IMAGE_MAX_BYTES = 10 * 1024 * 1024
export const FILE_MAX_BYTES = 20 * 1024 * 1024
export const IMAGE_ACCEPT = 'image/jpeg,image/png,image/gif,image/bmp'

export function isMediaMessageType(messageType?: string): messageType is ChatMediaKind {
  return messageType === 'image' || messageType === 'file'
}

/** 解析图片/文件消息的 content（JSON 字符串或对象）；无法解析时返回 null，界面按占位显示 */
export function parseMediaContent(messageType: string | undefined, content: unknown): ChatMedia | null {
  if (!isMediaMessageType(messageType) || content == null) return null
  let value: unknown = content
  if (typeof content === 'string') {
    const trimmed = content.trim()
    if (!trimmed.startsWith('{')) return null
    try {
      value = JSON.parse(trimmed)
    } catch {
      return null
    }
  }
  if (!value || typeof value !== 'object') return null
  const media = value as Partial<ChatMedia>
  if (typeof media.url !== 'string' || !isSafeMediaUrl(media.url)) return null
  if (media.thumbnail_url !== undefined && (typeof media.thumbnail_url !== 'string' || !isSafeMediaUrl(media.thumbnail_url))) {
    return { ...media, thumbnail_url: undefined } as ChatMedia
  }
  return media as ChatMedia
}

/** 只允许同源相对路径或 http(s)，防止 javascript: 等协议被当作链接渲染 */
export function isSafeMediaUrl(url: string): boolean {
  if (url.startsWith('/') && !url.startsWith('//')) return true
  return /^https?:\/\//iu.test(url)
}

export function formatFileSize(bytes?: number): string {
  if (bytes === undefined || bytes === null || !Number.isFinite(bytes) || bytes < 0) return ''
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value >= 100 ? Math.round(value) : value.toFixed(1)} ${units[unit]}`
}

/** 本地预检；返回错误提示，通过返回 null */
export function validateBeforeUpload(kind: ChatMediaKind, file: File): string | null {
  if (file.size === 0) return '不能发送空文件'
  if (kind === 'image') {
    if (!IMAGE_ACCEPT.split(',').includes(file.type)) return '仅支持 JPG、PNG、GIF、BMP 图片，其他格式请作为文件发送'
    if (file.size > IMAGE_MAX_BYTES) return `图片不能超过 ${formatFileSize(IMAGE_MAX_BYTES)}`
  } else if (file.size > FILE_MAX_BYTES) {
    return `文件不能超过 ${formatFileSize(FILE_MAX_BYTES)}`
  }
  return null
}

/** 把上传错误（经 api 拦截器归一化）转成提示文案；优先使用后端返回的中文说明 */
export function uploadErrorMessage(error: unknown): string {
  const err = (error || {}) as { status?: number; message?: string; code?: string }
  const serverMessage = err.message && /[\u4e00-\u9fa5]/u.test(err.message) ? err.message : ''
  if (err.status === 413) return serverMessage || '文件过大'
  if (err.status === 415) return serverMessage || '不支持的文件类型'
  if (err.status === 401) return '登录已失效，请重新登录'
  if (err.code === 'REQUEST_CANCELLED') return '已取消上传'
  return serverMessage || err.message || '上传失败，请重试'
}
