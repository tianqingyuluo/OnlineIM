import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

describe('router', () => {
  it('不再暴露调试用的 /test 路由', async () => {
    const { default: router } = await import('@/router')
    const paths = router.getRoutes().map((r) => r.path)
    expect(paths).not.toContain('/test')
    expect(router.resolve('/test').matched).toHaveLength(0)
    expect(paths).toContain('/login')
    expect(paths).toContain('/register')
  })
})
