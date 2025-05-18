<script setup lang="ts">
import {ref,  onMounted,  watch} from 'vue';
import { useUserStore } from "@/stores/user.ts";
import UserSettings from "./UserSettings.vue";
import DraggableHeader from "@/components/common/DraggableHeader.vue";

// 用户数据逻辑
const isLoading = ref(false);
const error = ref<Error | null>(null);
const showSettings = ref(false);
const userStore=useUserStore();
const userInfo = ref({
  nickname: '未设置',
  email: '未设置',
  phone: '未设置',
  region: '未设置',
  gender: '未设置',
  signature: '未设置',
  avatar_url: '/images/help.png'
});
// 监听用户数据变化
const modalRef = ref<HTMLElement | null>(null)

const handleDrag = ({ deltaX, deltaY }: { deltaX: number; deltaY: number }) => {
  if (!modalRef.value) return

  // 使用 getBoundingClientRect 获取精确位置
  const rect = modalRef.value.getBoundingClientRect()
  modalRef.value.style.top = `${rect.top + deltaY}px`
  modalRef.value.style.left = `${rect.left + deltaX}px`
}

// 初始化时转换百分比为像素
onMounted(() => {
  if (!modalRef.value) return
  const vw = window.innerWidth
  const vh = window.innerHeight
  modalRef.value.style.top = `${vh * 0.1}px`
  modalRef.value.style.left = `${vw * 0.35}px`
})

// 生命周期
onMounted(async () => {
  try {
    isLoading.value = true;
    const userData = userStore.loggedInUser
    userInfo.value = {
      nickname: userData.nickname ||"未设置",
      email: userData.email ||"未设置",
      phone: userData.phone ||"未设置",
      region: userData.region ||"未设置",
      gender: userData.gender!==0 ? (userData.gender === 1 ? '男' : '女') : '未设置',
      signature: userData.signature ||"未设置",
      avatar_url: userData.avatar_url
    };
  } catch (err) {
    error.value = err as Error;
    console.error('获取用户信息失败:', err);
  } finally {
    isLoading.value = false;
  }
});


watch(
    () => userStore.loggedInUser,
    (newUser) => {
      if (newUser) {
        userInfo.value = {
          nickname: newUser.nickname || "未设置",
          email: newUser.email || "未设置",
          phone: newUser.phone || "未设置",
          region: newUser.region || "未设置",
          gender: newUser.gender!==0 ? (newUser.gender === 1 ? '男' : '女') : '未设置',
          signature: newUser.signature || "未设置",
          avatar_url: newUser.avatar_url || '/images/help.png'
        };
      }
    },
    { immediate: true, deep: true }
);
// 跳转编辑
const editProfile = () => {
  showSettings.value = true;
};
</script>

<template>
  <!-- 外层容器添加拖动功能 -->
  <div ref="modalRef" class="fixed z-50 top-[10%] left-[30%] w-[40%]">
    <transition name="fade-slide" mode="out-in">
      <div v-if="!showSettings" class="bg-white rounded-lg shadow-lg w-full max-w-md">
        <!-- 新增统一顶栏 -->
        <DraggableHeader @drag="handleDrag">
          <h3 class="text-lg font-medium text-gray-900">用户信息</h3>
        </DraggableHeader>

        <!-- 内容区域 -->
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

        <!-- 底部按钮 -->
        <div class="px-6 py-4  flex justify-end space-x-3">
          <button
              @click="$emit('close')"
              class="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md transition-colors"
          >
            关闭
          </button>
          <button
              @click="editProfile"
              class="px-4 py-2 bg-black text-white rounded-md hover:bg-gray-700 transition-colors"
          >
            编辑信息
          </button>
        </div>
      </div>
    </transition>

    <!-- 设置组件 -->
    <transition name="fade-slide" mode="out-in">
      <UserSettings v-if="showSettings" @close="showSettings = false" />
    </transition>
  </div>
</template>

<style scoped>
/* 统一过渡效果 */
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

/* 拖动光标反馈 */
.cursor-move {
  cursor: move;
  user-select: none;
}
</style>