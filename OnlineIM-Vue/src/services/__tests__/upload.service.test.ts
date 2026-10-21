import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = { post: vi.fn() }
vi.mock('@/services/api.service', () => ({ default: apiMock }))

describe('uploadChatMedia', () => {
  beforeEach(() => vi.clearAllMocks())

  it('posts multipart form data and reports progress', async () => {
    apiMock.post.mockImplementation(async (_url, _form, config) => {
      config.onUploadProgress({ loaded: 5, total: 10 })
      return { data: { status: 'success', file_id: 'upl_1', url: '/onlineim/chats/files/x.txt', name: 'a.txt' } }
    })
    const { uploadChatMedia } = await import('@/services/upload.service')
    const progress: number[] = []
    const file = new File(['hello'], 'a.txt')
    const media = await uploadChatMedia('file', file, { onProgress: value => progress.push(value) })

    const [url, form, config] = apiMock.post.mock.calls[0]
    expect(url).toBe('/messages/upload')
    expect((form as FormData).get('type')).toBe('file')
    expect(((form as FormData).get('file') as File).name).toBe('a.txt')
    expect(config.headers['Content-Type']).toBe('multipart/form-data')
    expect(progress).toEqual([50])
    expect(media).toEqual({ file_id: 'upl_1', url: '/onlineim/chats/files/x.txt', name: 'a.txt' })
  })

  it('rejects responses without file metadata', async () => {
    apiMock.post.mockResolvedValue({ data: { status: 'success' } })
    const { uploadChatMedia } = await import('@/services/upload.service')
    await expect(uploadChatMedia('image', new File(['x'], 'a.png'))).rejects.toThrow('上传响应缺少文件信息')
  })
})
