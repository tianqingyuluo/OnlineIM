import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = { post: vi.fn() }

vi.mock('../api.service', () => ({ default: apiMock }))
vi.mock('vue-sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

describe('RegisterService.register', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.post.mockResolvedValue({ data: {} })
  })

  it('sends the trimmed nickname entered on the form', async () => {
    const { RegisterService } = await import('@/services/register.service')

    await RegisterService.register({ username: 'user_01', password: 'secret1', confirmPassword: 'secret1', nickname: '  小明  ' })

    expect(apiMock.post).toHaveBeenCalledWith('/auth/register', { username: 'user_01', password: 'secret1', nickname: '小明' })
  })

  it('omits an empty nickname so the server generates one', async () => {
    const { RegisterService } = await import('@/services/register.service')

    await RegisterService.register({ username: 'user_01', password: 'secret1', confirmPassword: 'secret1', nickname: '   ' })

    expect(apiMock.post).toHaveBeenCalledWith('/auth/register', { username: 'user_01', password: 'secret1' })
  })
})
