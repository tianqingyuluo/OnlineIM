import type { MessageResponse } from '@/type/message'

export const ENCRYPTED_PREVIEW = '[加密消息]'
export const LOCKED_TEXT = '🔒 [加密消息 · 当前设备无密钥，请导入密钥恢复]'

export function looksLikeEnvelope(content?: string | null): boolean {
  if (!content || content.trimStart().charAt(0) !== '{') return false
  if (!content.includes('"ciphertext"') || !content.includes('"iv"')) return false
  try {
    const parsed = JSON.parse(content) as { ciphertext?: unknown, iv?: unknown }
    return typeof parsed.ciphertext === 'string' && parsed.ciphertext.length > 0
      && typeof parsed.iv === 'string' && parsed.iv.length > 0
  } catch {
    return false
  }
}

export function isEncryptedMessage(message: Pick<MessageResponse, 'is_encrypted' | 'e2ee_status' | 'content' | 'e2ee_envelope'>): boolean {
  if (message.is_encrypted) return true
  if (message.e2ee_status === 'decrypted' || message.e2ee_status === 'locked') return true
  const raw = typeof message.content === 'string' ? message.content : ''
  return looksLikeEnvelope(message.e2ee_envelope || raw)
}

/** 还没在本机解开、需要再试一次解密的消息。 */
export function needsDecrypt(message: MessageResponse): boolean {
  if (message.e2ee_status === 'decrypted') {
    const text = typeof message.content === 'string' ? message.content : ''
    if (text && !looksLikeEnvelope(text)) return false
  }
  if (message.e2ee_status === 'locked') return true
  if (message.is_encrypted) return true
  const raw = message.e2ee_envelope || (typeof message.content === 'string' ? message.content : '')
  return looksLikeEnvelope(raw)
}

export function markLocked(message: MessageResponse, envelope: string): MessageResponse {
  return {
    ...message,
    is_encrypted: true,
    e2ee_status: 'locked',
    e2ee_envelope: envelope || message.e2ee_envelope,
    content: LOCKED_TEXT,
  }
}

/** 引用快照在服务端是 [加密消息]；本机如果已经解开原文，就用原文替换预览。 */
export function hydrateReplyPreviews(messages: MessageResponse[]): MessageResponse[] {
  const byId = new Map(messages.map(message => [message.message_id, message]))
  for (const message of messages) {
    const reference = message.reply_to
    if (!reference) continue
    const preview = reference.preview_text || ''
    const target = byId.get(reference.message_id)
    if (target?.e2ee_status === 'decrypted' && target.content && !looksLikeEnvelope(target.content)) {
      reference.preview_text = truncatePreview(target.content)
      continue
    }
    if (target?.e2ee_status === 'locked') {
      reference.preview_text = ENCRYPTED_PREVIEW
      continue
    }
    const placeholder = !preview || preview === ENCRYPTED_PREVIEW || looksLikeEnvelope(preview)
    if (!placeholder) continue
    if (looksLikeEnvelope(preview)) {
      reference.preview_text = ENCRYPTED_PREVIEW
    }
  }
  return messages
}

function truncatePreview(value: string): string {
  const compact = value.trim().replace(/\s+/gu, ' ')
  const chars = [...compact]
  if (chars.length <= 80) return compact
  return `${chars.slice(0, 80).join('')}…`
}
