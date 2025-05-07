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
import { z } from 'zod'
import { RegisterService } from '@/services/register.service'
import { useRouter } from 'vue-router'

const router = useRouter()

// 表单验证规则
const formSchema = toTypedSchema(
    z.object({
      username: z.string({
        required_error: RegisterService.validationRules.username.messages.required
      })
          .min(
              RegisterService.validationRules.username.min,
              RegisterService.validationRules.username.messages.min
          )
          .max(
              RegisterService.validationRules.username.max,
              RegisterService.validationRules.username.messages.max
          ),
      password: z.string({
        required_error: RegisterService.validationRules.password.messages.required
      })
          .min(
              RegisterService.validationRules.password.min,
              RegisterService.validationRules.password.messages.min
          )
          .max(
              RegisterService.validationRules.password.max,
              RegisterService.validationRules.password.messages.max
          ),
      confirmPassword: z.string({
        required_error: RegisterService.validationRules.confirmPassword.messages.required
      })
    }).refine(
        data => data.password === data.confirmPassword,
        {
          message: RegisterService.validationRules.confirmPassword.messages.mismatch,
          path: ["confirmPassword"]
        }
    )
)

const { handleSubmit, setFieldError } = useForm({
  validationSchema: formSchema,
  initialValues: {
    username: '',
    password: '',
    confirmPassword: ''
  }
})

const onSubmit = handleSubmit(async (values) => {
  await RegisterService.register(values, { setFieldError })
  await router.push('/login') // 注册成功后跳转到登录页
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
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 15a4 4 0 004 4h10a4 4 0 004-4v-4H3v4z" />
            </svg>
          </div>
          <span class="font-medium text-gray-800">用户注册</span>
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

          <FormField v-slot="{ componentField, errorMessage }" name="confirmPassword">
            <FormItem>
              <FormLabel class="text-sm font-medium text-gray-700">确认密码</FormLabel>
              <FormControl>
                <Input
                    type="password"
                    placeholder="请再次输入密码"
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
            注册
          </Button>
        </form>

        <div class="text-center text-sm text-gray-400 pt-4 border-t border-gray-100">
          已有账号？
          <router-link to="/login" class="text-gray-500 hover:text-gray-700 font-medium">立即登录</router-link>
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
/* 保持与登录页面一致的样式 */
.bg-white {
  border-top-left-radius: 0;
}
.bg-gray-100 {
  border-top-right-radius: 0;
}

:focus {
  outline: none;
}
</style>