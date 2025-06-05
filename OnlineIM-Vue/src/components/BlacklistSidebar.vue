<!-- src/components/BlacklistSidebar.vue -->
<script setup lang="ts">
import { ref} from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";
import { useRouter } from 'vue-router';
import {useListStore} from "@/stores/list.ts";

const router = useRouter();
const blacklist = useListStore().blacklist
const isExpanded = ref(false);
const activeItem = ref<string | null>(null);


const handleUserClick = (userId: string) => {
  activeItem.value = userId;
  router.push(`/friends/${userId}`);
};
</script>

<template>
  <div class="blacklist-container">
    <div
        @click="isExpanded = !isExpanded"
        class="cursor-pointer flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors font-bold "
    >
      <span>黑名单 ({{ blacklist.length }})</span>
      <ChevronDown
          v-if="isExpanded"
          class="w-5 h-5 transition-transform duration-200"
      />
      <ChevronRight
          v-else
          class="w-5 h-5 transition-transform duration-200"
      />
    </div>

    <transition
        name="slide"
        @enter="el => el.style.height = el.scrollHeight + 'px'"
        @after-enter="el => el.style.height = null"
        @before-leave="el => el.style.height = el.scrollHeight + 'px'"
        @leave="el => el.style.height = 0"
    >
      <div v-show="isExpanded" class="transition-all duration-300">
        <div
            v-for="user in blacklist"
            :key="user.user_id"
            @click="handleUserClick(user.user_id)"
            class="flex items-center w-full h-[80px] px-4 cursor-pointer hover:bg-gray-50 transition-colors user-item"
            :class="{ 'bg-gray-100': activeItem === user.user_id }"
        >
          <div class="w-[50px] h-[50px] rounded-full overflow-hidden mr-4 flex-shrink-0">
            <img
                :src="user.avatar_url || '/default-avatar.png'"
                :alt="user.nickname"
                class="w-full h-full object-cover"
            />
          </div>
          <div class="flex flex-col flex-grow space-y-1">
            <span class="text-[18px] font-bold">{{ user.nickname }}</span>
            <span class="text-[13px] text-gray-500">已拉黑</span>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>

.user-item {
  border-radius: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: height 0.3s ease-in-out;
  overflow: hidden;
}
</style>