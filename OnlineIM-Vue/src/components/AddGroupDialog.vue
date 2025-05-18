<script setup lang="ts">
import { ref } from 'vue'
import { friendGroupsService } from "@/services/friendGroups.servise.ts";
import { useListStore } from "@/stores/list.ts";

const props = defineProps({
  show: {
    type: Boolean,
    required: true
  }
})

const listStore = useListStore()

const emit = defineEmits(['update:show', 'confirm'])

const groupName = ref('')

async function handleConfirm() {
  if (groupName.value.trim()) {
    try {
      const newGroup = await friendGroupsService.createFriendGroup(groupName.value);
      listStore.userGroups.push(newGroup);
      listStore.groupedFriends.push({
        group: newGroup,
        friends: []
      });
      groupName.value = ''
      emit('update:show', false)
    } catch (error) {
      console.error('创建分组失败:', error);
    }
  }
}

function handleCancel() {
  groupName.value = ''
  emit('update:show', false)
}
</script>

<template>
  <div v-if="show" class="fixed inset-0 bg-black/80 bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-6 rounded-lg shadow-sm max-w-sm w-full mx-auto border-[2px] shadow-2xl">
      <h3 class="text-lg font-medium text-gray-800 mb-4">添加分组</h3>
      <div class="mb-4">
        <input
          v-model="groupName"
          type="text"
          placeholder="请输入分组名称"
          class="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-1 focus:ring-gray-500"
        />
      </div>
      <div class="flex space-x-3">
        <button
          @click="handleCancel"
          class="flex-1 py-2 px-4 bg-gray-100 text-gray-800 rounded-md hover:bg-gray-200 transition-colors"
        >
          取消
        </button>
        <button
          @click="handleConfirm"
          class="flex-1 py-2 px-4 bg-gray-800 text-white rounded-md hover:bg-gray-700 transition-colors"
        >
          确定
        </button>
      </div>
    </div>
  </div>
</template>