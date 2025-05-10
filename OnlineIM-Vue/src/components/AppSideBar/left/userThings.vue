<script setup lang="ts">
import { useUserStore } from '@/stores/user.ts'
import { useRouter } from 'vue-router'
import UserProfile from "@/components/independent/profile/UserProfile.vue";
import {ref} from "vue";

const userStore = useUserStore()
const router = useRouter()
const showProfile = ref(false)

const handleLogout = () => {
  userStore.clearUser()
  router.push('/login')
}

const handleProfile = () => {
  showProfile.value = true
}
</script>

<template>
  <div class="p-2">
    <!-- 退出按钮 -->
    <div class="py-2">
      <button 
        class="w-full text-left px-3 py-2 cursor-pointer hover:bg-gray-100"
        @click="handleLogout"
      >
        退出
      </button>
    </div>
    
    <!-- 信息按钮 -->
    <div class="py-2">
      <button 
        class="w-full text-left px-3 py-2 cursor-pointer hover:bg-gray-100"
        @click="handleProfile"
      >
        信息
      </button>
    </div>

    <div v-if="showProfile" class="fixed inset-0 bg-white/80 flex items-center justify-center z-[9999]">
      <UserProfile @close="showProfile = false" />
    </div>
  </div>
</template>