export type PresenceStatus = 'online' | 'offline'

export interface PresenceRecord {
  user_id: string
  status?: PresenceStatus | null
  last_seen_at?: number | null
  visible?: boolean
}

export interface PresenceUpdate {
  user_id: string
  status: PresenceStatus
  last_seen_at?: number | null
  server_time?: number
}

/** 在线 / 离线文案。没有记录或无权查看时显示“离线”，不暴露上次在线时间。 */
export function formatPresence(record: PresenceRecord | undefined, now = Date.now()): string {
  if (!record || record.visible === false || record.status !== 'online') {
    if (!record || record.visible === false || record.last_seen_at == null) return '离线'
    return `离线 · ${formatLastSeen(record.last_seen_at, now)}`
  }
  return '在线'
}

export function formatLastSeen(lastSeenAt: number, now = Date.now()): string {
  const delta = Math.max(0, now - lastSeenAt)
  if (delta < 60_000) return '刚刚'
  const minutes = Math.floor(delta / 60_000)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24 && sameDay(lastSeenAt, now)) return `${hours} 小时前`
  const seen = new Date(lastSeenAt)
  const clock = `${pad(seen.getHours())}:${pad(seen.getMinutes())}`
  const startOfToday = new Date(now)
  startOfToday.setHours(0, 0, 0, 0)
  const startOfSeen = new Date(lastSeenAt)
  startOfSeen.setHours(0, 0, 0, 0)
  const dayGap = Math.round((startOfToday.getTime() - startOfSeen.getTime()) / 86_400_000)
  if (dayGap === 1) return `昨天 ${clock}`
  if (seen.getFullYear() === new Date(now).getFullYear()) {
    return `${seen.getMonth() + 1}月${seen.getDate()}日 ${clock}`
  }
  return `${seen.getFullYear()}年${seen.getMonth() + 1}月${seen.getDate()}日 ${clock}`
}

function sameDay(left: number, right: number): boolean {
  const a = new Date(left)
  const b = new Date(right)
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}
