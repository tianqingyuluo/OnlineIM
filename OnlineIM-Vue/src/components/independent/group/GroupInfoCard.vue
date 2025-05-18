<script setup lang="ts">
import type { GroupResponse } from '@/type/group.ts'
import { ref } from 'vue';
import GroupMembersList from '@/components/independent/group/GroupMembersList.vue'
import type { GroupSetting } from '@/type/groupsetting';

const props = defineProps<{
  group: GroupResponse
  myRole?: string
  groupSettings?: GroupSetting
}>()

const roleTranslations = {
  owner: '群主',
  admin: '管理员',
  member: '成员'
}

const showMembersList = ref(false)

const handleViewMembers = () => {
  showMembersList.value = true
}

const handleViewAnnouncement = () => {
  // 点击逻辑待实现
}
</script>

<template>
  <div class="transition-container h-full">
    <Transition name="card-slide" mode="out-in">
      <div
          v-if="!showMembersList"
          key="info"
          class="group-info-card bg-white rounded-lg shadow-sm p-4 h-full"
      >
        <!-- 群头像和基本信息 -->
        <div class="flex items-center mb-4">
          <img
              :src="group.avatar_url || '/images/default-group-avatar.png'"
              class="w-16 h-16 rounded-full mr-4"
              alt="群头像"
          >
          <div>
            <h2 class="text-xl font-semibold">{{ group.name }}</h2>
            <p class="text-gray-500 text-sm">群号: {{ group.group_id }}</p>
          </div>
        </div>

        <!-- 群详情 -->
        <div class="space-y-3">
          <div>
            <span class="text-gray-500">创建时间:</span>
            <span class="ml-2">{{ new Date(group.created_at).toLocaleString() }}</span>
          </div>
          <div>
            <span class="text-gray-500">群组成员:</span>
            <button
                class="ml-2 px-2 py-1 rounded bg-gray-100 transition-colors"
                @click="handleViewMembers"
            >
              点击查看{{ group.member_count }}个群成员>
            </button>
          </div>
          <div>
            <span class="text-gray-500">我的角色:</span>
            <span class="ml-2">{{ roleTranslations[myRole || group.my_role] }}</span>
          </div>
          <div v-if="group.description">
            <span class="text-gray-500">群描述:</span>
            <p class="mt-1">{{ group.description }}</p>
          </div>

          <!-- 预留群公告位置 -->
          <div>
            <span class="text-gray-500">群公告:</span>
            <button
                class="ml-2 px-2 py-1 rounded bg-gray-100 transition-colors"
                @click="handleViewAnnouncement"
            >
              {{ group.announcement || '暂无公告' }}>
            </button>
          </div>
        </div>
      </div>

      <GroupMembersList
          v-else
          key="members"
          :groupId="group.group_id"
          @back="showMembersList = false"
      />
    </Transition>
  </div>
</template>

<style scoped>
.transition-container {
  position: relative;
  height: 100%;
  width: 100%;
}

.group-info-card {
  max-width: 400px;
  position: absolute;
  width: 100%;
}

.card-slide-enter-active,
.card-slide-leave-active {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.card-slide-enter-from {
  opacity: 0;
}

.card-slide-leave-to {
  opacity: 0;
}
</style>