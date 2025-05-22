<script setup lang="ts">
import {ref, onMounted, computed} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { groupService } from '@/services/group.service'
import type { GroupResponse } from '@/type/group'
import {Button} from "@/components/ui/button";

const route = useRoute()
const group = ref<GroupResponse | null>(null)

// 使用与路由定义一致的参数名 (groupId)
const groupId = computed(() => {
  const id = route.params.groupId // 注意这里改为 groupId 与路由定义一致
  return Array.isArray(id) ? id[0] : id
})

// 获取群组信息的函数
const fetchGroupInfo = async () => {
  try {
    group.value = await groupService.getGroupInfo(groupId.value)
    if (group.value.my_role==='0') {group.value.my_role = "成员"}
    if (group.value.my_role==='1') {group.value.my_role = "管理员"}
    if (group.value.my_role==='2') {group.value.my_role = "群主"}
  } catch (error) {
    console.error('获取群组信息失败:', error)
  }
}

// 初始加载
onMounted(fetchGroupInfo)

const router = useRouter()
</script>

<template>
  <div v-if="group" class="group-profile">
    <!-- 群组基本信息 -->
    <div class="group-header">
      <img 
        :src="group.avatar_url || '/images/group.png'" 
        class="avatar"
        :alt="group.name"
      >
      <h2>{{ group.name }}</h2>
      <p v-if="group.description">{{ group.description }}</p>
    </div>

    <!-- 群组详情信息 -->
    <div class="group-details">
      <div class="info-item">
        <span class="label">群主ID:</span>
        <span>{{ group.owner_id }}</span>
      </div>
      <div v-if="group.announcement" class="info-item">
        <span class="label">群公告:</span>
        <span>{{ group.announcement }}</span>
      </div>
      <div class="info-item">
        <span class="label">成员数量:</span>
        <span>{{ group.member_count }}</span>
      </div>
      <div class="info-item">
        <span class="label">我的角色:</span>
        <span>{{ group.my_role }}</span>
      </div>
      <div class="info-item">
        <span class="label">创建时间:</span>
        <span>{{ group.created_at }}</span>
      </div>
    </div>
    <Button 
  class="flex flex-col items-center justify-center w-full p-4 mt-8 hover:scale-105 transition-transform duration-200"
  @click="router.push(`/main/chat/group/${groupId}`)"
>
  发消息
</Button>
  </div>
  <div v-else class="loading">
    加载中...
  </div>
</template>

<style scoped>
.group-profile {
  padding: 20px;
  max-width: 400px;
  max-height: 600px;
  overflow: auto;
}
.group-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;
}
.avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  object-fit: cover;
  margin-bottom: 10px;
}
.info-item {
  margin: 16px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.label {
  font-weight: bold;
  margin-right: 10px;
}
</style>