<template>
  <div class="flex bg-white p-6 rounded-lg shadow-sm max-w-4xl mx-auto">
    <!-- 左侧好友头像侧边栏 -->
    <div class="w-2/5 pr-4 border-r border-gray-200"> 
       <h3 class="text-sm font-medium text-gray-700 mb-3">选择好友</h3> 
       <div class="max-h-96 overflow-y-auto space-y-2"> 
         <template v-if="groupedFriends.length === 0"> 
           <div class="flex justify-center items-center py-8 text-gray-500"> 
             没有好友 
           </div> 
         </template> 
         <template v-else v-for="group in groupedFriends" :key="group.group.id"> 
           <div class="group-container"> 
             <SidebarGroupLabel 
               @click="toggleGroup(group.group.id)" 
               class="cursor-pointer flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors font-bold" 
               style="font-size: 15px" 
             > 
               {{ group.group.name }} ({{ group.friends.length }}) 
               <ChevronDown 
                 v-if="isGroupExpanded(group.group.id)" 
                 class="w-5 h-5 transition-transform duration-200" 
               /> 
               <ChevronRight 
                 v-else 
                 class="w-5 h-5 transition-transform duration-200" 
               /> 
             </SidebarGroupLabel> 
             
             <transition 
               name="slide" 
               @enter="el => el.style.height = el.scrollHeight + 'px'" 
               @after-enter="el => el.style.height = null" 
               @before-leave="el => el.style.height = el.scrollHeight + 'px'" 
               @leave="el => el.style.height = 0" 
             > 
               <div v-show="isGroupExpanded(group.group.id)" class="transition-all duration-300"> 
                 <div 
                   v-for="friend in group.friends" 
                   :key="friend.friendship_id" 
                   class="flex items-center p-2 hover:bg-gray-50 rounded cursor-pointer" 
                   @click="toggleFriendSelection(friend.friend_info.user_id)" 
                 > 
                   <img 
                     :src="friend.friend_info.avatar_url || '/images/default-avatar.png'" 
                     class="w-10 h-10 rounded-full mr-2" 
                     :alt="friend.friend_info.nickname" 
                   /> 
                   <span class="text-sm text-gray-800 mr-2"> 
                     {{ friend.friend_info.nickname || friend.friend_info.username }} 
                   </span> 
                   <input 
                     type="checkbox" 
                     v-model="form.initial_members" 
                     :value="friend.friend_info.user_id" 
                     class="h-4 w-4 rounded border-gray-300 text-gray-600 focus:ring-gray-500" 
                   /> 
                 </div> 
               </div> 
             </transition> 
           </div> 
         </template> 
       </div> 
     </div>

    <!-- 右侧创建群组表单 -->
    <div class="w-3/5 pl-6">
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
import {ref, computed, onMounted} from 'vue'
import { groupService } from '@/services/group.service'
import { useRouter } from 'vue-router'
import { useListStore } from '@/stores/list'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import { SidebarGroupLabel } from '@/components/ui/sidebar'

const router = useRouter()
const listStore = useListStore()
const loading = ref(false)
const expandedGroups = ref<Record<string, boolean>>({})

// 初始化所有分组为展开状态
onMounted(() => {
  listStore.userGroups.forEach(group => {
    expandedGroups.value[group.id] = true
  })
})

function toggleGroup(groupId: string) {
  expandedGroups.value[groupId] = !expandedGroups.value[groupId]
}

function isGroupExpanded(groupId: string) {
  return expandedGroups.value[groupId] ?? true
}

const groupedFriends = computed(() => {
  return listStore.groupedFriends
})

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

const form = ref({
  initial_members: [] as string[]
})

// 使用defineField方式绑定表单字段
const { handleSubmit, errors, defineField } = useForm({
  validationSchema: formSchema,
  initialValues: {
    name: '',
    description: '',
    initial_members: []
  }
})

// 为每个字段添加绑定
const [name] = defineField('name')
const [description] = defineField('description')

// 提交处理
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

const toggleFriendSelection = (userId: string) => {
  const index = form.value.initial_members.indexOf(userId)
  if (index === -1) {
    form.value.initial_members.push(userId)
  } else {
    form.value.initial_members.splice(index, 1)
  }
}
</script>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: height 0.3s ease-in-out;
  overflow: hidden;
}

.group-container {
  transition: all 0.3s ease;
}

.chevron-rotate-enter-active,
.chevron-rotate-leave-active {
  transition: transform 0.3s ease;
}

.chevron-rotate-enter-from,
.chevron-rotate-leave-to {
  transform: rotate(-90deg);
}
</style>