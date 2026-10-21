// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import Register from '@/views/Register.vue'
import Login from '@/views/Login.vue'

function router() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/login', component: { template: '<div />' } },
      { path: '/register', component: { template: '<div />' } },
    ],
  })
}

async function submit(component: object, values: string[]) {
  const wrapper = mount(component, { global: { plugins: [router()] } })
  const inputs = wrapper.findAll('input')
  for (let index = 0; index < values.length; index += 1) {
    await inputs[index].setValue(values[index])
  }
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 30))
  await flushPromises()
  return wrapper
}

describe('auth form validation', () => {
  it('rejects a short username and a non matching password on register', async () => {
    const wrapper = await submit(Register, ['ab', 'secret1', 'secret2'])

    expect(wrapper.text()).toContain('用户名长度必须在4-16之间')
    expect(wrapper.text()).toContain('两次输入密码不一致')
  })

  it('rejects username characters outside letters, digits, and underscore', async () => {
    const wrapper = await submit(Register, ['user-name', 'secret1', 'secret1'])

    expect(wrapper.text()).toContain('用户名只能包含字母、数字和下划线')
  })

  it('rejects passwords outside 6 to 20 characters', async () => {
    const wrapper = await submit(Register, ['user_01', '12345', '12345'])

    expect(wrapper.text()).toContain('密码长度必须在6-20之间')
  })

  it('uses the same username and password rules on login', async () => {
    const wrapper = await submit(Login, ['user-name', '12345'])

    expect(wrapper.text()).toContain('用户名只能包含字母、数字和下划线')
    expect(wrapper.text()).toContain('密码长度必须在6-20之间')
  })
})
