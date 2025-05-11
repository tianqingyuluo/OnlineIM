<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DraggableHeader from '@/components/common/DraggableHeader.vue'
import { Button } from "@/components/ui/button"
import { groupService } from '@/services/group.service.ts'
import { userService } from "@/services/user.service.ts";

const emit = defineEmits(['close'])
const modalRef = ref<HTMLElement | null>(null)
const props = defineProps({
  groupId: {
    type: String,
    required: true
  }
})

// 群组信息数据结构
const groupInfo = ref({
  group_id: '',
  name: '加载中...',
  owner_id: '',
  avatar_url: '/images/group.png',
  description: '',
  announcement: '',
  created_at: '',
  member_count: 0,
  my_role: 'member',
  members: [] as Array<{
    user_id: string
    username: string
    avatar_url?: string
    role: 'owner' | 'admin' | 'member'
    join_time: string
  }>,
  settings: {
    join_approval_required: false,
    invite_only: false,
    message_history_visible: true
  }
})

let ownerName = ref('')

// 拖动处理逻辑（改为您指定的方式）
const handleDrag = ({ deltaX, deltaY }: { deltaX: number; deltaY: number }) => {
  if (!modalRef.value) return
  const currentTop = parseInt(modalRef.value.style.top || '10%', 10)
  const currentLeft = parseInt(modalRef.value.style.left || '30%', 10)
  modalRef.value.style.top = `${currentTop + deltaY}px`
  modalRef.value.style.left = `${currentLeft + deltaX}px`
}

// 初始化定位（改为您指定的方式）
onMounted(() => {
  if (!modalRef.value) return
  const vw = window.innerWidth
  const vh = window.innerHeight
  modalRef.value.style.top = `${vh * 0.1}px`
  modalRef.value.style.left = `${vw * 0.3}px`
})

// 加载群组信息
onMounted(async () => {
  try {
    const data = await groupService.getGroupInfo(props.groupId)
    groupInfo.value = data
    const response = await userService.getUserById(data.owner_id)
    ownerName.value = response.username
  } catch (error) {
    console.error('加载群组信息失败:', error)
  }
})

// 关闭弹窗
function handleClose() {
  emit('close')
}

// 显示成员头像（最多5个）
function getDisplayMembers() {
  return groupInfo.value.members?.slice(0, 5) || []
}
</script>

<template>
  <div ref="modalRef" class="fixed z-50 top-[10%] left-[30%] w-[500px]">
    <div class="bg-white rounded-lg shadow-lg max-h-[80vh] overflow-y-auto">
      <DraggableHeader @drag="handleDrag">
        <div class="p-4 border-b">
          <h3 class="text-lg font-semibold">群组信息</h3>
        </div>
      </DraggableHeader>

      <!-- 内容区 -->
      <div class="p-4">
        <!-- 群组基本信息 -->
        <div class="flex items-start mb-6">
          <img
              :src="groupInfo.avatar_url"
              :alt="groupInfo.name"
              class="w-20 h-20 rounded-full mr-4"
          >
          <div>
            <h2 class="text-xl font-bold">{{ groupInfo.name }}</h2>
            <p class="text-gray-500 text-sm mt-1">
              创建时间: {{ new Date(groupInfo.created_at).toLocaleString() }}
            </p>
            <p class="text-gray-500 text-sm">群主: {{ ownerName }}</p>
          </div>
        </div>

        <!-- 群描述 -->
        <div class="mb-6">
          <h4 class="font-medium mb-2">群描述</h4>
          <div class="bg-gray-100 p-3 rounded">
            {{ groupInfo.description || '暂无描述' }}
          </div>
        </div>

        <!-- 群成员 -->
        <div>
          <h4 class="font-medium mb-2">群成员 ({{ groupInfo.member_count }})</h4>
          <div class="flex flex-wrap gap-2">
            <template v-for="member in getDisplayMembers()" :key="member.user_id">
              <div class="flex flex-col items-center w-16">
                <img
                    :src="member.avatar_url || '/images/help.png'"
                    :alt="member.username"
                    class="w-10 h-10 rounded-full mb-1"
                >
                <span class="text-xs truncate w-full text-center">{{ member.username }}</span>
              </div>
            </template>
            <div
                v-if="groupInfo.member_count > 5"
                class="flex flex-col items-center w-16"
            >
              <div class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center mb-1">
                +{{ groupInfo.member_count - 5 }}
              </div>
              <span class="text-xs">更多</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部按钮 -->
      <div class="p-4 border-t flex justify-end gap-2">
        <Button variant="outline" @click="handleClose">关闭</Button>
        <Button v-if="groupInfo.my_role === 'owner'">管理群组</Button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 移除不必要的过渡效果 */
</style>