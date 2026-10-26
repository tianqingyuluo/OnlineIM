import type { CallRecordContent, CallType } from '@/type/call'

export function createCallId(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `call_${hex}`
}

export function formatCallDuration(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds || 0))
  return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}`
}

export function parseCallContent(content: unknown): CallRecordContent | null {
  let value: unknown = content
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value)
    } catch {
      return null
    }
  }
  if (!value || typeof value !== 'object') return null
  const record = value as CallRecordContent
  if (typeof record.call_type !== 'string' && typeof record.call_status !== 'string') return null
  return record
}

/** 与服务端 CallMessageTexts 保持一致，刷新前后会话预览相同。 */
export function callPreviewText(content: unknown): string {
  const record = parseCallContent(content)
  const type = record?.call_type === 'audio' ? 'audio' : 'video'
  const status = record?.call_status || ''
  const duration = formatCallDuration(Number(record?.duration_seconds || 0))
  switch (status) {
    case 'completed':
      return `${type === 'audio' ? '[语音通话]' : '[视频通话]'} ${duration}`
    case 'rejected':
      return '[已拒接]'
    case 'cancelled':
      return '[已取消]'
    case 'busy':
      return '[对方忙线]'
    case 'missed':
      return type === 'audio' ? '[未接语音通话]' : '[未接视频通话]'
    default:
      return type === 'audio' ? '[语音通话]' : '[视频通话]'
  }
}

export function callTypeOf(content: unknown): CallType {
  return parseCallContent(content)?.call_type === 'audio' ? 'audio' : 'video'
}

export function deviceErrorMessage(error: unknown): string {
  const name = error && typeof error === 'object' && 'name' in error
    ? String((error as { name?: unknown }).name || '')
    : ''
  if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
    return '无法使用麦克风或摄像头：权限被拒绝'
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return '未找到可用的麦克风或摄像头'
  }
  if (name === 'NotReadableError' || name === 'TrackStartError') {
    return '麦克风或摄像头被其他应用占用'
  }
  if (error instanceof Error && error.message) return error.message
  return '无法打开麦克风或摄像头'
}
