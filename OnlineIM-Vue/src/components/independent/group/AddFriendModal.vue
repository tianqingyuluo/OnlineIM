<script setup lang="ts">
import { Button } from "@/components/ui/button";
import FriendSelection from '@/components/independent/group/FriendSelection.vue';
import { ref } from "vue";
import { groupService } from "@/services/group.service";
import { toast } from "vue-sonner";
const emit = defineEmits(['close']);
const selectedFriends = ref<string[]>([]);
const props = defineProps<{
  groupId: string
}>();

const handleInvite = async () => {
  if (selectedFriends.value.length === 0) {
    toast.error('您没有选择想要拉进群的好友');
    return;
  }
  
  try {
    console.log('邀请好友:', selectedFriends.value);
    await groupService.inviteUsersToGroup(props.groupId, selectedFriends.value);
    handleClose();
  } catch (error) {
    console.error('邀请好友失败:', error);
  }
};

const handleClose = () => {
  emit('close');
};
</script>

<template>
  <div class="fixed z-50  w-1/2 h-[600px] left-[25%] top-[10%]">
    <div class="bg-white shadow-lg w-full h-full rounded-lg">
      <!-- 顶部栏 -->
      <div class="flex items-center justify-between h-12 w-full bg-gray-100 rounded-t-lg px-6 pr-0">
        <h3 class="text-lg font-medium text-gray-900">添加好友</h3>
        <div class="mr-0">
          <Button
            @click="handleClose"
            class="text-gray-500 bg-gray-100 text-3xl hover:text-gray-700 hover:bg-white border-none shadow-none"
          >
            ×
          </Button>
        </div>
      </div>

      <div class="flex flex-col h-full">

        <div class="p-4 overflow-auto flex-1">

          <FriendSelection v-model="selectedFriends" class="h-full"/>

        </div>

        <button 
  class="w-full bg-black text-white py-3 rounded-b-lg"
  @click="handleInvite"
  :disabled="selectedFriends.length === 0"
>
  确定
</button>

      </div>
      
    </div>
  </div>
</template>

<style scoped>
/* 保持与现有UI风格一致 */
</style>