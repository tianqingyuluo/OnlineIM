<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Button } from "@/components/ui/button";
import { userService } from "@/services/user.service.ts";
import DraggableHeader from "@/components/common/DraggableHeader.vue";

const props = defineProps({
  userId: {
    type: String,
    required: true
  }
})

const userInfo = ref({
  nickname: '未设置',
  email: '未设置',
  phone: '未设置',
  region: '未设置',
  gender: '未设置',
  signature: '未设置',
  avatar_url: '/images/help.png'
});

// 好友状态管理
const friendStatus = ref('apply'); // 'apply'|'applied'|'added'
const modalRef = ref<HTMLElement | null>(null)

const handleDrag = ({ deltaX, deltaY }: { deltaX: number; deltaY: number }) => {
  if (!modalRef.value) return
  const rect = modalRef.value.getBoundingClientRect()
  modalRef.value.style.top = `${rect.top + deltaY}px`
  modalRef.value.style.left = `${rect.left + deltaX}px`
}

onMounted(() => {
  if (!modalRef.value) return
  const vw = window.innerWidth
  const vh = window.innerHeight
  modalRef.value.style.top = `${vh * 0.1}px`
  modalRef.value.style.left = `${vw * 0.3}px`
})
// 处理好友申请
function handleFriendApply() {
  friendStatus.value = 'applied';
  // 这里可以添加实际调用API申请好友的逻辑
}

// 生命周期
onMounted(async () => {
  try {
    const userData = await userService.getUserById(props.userId);
    userInfo.value = {
      nickname: userData.nickname || "未设置",
      email: userData.email || "未设置",
      phone: userData.phone || "未设置",
      region: userData.region || "未设置",
      gender: userData.gender ? (userData.gender === 'male' ? '男' : '女') : '未设置',
      signature: userData.signature || "未设置",
      avatar_url: userData.avatar_url || '/images/help.png'
    };
  } catch (error) {
    console.error('加载用户信息失败:', error);
  }
});

</script>

<template>
  <div ref="modalRef" class="fixed z-50 top-[10%] left-[30%] w-[40%]">
    <div class="bg-white rounded-lg shadow-lg w-full max-w-md">
      <DraggableHeader @drag="handleDrag">
        <h3 class="text-lg font-medium text-gray-900">用户信息</h3>
      </DraggableHeader>

      <div class="p-6">
        <div class="flex flex-col items-center mb-6">
          <img
            :src="userInfo.avatar_url"
            class="w-20 h-20 rounded-full object-cover border-2 border-gray-200"
            alt="用户头像"
          >
        </div>

        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700">昵称</label>
            <p class="mt-1 text-gray-900">{{ userInfo.nickname }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">邮箱</label>
            <p class="mt-1 text-gray-900">{{ userInfo.email }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">手机号</label>
            <p class="mt-1 text-gray-900">{{ userInfo.phone }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">地区</label>
            <p class="mt-1 text-gray-900">{{ userInfo.region }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">性别</label>
            <p class="mt-1 text-gray-900">{{ userInfo.gender }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700">个性签名</label>
            <p class="mt-1 text-gray-900">{{ userInfo.signature }}</p>
          </div>
        </div>
      </div>
      <div class="px-6 py-4 flex justify-end space-x-3">
        <Button
            @click="$emit('close')"
            class="px-4 py-2 text-gray-700 bg-gray-200 hover:bg-gray-100 rounded-md transition-colors"
        >
          关闭
        </Button>
        <template v-if="friendStatus === 'apply'">
          <Button
            @click="handleFriendApply"
            class="px-4 py-2 bg-black text-white hover:bg-gray-700 rounded-md transition-colors"
          >
            添加好友
          </Button>
        </template>
        <template v-else-if="friendStatus === 'applied'">
          <Button
            disabled
            class="px-4 py-2 bg-gray-100 text-gray-500 rounded-md cursor-not-allowed"
          >
            已申请
          </Button>
        </template>
        <template v-else-if="friendStatus === 'added'">
          <span class="px-4 py-2 text-gray-500">已是好友</span>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
.cursor-move {
  cursor: move;
  user-select: none;
}
</style>