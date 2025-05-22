<template>
  <div class="flex bg-white p-6 rounded-lg shadow-sm max-w-5xl mx-auto">
    <!-- 使用封装的好友选择组件 -->
    <FriendSelection v-model="form.initial_members" class="w-2/5 max-h-[500px] overflow-auto" />

    <!-- 右侧创建群组表单 -->
    <div class="w-3/5 pl-8">
      <!-- 标题区域 -->
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-xl font-medium text-gray-800">创建新群组</h2>
        <button @click="closeModal" class="text-gray-500 hover:text-gray-700">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- 头像区域 -->
      <div class="mb-5 flex flex-col items-center">
        <div
          class="w-24 h-24 rounded-full overflow-hidden bg-gray-100 border border-gray-200 relative cursor-pointer hover:opacity-90 transition-opacity"
          @click="changeAvatar"
        >
          <img
            :src="'/images/default-avatar.png'"
            class="w-full h-full object-cover"
            alt="群组头像"
          >
          <div class="absolute inset-0 hover:bg-black/80 bg-opacity-30 flex items-center justify-center transition-all">
            <span class="text-white opacity-0 hover:opacity-100 text-sm">更换头像</span>
          </div>
        </div>
      </div>

      <!-- 群组名称 -->
      <div class="mb-4">
        <label class="block text-sm font-medium text-gray-700 mb-1">群组名称</label>
        <input
          v-model="name"
          type="text"
          required
          class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-1 focus:ring-gray-500"
          :class="{'border-red-500': errors.name}"
        >
        <p v-if="errors.name" class="text-red-500 text-sm mt-1">{{ errors.name }}</p>
      </div>

      <!-- 群组描述 -->
      <div class="mb-4">
        <label class="block text-sm font-medium text-gray-700 mb-1">群组描述</label>
        <textarea
          v-model="description"
          rows="3"
          class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-1 focus:ring-gray-500"
        ></textarea>
      </div>

      <!-- 创建按钮 -->
      <button
        @click="saveGroup"
        :disabled="loading"
        class="w-full py-2 px-4 bg-gray-800 text-white rounded-md hover:bg-gray-700 transition-colors"
      >
        <span v-if="loading">创建中...</span>
        <span v-else>创建群组</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { groupService } from '@/services/group.service'
import { useRouter } from 'vue-router'
import { useListStore } from '@/stores/list'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import FriendSelection from '@/components/independent/group/FriendSelection.vue'

const router = useRouter()
const listStore = useListStore()
const loading = ref(false)

// 表单验证规则
const formSchema = toTypedSchema(
  z.object({
    name: z.string()
      .min(1, "你的群组需要一个的名字")
      .max(20, "群名称最多有20个字符"),
    description: z.string().optional(),
    initial_members: z.array(z.string()).optional()
  })
)

const { handleSubmit, errors, defineField } = useForm({
  validationSchema: formSchema,
  initialValues: {
    name: '',
    description: '',
    initial_members: []
  }
})

const form = ref({
  initial_members: [] as string[]
})

// 绑定表单字段
const [name] = defineField('name')
const [description] = defineField('description')

const saveGroup = handleSubmit(async (values) => {
  try {
    loading.value = true
    const response = await groupService.createGroup({
      name: values.name,
      description: values.description || undefined,
      initial_members: form.value.initial_members
    })

    listStore.groups = [...listStore.groups, response]
    router.push({
      name: 'chat',
      params: {
        type: 'group',
        id: response.group_id
      }
    })
  } catch (error) {
    console.error('创建群组失败:', error)
  } finally {
    loading.value = false
  }
})

const changeAvatar = () => {
  console.log('更换头像')
}

const emit = defineEmits(['close'])

const closeModal = () => {
  emit('close')
}
</script>