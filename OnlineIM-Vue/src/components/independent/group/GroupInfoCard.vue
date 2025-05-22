<script setup lang="ts">
import type {GroupMemberAll, GroupResponse} from '@/type/group.ts'
import {nextTick, onMounted, ref, shallowRef, watch} from 'vue';
import GroupMembersList from '@/components/independent/group/GroupMembersList.vue'
import type {GroupSetting} from '@/type/groupsetting';
import {groupService} from '@/services/group.service';
import {Switch} from "@/components/ui/switch";
import {GroupSettingService} from '@/services/groupsetting.service';
import {Button} from "@/components/ui/button";
import {toast} from 'vue-sonner';
import AddFriendModal from '@/components/independent/group/AddFriendModal.vue';

const showAddFriendModal = ref(false);

const props = defineProps<{
  group: GroupResponse
  myRole?: string
  groupSettings: GroupSetting
  currentUser: any
}>()

const groupMembersListRef = shallowRef<{
  membersListContainer?: HTMLElement
} | null>(null)
const showMembersList = ref(false)
// 添加监听确保子组件加载
watch(() => showMembersList.value, (newVal) => {
  if (newVal) {
    nextTick(() => {
      console.log('子组件引用:', groupMembersListRef.value?.membersListContainer)
    })
  }
})
const roleTranslations = {
  owner: '群主',
  admin: '管理员',
  member: '成员'
}


defineExpose({  groupMembersListRef})
const handleViewMembers = () => {
  console.log('handleViewMembers被触发，showMembersList:', showMembersList.value)
  console.log('groupMembersListRef:', groupMembersListRef.value)
  showMembersList.value = true
}

const handleViewAnnouncement = () => {
  // 点击逻辑待实现
}

const handleSettingChange = async (key: keyof GroupSetting, value: any) => {

  try {
    // 创建包含所有设置字段的完整对象
    const updatedSettings = await GroupSettingService.updateGroupSetting(props.group.group_id, {
      ...props.groupSettings,
      [key]: value
    })
    // 更新本地状态
    if (props.groupSettings) {
      props.groupSettings[key] = value
    }
    return updatedSettings
  } catch (err) {
    console.error('更新群设置失败:', err)
  }
}


const members = ref<GroupMemberAll[]>([])
const loading = ref(false)
const error = ref<Error | null>(null)

const handleDissolveGroup = async () => {
  try {
    await groupService.delGroup(props.group.group_id)
    toast.success('群组解散成功')
    // 这里可以添加解散成功后的跳转逻辑
  } catch (error) {
    console.error('解散群组失败:', error)
    toast.error('解散群组失败')
  }
}

const changeAvatar = () => {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/png, image/jpeg';
  
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    
    // 验证文件类型
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      toast.error('请选择PNG或JPG格式的图片');
      return;
    }
    try {
      // 上传群头像
      props.group.avatar_url = await groupService.uploadGroupAvatar(props.group.group_id, file);
      toast.success('群头像更新成功');
    } catch (error) {
      console.error('群头像上传失败:', error);
      toast.error('群头像上传失败，请重试');
    }
  };
  
  input.click();
}
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
  console.log( "父组件传进来的",props.group)
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
          <div class="relative">
            <img
                :src="group.avatar_url || '/images/default-group-avatar.png'"
                class="w-16 h-16 rounded-full mr-4"
                alt="群头像"
            >
            <div 
                v-if="myRole === 'owner' || myRole === 'admin'"
                class="absolute inset-0 flex items-center justify-center w-16 h-16 rounded-full mr-4 bg-black/80 opacity-0 hover:opacity-100 transition-opacity duration-200 cursor-pointer"
                @click="changeAvatar"
            >
                <span class="text-white text-xs">更改头像</span>
            </div>
          </div>
          <div>
          <p class="text-gray-500 text-sm">群昵称: {{group.name }}</p>
</div>
        </div>

        <!-- 群详情 -->
        <div class="space-y-3">
          <div>
            <span class="text-gray-500">创建时间:</span>
            <span class="ml-2">{{ group.create_at }}</span>
          </div>
          <div>
            <span class="text-gray-500">我的角色:</span>
            <span class="ml-2">{{ roleTranslations[group.my_role] }}</span>
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
                :key="member.user_info.user_id"
                class="flex flex-col items-center hover:bg-gray-100"
              >
                <img
                  :src="member.user_info.avatar_url || '/images/default-avatar.png'"
                  class="w-10 h-10 rounded-full"
                  :alt="member.user_info.username"
                >
                <span class="text-xs mt-2 truncate w-full text-center">{{ member.user_info.nickname||member.user_info.username }}</span>
              </div>
              <!-- 邀请成员按钮 -->
              <div 
                class="flex flex-col items-center cursor-pointer hover:bg-gray-100"
                @click="showAddFriendModal = true"
              >
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
              <Switch v-model="groupSettings.allow_member_invite" @update:modelValue="val => handleSettingChange('allow_member_invite', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许成员修改群名</span>
              <Switch v-model="groupSettings.allow_member_modify_name" @update:modelValue="val => handleSettingChange('allow_member_modify_name', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许成员上传文件</span>
              <Switch v-model="groupSettings.allow_member_upload_file" @update:modelValue="val => handleSettingChange('allow_member_upload_file', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许成员@所有人</span>
              <Switch v-model="groupSettings.allow_member_at_all" @update:modelValue="val => handleSettingChange('allow_member_at_all', val)"/>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="text-gray-500">允许查看历史消息</span>
              <Switch v-model="groupSettings.allow_view_history_message" @update:modelValue="val => handleSettingChange('allow_view_history_message', val)"/>
            </div>
          </div>
          <Button
              v-if="group?.my_role === 'owner'"
              class="flex flex-col items-center justify-center w-full p-4 mt-4 bg-white text-red-500 border border-red-500 hover:bg-red-50 hover:scale-105 transition-transform duration-200"
              @click="handleDissolveGroup"
          >
            解散群组
          </Button>
        </div>
      </div>


      <GroupMembersList
          v-else
          key="members"
          :groupId="group.group_id"
          :myRole="group.my_role"
          @back="showMembersList = false"
          ref="groupMembersListRef"
          @click.stop
      />
    </Transition>
    
    <AddFriendModal 
      v-if="showAddFriendModal"
      @close="showAddFriendModal = false"
      :group-id="group.group_id"
    />
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