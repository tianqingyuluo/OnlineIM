import { isSafeMediaUrl } from '@/utils/media-message'

/** 自定义表情（与后端 /emojis 接口及表情消息 content 一致，snake_case） */
export interface CustomEmoji {
  emoji_id: string
  type?: 'emoji'
  url: string
  name: string
  mime: string
  size: number
  width?: number
  height?: number
  animated?: boolean
  sort_order?: number
  created_at?: string
}

/** 列表接口附带的限制；服务端为准，前端只用于预检和展示 */
export interface EmojiLimits {
  max_count: number
  max_size: number
  max_dimension: number
  formats: string[]
}

/** 与后端 im.emoji.* 默认值一致 */
export const DEFAULT_EMOJI_LIMITS: EmojiLimits = {
  max_count: 100,
  max_size: 2 * 1024 * 1024,
  max_dimension: 1024,
  formats: ['png', 'jpeg', 'gif'],
}
/** 静态图源文件上限（服务端会等比缩小），GIF 必须直接满足 max_size */
export const EMOJI_SOURCE_MAX_BYTES = 10 * 1024 * 1024
export const EMOJI_ACCEPT = 'image/png,image/jpeg,image/gif'

export function isStickerMessageType(messageType?: string): boolean {
  return messageType === 'emoji' || messageType === 'sticker'
}

/** 图片和表情消息可以“添加到表情” */
export function canSaveAsEmoji(messageType?: string): boolean {
  return messageType === 'image' || isStickerMessageType(messageType)
}

/** 解析表情消息 content（JSON 字符串或对象）；无效时返回 null，界面显示 [表情] 占位 */
export function parseStickerContent(messageType: string | undefined, content: unknown): CustomEmoji | null {
  if (!isStickerMessageType(messageType) || content == null) return null
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
  const emoji = value as Partial<CustomEmoji>
  if (typeof emoji.url !== 'string' || !isSafeMediaUrl(emoji.url)) return null
  return emoji as CustomEmoji
}

/** 消息里表情的显示尺寸：长边不超过 max，保持比例；小图不放大 */
export function stickerDisplaySize(width?: number, height?: number, max = 140): { width: number; height: number } {
  if (!width || !height || width <= 0 || height <= 0) return { width: max, height: max }
  const scale = Math.min(1, max / Math.max(width, height))
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

/** 上传前预检；返回错误提示，通过返回 null */
export function validateEmojiFile(file: File, limits: EmojiLimits = DEFAULT_EMOJI_LIMITS): string | null {
  if (file.size === 0) return '不能添加空文件'
  if (!EMOJI_ACCEPT.split(',').includes(file.type)) return '表情只支持 PNG、JPG、GIF 图片'
  if (file.type === 'image/gif' && file.size > limits.max_size) {
    return `动图表情不能超过 ${Math.round(limits.max_size / 1024 / 1024)}MB`
  }
  if (file.size > Math.max(limits.max_size, EMOJI_SOURCE_MAX_BYTES)) {
    return `表情图片不能超过 ${Math.round(Math.max(limits.max_size, EMOJI_SOURCE_MAX_BYTES) / 1024 / 1024)}MB`
  }
  return null
}

/** 把某个表情移到最前，返回新的 id 顺序 */
export function moveToFront(ids: string[], id: string): string[] {
  if (!ids.includes(id)) return ids.slice()
  return [id, ...ids.filter(item => item !== id)]
}
