<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { userService } from '@/services/user.service'
import { useListStore } from '@/stores/list'
import type { User } from '@/type/User'
import type { Friend } from '@/type/Friends'
import { Input } from "@/components/ui/input";
import { friendsService } from '@/services/friends.service'
import { Button } from "@/components/ui/button";

const route = useRoute()
const listStore = useListStore()
const user = ref<User | null>(null)
const friendInfo = ref<Friend | null>(null)

const userId = computed(() => {
  const id = route.params.userId
  return Array.isArray(id) ? id[0] : id
})

onMounted(async () => {
  try {
    // 获取用户基本信息
    user.value = await userService.getUserById(userId.value)
    
    // 从好友列表中查找备注和分组信息
    const friend = listStore.friends.find(
      f => f.friend_info.user_id === userId.value
    )
    if (friend) {
      friendInfo.value = {
        ...friend,
        friend_info: {
          ...friend.friend_info,
          friend_group_id: friend.friend_info.friend_group_id || undefined,
          remark: friend.friend_info.remark || undefined
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
      <div class="info-item mb-6">
        <span class="label">备注名:</span>
        <template v-if="editingRemark">
          <Input 
            v-model="remarkTemp" 
            @blur="saveRemark"
            @keyup.enter="saveRemark"
            autofocus
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
      <div class="info-item mb-6">
        <span class="label">分组:</span>
        <span>{{ friendInfo.friend_info.friend_group_id ||"我的好友"}}</span>
      </div>
    </div>
    <Button class="flex flex-col items-center justify-center w-full p-4 mt-8 hover:scale-105 transition-transform duration-200">发消息</Button>
  </div>
  <div v-else class="loading">
    加载中...
  </div>
</template>

<style scoped>
.user-profile {
  padding: 20px;
  width: 400px;
  height: 600px;
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
