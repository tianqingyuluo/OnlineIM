import { describe, expect, it } from 'vitest'
import {
  formatFileSize,
  isSafeMediaUrl,
  parseMediaContent,
  uploadErrorMessage,
  validateBeforeUpload,
} from '@/utils/media-message'
import { buildReplyReference } from '@/utils/reply-preview'

const image = {
  file_id: 'upl_0123456789abcdef0123456789abcdef',
  type: 'image',
  url: '/onlineim/chats/images/202610/a.png',
  thumbnail_url: '/onlineim/chats/images/202610/a_thumb.jpg',
  name: '夏天.png',
  size: 2048,
  mime: 'image/png',
  width: 1200,
  height: 800,
}

describe('media message utils', () => {
  it('parses JSON content of image and file messages only', () => {
    expect(parseMediaContent('image', JSON.stringify(image))?.thumbnail_url).toBe(image.thumbnail_url)
    expect(parseMediaContent('file', image)?.name).toBe('夏天.png')
    expect(parseMediaContent('text', JSON.stringify(image))).toBeNull()
    expect(parseMediaContent('image', 'upl_0123')).toBeNull()
    expect(parseMediaContent('image', '{broken')).toBeNull()
  })

  it('rejects unsafe urls', () => {
    expect(parseMediaContent('file', { ...image, url: 'javascript:alert(1)' })).toBeNull()
    expect(parseMediaContent('image', { ...image, thumbnail_url: '//evil.example/x' })?.thumbnail_url).toBeUndefined()
    expect(isSafeMediaUrl('/onlineim/chats/x')).toBe(true)
    expect(isSafeMediaUrl('https://cdn.example/x')).toBe(true)
    expect(isSafeMediaUrl('data:text/html,x')).toBe(false)
  })

  it('formats sizes', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(20 * 1024 * 1024)).toBe('20.0 MB')
    expect(formatFileSize(undefined)).toBe('')
  })

  it('validates before upload', () => {
    expect(validateBeforeUpload('image', new File(['x'], 'a.png', { type: 'image/png' }))).toBeNull()
    expect(validateBeforeUpload('image', new File(['x'], 'a.webp', { type: 'image/webp' }))).toContain('作为文件发送')
    expect(validateBeforeUpload('file', new File([], 'a.txt'))).toBe('不能发送空文件')
    const big = new File(['x'], 'big.bin')
    Object.defineProperty(big, 'size', { value: 21 * 1024 * 1024 })
    expect(validateBeforeUpload('file', big)).toContain('20.0 MB')
  })

  it('maps upload errors to messages', () => {
    expect(uploadErrorMessage({ status: 413, message: '文件超过大小上限 20MB' })).toBe('文件超过大小上限 20MB')
    expect(uploadErrorMessage({ status: 413, message: 'Request failed with status code 413' })).toBe('文件过大')
    expect(uploadErrorMessage({ status: 415 })).toBe('不支持的文件类型')
    expect(uploadErrorMessage({ status: 401 })).toBe('登录已失效，请重新登录')
  })

  it('builds file reply previews with the file name', () => {
    const reference = buildReplyReference({
      message_id: 'msg_1',
      conversation_id: 'conv_1',
      sender_info: { user_id: 'usr_1', username: 'u1' },
      message_type: 'file',
      content: JSON.stringify({ ...image, type: 'file', name: '报告.pdf' }),
      timestamp: '',
      seq_id: '1',
      status: 0,
    } as never)
    expect(reference?.preview_text).toBe('[文件] 报告.pdf')
  })
})
