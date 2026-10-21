/**
 * 会话列表时间工具。服务端时间形如 "2026-10-21 22:34:27"（Asia/Shanghai 本地时间，无时区），
 * 本地临时消息是 ISO 字符串，两种都要能解析与比较。
 */
export function parseTimestamp(value: unknown): number {
  if (value === null || value === undefined || value === '') return 0
  if (typeof value === 'number') return Number.isFinite(value) ? value : 0
  const text = String(value).trim()
  if (/^\d+$/u.test(text)) return Number(text)
  const local = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?$/u.exec(text)
  if (local) {
    const [, y, mo, d, h, mi, s] = local
    return new Date(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi), Number(s || 0)).getTime()
  }
  const parsed = Date.parse(text)
  return Number.isNaN(parsed) ? 0 : parsed
}

const pad = (n: number) => String(n).padStart(2, '0')

/** 转成与服务端一致的 "YYYY-MM-DD HH:mm:ss" 本地时间 */
export function toServerTimestamp(value: unknown = Date.now()): string {
  const ms = parseTimestamp(value) || Date.now()
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 列表展示：今天 HH:mm，昨天，同年 MM-DD，更早 YYYY-MM-DD */
export function formatConversationTime(value: unknown, now: number = Date.now()): string {
  const ms = parseTimestamp(value)
  if (!ms) return ''
  const d = new Date(ms)
  const today = new Date(now)
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  if (ms >= startOfToday) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  if (ms >= startOfToday - 24 * 3600 * 1000) return '昨天'
  if (d.getFullYear() === today.getFullYear()) return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function formatUnread(count?: number): string {
  if (!count || count <= 0) return ''
  return count > 99 ? '99+' : String(count)
}
