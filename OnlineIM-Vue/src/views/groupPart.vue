<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { groupService } from '@/services/group.service'
import type { GroupResponse } from '@/type/group'

const route = useRoute()
const group = ref<GroupResponse | null>(null)

const groupId = computed(() => {
  const id = route.params.id
  return Array.isArray(id) ? id[0] : id
})

onMounted(async () => {
  try {
    // 获取群组基本信息
    group.value = await groupService.getGroupInfo(groupId.value)
  } catch (error) {
    console.error('获取群组信息失败:', error)
  }
})
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
  </div>
  <div v-else class="loading">
    加载中...
  </div>
</template>

<style scoped>
.group-profile {
  padding: 20px;
}
.group-header {
  text-align: center;
  margin-bottom: 20px;
}
.avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  object-fit: cover;
}
.info-item {
  margin: 10px 0;
}
.label {
  font-weight: bold;
  margin-right: 10px;
}
</style>