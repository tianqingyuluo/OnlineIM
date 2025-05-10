<script setup lang="ts">
import { Button } from '@/components/ui/button'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Toaster } from 'vue-sonner'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import { LoginService } from '@/services/login.service'
// 使用 LoginService 的验证规则
const formSchema = toTypedSchema(
    z.object({
      username: z.string()
          .min(LoginService.validationRules.username.min, LoginService.validationRules.username.messages.min)
          .max(LoginService.validationRules.username.max, LoginService.validationRules.username.messages.max),
      password: z.string()
          .min(LoginService.validationRules.password.min, LoginService.validationRules.password.messages.min)
          .max(LoginService.validationRules.password.max, LoginService.validationRules.password.messages.max)
    })
)

// 表单初始化
const { handleSubmit, setFieldError } = useForm({
  validationSchema: formSchema,
  initialValues: {
    username: '',
    password: ''
  }
})

import { useRouter } from 'vue-router'

// 在组件内部获取路由实例
const router = useRouter()

// 提交处理
const onSubmit = handleSubmit(async (values) => {
  console.log('提交登录表单:', values)
  try {
    const response = await LoginService.login(values, router)
    console.log('登录响应:', response) // 添加调试日志
  } catch (error) {
    console.error('登录错误:', error) // 添加错误日志
  }
})
</script>

<template>
  <div class="min-h-screen flex flex-col items-center justify-center bg-gray-50 p-4">
    <div class="w-full max-w-md bg-white rounded-lg shadow-sm overflow-hidden border border-gray-200">
      <!-- 头部区域 -->
      <div class="flex items-center justify-between w-full p-4 border-b border-gray-200">
        <div class="flex items-center space-x-3">
          <div class="w-10 h-10 bg-gray-100 rounded-lg flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 11c0 3.517-1.009 6.799-2.753 9.571m-3.44-2.04l.054-.09A13.916 13.916 0 008 11a4 4 0 118 0c0 1.017-.07 2.019-.203 3m-2.118 6.844A21.88 21.88 0 0015.171 17m3.839 1.132c.645-2.266.99-4.659.99-7.132A8 8 0 008 4.07M3 15.364c.64-1.319 1-2.8 1-4.364 0-1.457.39-2.823 1.07-4" />
            </svg>
          </div>
          <span class="font-medium text-gray-800">用户登录</span>
        </div>
      </div>

      <!-- 表单内容 -->
      <div class="p-4 space-y-4">
        <form @submit.prevent="onSubmit" class="space-y-4">
          <FormField v-slot="{ componentField, errorMessage }" name="username">
            <FormItem>
              <FormLabel class="text-sm font-medium text-gray-700">用户名</FormLabel>
              <FormControl>
                <Input
                    type="text"
                    placeholder="请输入用户名"
                    v-bind="componentField"
                    class="border-gray-200 focus:border-gray-500 focus:ring-gray-500"
                />
              </FormControl>
              <FormMessage class="text-red-500 text-sm">
                {{ errorMessage }}
              </FormMessage>
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField, errorMessage }" name="password">
            <FormItem>
              <FormLabel class="text-sm font-medium text-gray-700">密码</FormLabel>
              <FormControl>
                <Input
                    type="password"
                    placeholder="请输入密码"
                    v-bind="componentField"
                    class="border-gray-200 focus:border-gray-500 focus:ring-gray-500"
                />
              </FormControl>
              <FormMessage class="text-red-500 text-sm">
                {{ errorMessage }}
              </FormMessage>
            </FormItem>
          </FormField>

          <Button
              type="submit"
              class="w-full bg-black hover:bg-gray-900"
          >
            登录
          </Button>
        </form>

        <div class="text-center text-sm text-gray-400 pt-4 border-t border-gray-100">
          还没有账号？
          <router-link to="/register" class="text-gray-500 hover:text-gray-700 font-medium">立即注册</router-link>
        </div>
      </div>
    </div>

    <Toaster
        position="top-center"
        :theme="'light'"
        richColors
        closeButton
    />
  </div>
</template>

<style scoped>
/* 气泡样式微调，与 UserMainPart.vue 保持一致 */
.bg-white {
  border-top-left-radius: 0;
}
.bg-gray-100 {
  border-top-right-radius: 0;
}

/* 输入框聚焦样式 */
:focus {
  outline: none;
}
</style>