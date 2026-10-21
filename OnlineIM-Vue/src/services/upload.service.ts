import api from '@/services/api.service'
import type { ChatMedia, ChatMediaKind } from '@/utils/media-message'

export interface UploadOptions {
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

/**
 * 上传聊天图片/文件到 POST /messages/upload。
 * 成功返回服务端元数据（file_id、url、thumbnail_url、尺寸等），发送消息时把它序列化进 content。
 */
export async function uploadChatMedia(
  kind: ChatMediaKind,
  file: File,
  options: UploadOptions = {},
): Promise<ChatMedia> {
  const form = new FormData()
  form.append('type', kind)
  form.append('file', file, file.name)
  const response = await api.post('/messages/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 5 * 60 * 1000,
    signal: options.signal,
    onUploadProgress: event => {
      if (!options.onProgress) return
      const total = event.total || file.size
      options.onProgress(total > 0 ? Math.min(100, Math.round((event.loaded / total) * 100)) : 0)
    },
  })
  const { status: _status, ...media } = response.data || {}
  if (!media.file_id || !media.url) throw new Error('上传响应缺少文件信息')
  return media as ChatMedia
}
