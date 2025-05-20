<script setup lang="ts">
import type { GroupMember, GroupResponse } from '@/type/group.ts'
import { onMounted, ref } from 'vue';
import GroupMembersList from '@/components/independent/group/GroupMembersList.vue'
import type { GroupSetting } from '@/type/groupsetting';
import { groupService } from '@/services/group.service';
import {Switch} from "@/components/ui/switch";
import {GroupSettingService} from '@/services/groupsetting.service';
const props = defineProps<{
  group: GroupResponse
  myRole?: string
  groupSettings: GroupSetting
  currentUser: any
}>()

const roleTranslations = {
  owner: '群主',
  admin: '管理员',
  member: '成员'
}

const showMembersList = ref(false)
const groupMembersListRef = ref<InstanceType<typeof GroupMembersList> | null>(null)

const handleViewMembers = () => {
  showMembersList.value = true
}

const handleViewAnnouncement = () => {
  // 点击逻辑待实现
}

const handleSettingChange = async (key: keyof GroupSetting, value: any) => {
  try {
    // 将布尔值转换为数字类型
    const processedValue = typeof value === 'boolean' ? (value ? 1 : 0) : value;
    // 创建包含所有设置字段的完整对象
    const updatedSettings = await GroupSettingService.updateGroupSetting(props.group.group_id, {
      ...props.groupSettings,
      [key]: processedValue
    })
    // 更新本地状态
    if (props.groupSettings) {
      props.groupSettings[key] = value
    }
    return updatedSettings
  } catch (err) {
    console.error('更新群设置失败:', err)
    throw err
  }
}

const nicknameTemp = ref('')

async function saveNickname() {
  if (props.group) {
    try {
      await groupService.updateMemberNickname(
        props.group.group_id,
        nicknameTemp.value
      )
      props.group.name = nicknameTemp.value
    } catch (error) {
      console.error('更新群昵称失败:', error)
    }
  }
}

const members = ref<GroupMember[]>([])
const loading = ref(false)
const error = ref<Error | null>(null)

// 获取群成员
const fetchMembers = async () => {
  try {
    loading.value = true
    const response = await groupService.getGroupMembers(props.group.group_id, { 
      offset: 0
    })
    members.value = response.members.slice(0, 11)
  } catch (err) {
    error.value = err as Error
  } finally {
    loading.value = false
  }
}

// 初始化获取成员
onMounted(() => {
  fetchMembers()
})
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
  <Input 
    v-model="nicknameTemp" 
    @blur="saveNickname"
    @keyup.enter="saveNickname"
    :default-value="group.name"
    class="w-[180px]"
  />
  <p class="text-gray-500 text-sm">群昵称: {{ }}</p>
</div>
        </div>

        <!-- 群详情 -->
        <div class="space-y-3">
          <div>
            <span class="text-gray-500">创建时间:</span>
            <span class="ml-2">{{ new Date(group.created_at).toLocaleString() }}</span>
          </div>
          <div>
            <span class="text-gray-500">我的角色:</span>
            <span class="ml-2">{{ roleTranslations[myRole || group.my_role] }}</span>
          </div>
          <div v-if="group.description">
            <span class="text-gray-500">群描述:</span>
            <p class="mt-1">{{ group.description }}</p>
          </div>

          <!-- 群成员网格 -->
          <div v-if="members.length > 0" class="mt-4">
            <div class="flex items-center mb-2">
              <span class="text-gray-500">群成员:</span>
              <button
                  class="ml-2 px-2 py-1 rounded bg-gray-100 transition-colors"
                  @click="handleViewMembers"
              >
                点击查看{{ group.member_count }}个群成员>
              </button>
            </div>
            <div class="grid grid-cols-4 gap-4">
              <div 
                v-for="member in members" 
                :key="member.user_id"
                class="flex flex-col items-center hover:bg-gray-100"
              >
                <img
                  :src="member.avatar_url || '/images/default-avatar.png'"
                  class="w-10 h-10 rounded-full"
                  :alt="member.username"
                >
                <span class="text-xs mt-2 truncate w-full text-center">{{ member.username }}</span>
              </div>
              <!-- 邀请成员按钮 -->
              <div class="flex flex-col items-center cursor-pointer hover:bg-gray-100">
                <div class="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center">
                  <span class="text-4xl">+</span>
                </div>
                <span class="text-xs mt-1">邀请成员</span>
              </div>
            </div>
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
          
          <!-- 群组设置 -->
          <div v-if="myRole === 'owner' || myRole === 'admin'" class="space-y-2">
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许成员邀请</span>
              <Switch v-model="groupSettings.allowMemberInvite" @update:modelValue="val => handleSettingChange('allowMemberInvite', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许成员修改群名</span>
              <Switch v-model="groupSettings.allowMemberModifyName" @update:modelValue="val => handleSettingChange('allowMemberModifyName', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许成员上传文件</span>
              <Switch v-model="groupSettings.allowMemberUploadFile" @update:modelValue="val => handleSettingChange('allowMemberUploadFile', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许成员@所有人</span>
              <Switch v-model="groupSettings.allowMemberAtAll" @update:modelValue="val => handleSettingChange('allowMemberAtAll', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许查看历史消息</span>
              <Switch v-model="groupSettings.allowViewHistoryMessage" @update:modelValue="val => handleSettingChange('allowViewHistoryMessage', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">禁言设置</span>
              <Switch v-model="groupSettings.mute_type" @update:modelValue="val => handleSettingChange('mute_type', val)"/>
            </div>
          </div>
        </div>
      </div>

      <GroupMembersList
          v-else
          key="members"
          :groupId="group.group_id"
          :myRole="group.my_role"
          @back="showMembersList = false"
          ref="groupMembersListRef"
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