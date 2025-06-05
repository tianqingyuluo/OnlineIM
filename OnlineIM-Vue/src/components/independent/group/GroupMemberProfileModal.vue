<script setup lang="ts">
import { ref, watch } from 'vue';
import type { GroupMemberAll } from '@/type/group';
import DraggableHeader from '@/components/common/DraggableHeader.vue';

const props = defineProps<{ member: GroupMemberAll | null }>();
const emit = defineEmits(['close']);

const modalRef = ref<HTMLElement | null>(null);

watch(() => props.member, (newMember) => {
  if (newMember) {
  }
});
const handleDrag = ({ deltaX, deltaY }: { deltaX: number; deltaY: number }) => {
  if (!modalRef.value) return

  // 使用 getBoundingClientRect 获取精确位置
  const rect = modalRef.value.getBoundingClientRect()
  modalRef.value.style.top = `${rect.top + deltaY}px`
  modalRef.value.style.left = `${rect.left + deltaX}px`
}


</script>

<template>
  <div v-if="member" class="fixed inset-0 bg-white/80 bg-opacity-50 flex justify-center items-center z-50" @click.self="emit('close')">
    <div ref="modalRef" class="bg-white rounded-lg shadow-xl overflow-hidden w-96" style="position: absolute; top: 10%; left: 33%;" @click.stop>
      <DraggableHeader @drag="handleDrag" >
        <div class="flex justify-between items-center p-4 border-b cursor-move">
          <h2 class="text-lg font-semibold">成员资料</h2>
          <button @click="emit('close')" class="text-gray-500 hover:text-gray-700">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </DraggableHeader>

      <div class="p-6">
        <div class="flex flex-col items-center mb-6">
          <img
              :src="member.user_info.avatar_url || '/images/default-avatar.png'"
              class="w-20 h-20 rounded-full object-cover border-2 border-gray-200"
              alt="成员头像"
          >
        </div>

        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700">用户名</label>
            <p class="mt-1 text-gray-900">{{ member.user_info.username }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">昵称</label>
            <p class="mt-1 text-gray-900">{{ member.user_info.nickname || '未设置' }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">群内昵称</label>
            <p class="mt-1 text-gray-900">{{ member.group_nickname || '未设置' }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">角色</label>
            <p class="mt-1 text-gray-900">{{ member.role }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">加入时间</label>
            <p class="mt-1 text-gray-900">{{ member.joined_at }}</p>
          </div>
          <!-- 可以根据需要添加更多 GroupMemberAll 的字段 -->
        </div>
      </div>

      <!-- 底部按钮 -->
      <div class="px-6 py-4 flex justify-end space-x-3">
        <button
            @click="emit('close')"
            class="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md transition-colors"
        >
          关闭
        </button>
        <!-- 可以根据需要添加其他操作按钮，如发送消息、添加好友等 -->
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Remove manual drag styles if any */
/* .draggable-header { */
/*   cursor: move; */
/* } */
</style>