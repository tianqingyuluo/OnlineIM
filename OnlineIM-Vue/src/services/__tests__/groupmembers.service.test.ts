import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}

vi.mock('@/services/api.service', () => ({ default: apiMock }))
vi.mock('vue-sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

describe('groupMembersService 与后端 GroupMemberController 路由一致', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.post.mockResolvedValue({ data: {} })
  })

  it('取消管理员走 POST /members/{memberId}/cancel-admin', async () => {
    const { groupMembersService } = await import('@/services/groupmembers.service')
    await groupMembersService.removeAdmin('grp_1', 'mem_1')
    expect(apiMock.post).toHaveBeenCalledWith('/groups/grp_1/members/mem_1/cancel-admin')
    expect(apiMock.delete).not.toHaveBeenCalled()
  })

  it('禁言走 POST /members/{memberId}/mute，duration 单位为分钟', async () => {
    const { groupMembersService } = await import('@/services/groupmembers.service')
    await groupMembersService.muteMember('grp_1', 'mem_1', 30)
    expect(apiMock.post).toHaveBeenCalledWith('/groups/grp_1/members/mem_1/mute', { duration: 30 })
    expect(apiMock.put).not.toHaveBeenCalled()
  })

  it('解除禁言走 POST /members/{memberId}/unmute', async () => {
    const { groupMembersService } = await import('@/services/groupmembers.service')
    await groupMembersService.unmuteMember('grp_1', 'mem_1')
    expect(apiMock.post).toHaveBeenCalledWith('/groups/grp_1/members/mem_1/unmute')
    expect(apiMock.delete).not.toHaveBeenCalled()
  })

  it('转让群主走 POST /members/{memberId}/transfer-ownership', async () => {
    const { groupMembersService } = await import('@/services/groupmembers.service')
    await groupMembersService.transferOwnership('grp_1', 'mem_2')
    expect(apiMock.post).toHaveBeenCalledWith('/groups/grp_1/members/mem_2/transfer-ownership')
    expect(apiMock.put).not.toHaveBeenCalled()
  })
})
