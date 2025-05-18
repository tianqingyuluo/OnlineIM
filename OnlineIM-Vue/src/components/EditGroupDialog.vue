<script setup lang="ts">
import { ref } from "vue";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { friendGroupsService } from "@/services/friendGroups.servise.ts";
import { toast } from "vue-sonner";
import { useListStore } from "@/stores/list.ts";

const props = defineProps<{
  show: boolean;
  groupId: string;
}>();

const emits = defineEmits(['close']);

const listStore = useListStore();
const name = ref(listStore.userGroups.find(g => g.id === props.groupId)?.name || '');
const isLoading = ref(false);

async function handleUpdate() {
  if (!name.value.trim()) {
    toast.error("分组名称不能为空");
    return;
  }
  
  try {
    isLoading.value = true;
    await friendGroupsService.updateFriendGroup(props.groupId, name.value);
    const index = listStore.userGroups.findIndex(g => g.id === props.groupId);
    if (index !== -1) {
      listStore.userGroups[index].name = name.value;
      const groupIndex = listStore.groupedFriends.findIndex(g => g.group.id === props.groupId);
      if (groupIndex !== -1) {
        listStore.groupedFriends[groupIndex].group.name = name.value;
      }
    }
    emits('close');
    toast.success("分组名称更新成功");
  } catch (error) {
    console.error("更新分组失败:", error);
  } finally {
    isLoading.value = false;
  }
}

function handleCancel() {
  name.value = ''
  emits('close')
}
</script>

<template>
  <!-- 关键修复：添加 v-if="show" -->
  <div v-if="show" class="fixed inset-0 bg-black/80 bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-6 rounded-lg shadow-sm max-w-sm w-full mx-auto border-[2px] shadow-2xl">
      <h2 class="text-xl font-bold mb-4">修改分组名称</h2>
      <div class="space-y-4">
        <Input 
          v-model="name" 
          placeholder="请输入分组名称"
          class="w-full"
        />
        <div class="flex justify-end space-x-2">
          <div class="flex space-x-3">
        <button
          @click="handleCancel"
          class="flex-1 py-2 px-4 bg-gray-100 text-gray-800 rounded-md hover:bg-gray-200 transition-colors"
        >
          取消
        </button>
        <button
          :disabled="isLoading"
          @click="handleUpdate"
          class="flex-1 py-2 px-4 bg-gray-800 text-white rounded-md hover:bg-gray-700 transition-colors"
        >
          确定
        </button>
      </div>
        </div>
      </div>
    </div>
  </div>
</template>