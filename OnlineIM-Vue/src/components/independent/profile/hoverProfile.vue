<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import SendFriendRequest from "@/components/independent/friends/SendFriendRequest.vue";

import { userService } from '@/services/user.service'
import { useListStore } from '@/stores/list'
import type { User } from '@/type/User'
import type { Friend } from '@/type/Friends'
import { Input } from "@/components/ui/input";
import { friendsService } from '@/services/friends.service'
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const listStore = useListStore()
const user = ref<User | null>(null)
const friendInfo = ref<Friend | null>(null)


// 定义 userId prop
const props = defineProps({
  userId: {
    type: String,
    required: true
  }
})

onMounted(async () => {
  try {
    // 获取用户基本信息，使用 prop 传入的 userId
    user.value = await userService.getUserById(props.userId)

    // 从好友列表中查找备注和分组信息
    let foundFriend: Friend | null = null

    // 遍历所有分组中的好友
    for (const group of listStore.userGroups) {
      const friend = group.friends.find(
          f => f.user_id === props.userId
      )
      if (friend) {
        foundFriend = {
          friendship_id: friend.friendship_id,
          friend_info: {
            user_id: friend.user_id,
            username: friend.username,
            nickname: friend.nickname,
            avatar_url: friend.avatar_url,
            remark: friend.remark,
            friend_group_id: friend.friend_group_id,
            online_status: friend.online_status
          },
          created_at: friend.created_at
        }
        break
      }
    }

    if (foundFriend) {
      friendInfo.value = {
        ...foundFriend,
        friend_info: {
          ...foundFriend.friend_info,
          friend_group_id: foundFriend.friend_info.friend_group_id || undefined,
          remark: foundFriend.friend_info.remark || undefined
        }
      }
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
})

const editingRemark = ref(false);
const remarkTemp = ref('');

function startEditingRemark() {
  remarkTemp.value = friendInfo.value?.friend_info.remark || '';
  editingRemark.value = true;
  nextTick(() => {
    const input = document.querySelector('input[autofocus]') as HTMLInputElement;
    input?.focus();
  });
}

async function saveRemark() {
  if (friendInfo.value) {
    try {
      await friendsService.setFriendRemark(
          friendInfo.value.friendship_id,
          remarkTemp.value
      );
      friendInfo.value.friend_info.remark = remarkTemp.value;
    } catch (error) {
      console.error('设置备注失败:', error);
    }
  }
  editingRemark.value = false;
}

async function handleGroupChange(newGroupId: string) {
  if (friendInfo.value) {
    try {
      const response = await friendsService.setFriendGroup(
          friendInfo.value.friendship_id,
          newGroupId
      );
      friendInfo.value.friend_info.friend_group_id = response.friendship_group_id;

      // 调用store的updateFriendGroup方法同步更新friends和groupedFriends数组
      listStore.updateFriendGroup(friendInfo.value.friendship_id, response.friendship_group_id);
    } catch (error) {
      console.error('修改分组失败:', error);
    }
  }
}

const isFriend = computed(() => {
  return listStore.friends.some(friend => friend.friend_info.user_id === props.userId);
});

const showSendFriendRequest = ref(false);
const selectedUserForFriendRequest = ref<User | null>(null);

function openSendFriendRequest() {

  showSendFriendRequest.value = true;
}

function closeSendFriendRequest() {
  showSendFriendRequest.value = false;

}
</script>

<template>
  <div v-if="user" class="user-profile">
    <!-- 用户基本信息 -->
    <div class="user-header">
      <img
          :src="user.avatar_url || '/images/default-avatar.png'"
          class="avatar"
          :alt="user.nickname"
      >
      <h2 class="nickname">{{ user.nickname || user.username || '未设置昵称' }}</h2>
      <p>{{ user.signature || '未设置个性签名' }}</p>
    </div>

    <!-- 好友专属信息 -->
    <div v-if="friendInfo" class="friend-info mt-8">
      <div class="info-item mb-6 flex items-center justify-between">
        <span class="label">备注名:</span>
        <template v-if="editingRemark">
          <Input
              v-model="remarkTemp"
              @blur="saveRemark"
              @keyup.enter="saveRemark"
              autofocus
              class="w-[180px]"
          />
        </template>
        <span
            v-else
            @click="startEditingRemark"
            class="editable-text"
        >
          {{ friendInfo.friend_info.remark || '未设置备注名' }}
        </span>
      </div>
      <div class="info-item mb-6 flex items-center justify-between">
        <span class="label">分组:</span>
        <Select
            v-model="friendInfo.friend_info.friend_group_id"
            @update:modelValue="handleGroupChange"
            class="w-[180px]"
        >
          <SelectTrigger>
            <SelectValue placeholder="选择分组" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem
                  v-for="group in listStore.userGroups"
                  :key="group.group_id"
                  :value="group.group_id"
              >
                {{ group.name }}
              </SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>
    </div>
    <!-- 根据是否是好友显示不同按钮 -->
    <Button v-if="isFriend" class="flex flex-col items-center justify-center w-full p-4 mt-8 hover:scale-105 transition-transform duration-200">发消息</Button>
    <Button v-else @click="openSendFriendRequest" class="flex flex-col items-center justify-center w-full p-4 mt-8 hover:scale-105 transition-transform duration-200">加好友</Button>
  </div>
  <div v-else class="loading">
    加载中...
  </div>

  <SendFriendRequest
      v-if="showSendFriendRequest && selectedUserForFriendRequest"
      @close="closeSendFriendRequest"
      :user="user"
      class="fixed inset-0 m-auto w-1/2 h-1/2 z-[9999]"
  />
</template>

<style scoped>
.user-profile {
  padding: 20px;
  min-width: 400px;
  min-height: 350px;
  overflow: auto;
}
.user-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;
}
.avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;  /* 这行代码确保头像显示为圆形 */
  margin-bottom: 10px;
  overflow:hidden;
}
.info-item {
  margin: 16px 0;
}
.label {
  font-weight: bold;
  margin-right: 10px;
}
.editable-text {
  cursor: pointer;
  padding: 2px 5px;
  border-radius: 4px;
}
.editable-text:hover {
  background-color: #f0f0f0;
}
.nickname {
  font-weight: bold;
}
</style>
