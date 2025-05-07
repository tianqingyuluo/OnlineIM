<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useUserStore } from "@/stores/user";
import UserSettings from "./UserSettings.vue";
import { meService } from "@/services/me.service.ts";

// 用户数据逻辑
const userStore = useUserStore();
const isLoading = ref(false);
const error = ref<Error | null>(null);
const showSettings = ref(false);

const userInfo = ref({
  nickname: '未设置',
  email: '未设置',
  phone: '未设置',
  region: '未设置',
  gender: '未设置',
  signature: '未设置',
  avatar_url: '/images/help.png'
});

// 拖动逻辑（新增）
const modalRef = ref<HTMLElement | null>(null);
let isDragging = false;
let initialX = 0;
let initialY = 0;
let currentX = 0;
let currentY = 0;

const startDrag = (e: MouseEvent) => {
  if (!modalRef.value) return;
  isDragging = true;
  initialX = e.clientX;
  initialY = e.clientY;
  document.addEventListener('mousemove', drag);
  document.addEventListener('mouseup', stopDrag);
};

const drag = (e: MouseEvent) => {
  if (!isDragging || !modalRef.value) return;
  e.preventDefault();
  currentX = e.clientX - initialX;
  currentY = e.clientY - initialY;
  initialX = e.clientX;
  initialY = e.clientY;
  const modal = modalRef.value;
  modal.style.top = `${modal.offsetTop + currentY}px`;
  modal.style.left = `${modal.offsetLeft + currentX}px`;
};

const stopDrag = () => {
  isDragging = false;
  document.removeEventListener('mousemove', drag);
  document.removeEventListener('mouseup', stopDrag);
};

// 生命周期
onMounted(async () => {
  try {
    isLoading.value = true;
    const userData = await meService.me();
    userInfo.value = {
      nickname: userData.nickname ||"未设置",
      email: userData.email ||"未设置",
      phone: userData.phone ||"未设置",
      region: userData.region ||"未设置",
      gender: userData.gender ? (userData.gender === 'male' ? '男' : '女') : '未设置',
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

onUnmounted(() => {
  document.removeEventListener('mousemove', drag);
  document.removeEventListener('mouseup', stopDrag);
});

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
        <div
            class="flex items-center h-12 w-full bg-gray-100 rounded-t-lg px-6 cursor-move"
            @mousedown="startDrag"
        >
          <h3 class="text-lg font-medium text-gray-900">用户信息</h3>
        </div>

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