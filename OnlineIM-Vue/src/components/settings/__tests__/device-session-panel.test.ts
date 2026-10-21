// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const list = vi.fn()
const logout = vi.fn()
vi.mock('@/services/device.service', () => ({ deviceService: { list, logout } }))
vi.mock('vue-sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

describe('device session panel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('lists devices and logs out another device', async () => {
    list.mockResolvedValue([
      { device_id: 'web-1', device_type: 'web', online: true, current: true },
      { device_id: 'phone-1', device_type: 'phone', online: false, current: false },
    ])
    logout.mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { default: DeviceSessionPanel } = await import('@/components/settings/DeviceSessionPanel.vue')
    const wrapper = mount(DeviceSessionPanel, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('网页端')
    expect(wrapper.text()).toContain('当前设备')
    expect(wrapper.text()).toContain('手机端')
    const buttons = wrapper.findAll('button').filter(button => button.text() === '登出')
    expect(buttons).toHaveLength(1)
    await buttons[0].trigger('click')
    await flushPromises()

    expect(logout).toHaveBeenCalledWith('phone-1')
  })

  it('shows an error when the list cannot be loaded', async () => {
    list.mockRejectedValue(new Error('offline'))
    const { default: DeviceSessionPanel } = await import('@/components/settings/DeviceSessionPanel.vue')
    const wrapper = mount(DeviceSessionPanel)
    await flushPromises()
    expect(wrapper.text()).toContain('设备列表加载失败')
  })
})