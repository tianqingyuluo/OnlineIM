<script setup lang="ts">
import { ref, watch } from 'vue';
import { Switch } from "@/components/ui/switch";
import { useListStore } from '@/stores/list';
import type { Conversation } from '@/type/Conversation';
import { conversationService } from '@/services/conversation.service';
import { blacklistService } from '@/services/blacklist.service';
import { friendsService } from '@/services/friends.service';

// 修改 props，同时接收 conversation 和 userId
const props = defineProps<{
  conversation: Conversation;
  userId: string | number;
}>();

const listStore = useListStore();

const settings = ref({
  // 根据 conversation prop 初始化 isPinned 和 isMuted
  isPinned: props.conversation?.is_pinned || false,
  isMuted: props.conversation?.is_muted || false,
  // 根据 userId 和黑名单初始化 isBlocked
  isBlocked: listStore.blacklist.some(user => user.user_id === props.userId)
});

// Watch for changes in conversation or userId props to update settings
watch(() => [props.conversation, props.userId], ([newConversation, newUserId]) => {
  if (newConversation) {
    settings.value.isPinned = newConversation.is_pinned || false;
    settings.value.isMuted = newConversation.is_muted || false;
  }
  if (newUserId) {
    settings.value.isBlocked = listStore.blacklist.some(user => user.user_id === newUserId);
  }
}, { immediate: true });

// 处理置顶状态变化
const handlePinnedChange = async (isPinned: boolean) => {
  try {
    if (isPinned) {
      await conversationService.topConversation([props.conversation.conversation_id]);
    } else {
      await conversationService.unTopConversation(props.conversation.conversation_id);
    }
    // 更新本地状态（如果服务调用成功）
    settings.value.isPinned = isPinned;
  } catch (error) {
    console.error('更新置顶状态失败:', error);
    // 可以选择回滚 Switch 状态或显示错误提示
  }
};

// 处理免打扰状态变化
const handleMutedChange = async (isMuted: boolean) => {
  try {
    if (isMuted) {
      await conversationService.muteConversation(props.conversation.conversation_id);
    } else {
      await conversationService.unmuteConversation(props.conversation.conversation_id);
    }
    // 更新本地状态
    settings.value.isMuted = isMuted;
  } catch (error) {
    console.error('更新免打扰状态失败:', error);
  }
};

// 处理屏蔽状态变化
const handleBlockedChange = async (isBlocked: boolean) => {
  try {
    if (isBlocked) {
      // 确保 userId 是字符串类型，因为 addToBlacklist 接收 string
      await blacklistService.addToBlacklist(String(props.userId));
      await friendsService.deleteFriend(String(props.userId));
      // 同时从 listStore 的 friends 和 userGroups 中移除该好友
      listStore.friends = listStore.friends.filter(friend => friend.friend_info.user_id !== props.userId);
      listStore.userGroups.forEach(group => {
        group.friends = group.friends.filter(friend => friend.user_id !== props.userId);
      });
      settings.value.isBlocked = isBlocked;
      const blacklistResponse = await blacklistService.getBlacklist();
      listStore.blacklist = blacklistResponse.blacklist;

    } else {

      await blacklistService.removeFromBlacklist(String(props.userId));
      // 更新本地状态
      settings.value.isBlocked = isBlocked;

      listStore.blacklist = listStore.blacklist.filter(user => user.user_id !== props.userId);

    }

  } catch (error) {
    console.error('更新屏蔽状态失败:', error);
  }
};

</script>

<template>
  <div class="p-4 space-y-4 ">
    <div class="flex items-center justify-between py-2 border-b border-gray-100">
      <span class="text-gray-500">设为置顶</span>
      <Switch v-model="settings.isPinned" @update:modelValue="handlePinnedChange"/>
    </div>
    <div class="flex items-center justify-between py-2 border-b border-gray-100">
      <span class="text-gray-500">消息免打扰</span>
      <Switch v-model="settings.isMuted" @update:modelValue="handleMutedChange"/>
    </div>
    <div class="flex items-center justify-between py-2 border-b border-gray-100">
      <span class="text-gray-500">屏蔽此人</span>
      <Switch v-model="settings.isBlocked" @update:modelValue="handleBlockedChange"/>
    </div>
    <button
      class="w-full py-2 mt-4 text-red-500 bg-white border border-red-500 rounded-md hover:bg-red-50"
    >
      删除好友
    </button>
  </div>
</template>

<style scoped>
/* 可根据需要添加样式 */
</style>