<script setup lang="ts">
import type {GroupMemberAll, GroupResponse} from '@/type/group.ts'
import {inject, nextTick, onMounted, ref, shallowRef, watch} from 'vue';
import GroupMembersList from '@/components/independent/group/GroupMembersList.vue'
import Announcement from '@/components/Announcement.vue'; // 引入 Announcement 组件
import type {GroupSetting} from '@/type/groupsetting';
import {groupService} from '@/services/group.service';
import {Switch} from "@/components/ui/switch";
import {GroupSettingService} from '@/services/groupsetting.service';
import {Button} from "@/components/ui/button";
import {toast} from 'vue-sonner';
import AddFriendModal from '@/components/independent/group/AddFriendModal.vue';
import {conversationService} from "@/services/conversation.service.ts";
import GroupMemberProfileModal from '@/components/independent/group/GroupMemberProfileModal.vue'; // 引入成员资料模态框组件

const showAddFriendModal = ref(false);
const showMemberProfileModal = ref(false); // 控制成员资料模态框显示状态
const selectedMember = ref<GroupMemberAll | null>(null); // 存储选中的成员数据

const props = defineProps<{
  group: GroupResponse
  myRole?: string
  groupSettings: GroupSetting
  conversation: any
}>()

const groupMembersListRef = shallowRef<{
  membersListContainer?: HTMLElement
} | null>(null)
const showMembersList = ref(false)
const showAnnouncement = ref(false); // 控制公告组件显示状态

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
const isMute = ref(props.conversation?.is_mute || false)
const isPinned = ref(props.conversation?.is_pinned || false)

defineExpose({  groupMembersListRef})
const handleViewMembers = () => {
  console.log('handleViewMembers被触发，showMembersList:', showMembersList.value)
  console.log('groupMembersListRef:', groupMembersListRef.value)
  showMembersList.value = true
}

const handleViewAnnouncement = () => {
  showAnnouncement.value = true; // 点击公告按钮时显示 Announcement 组件
}

// 处理点击成员头像事件
const handleMemberClick = (member: GroupMemberAll) => {
  selectedMember.value = member;
  showMemberProfileModal.value = true;
};

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

const setMute = async (isMute: boolean) => {
    if (isMute) {
    await conversationService.muteConversation(props.conversation.conversation_id)
  }else
    await conversationService.unmuteConversation(props.conversation.conversation_id)
};

const setPinned =async (isPinned: boolean) => {
  if (isPinned) {
    await conversationService.topConversation(props.conversation.conversation_id)
  }else
    await conversationService.unTopConversation(props.conversation.conversation_id)
};

const clearGroupMessages = inject<() => Promise<void>>('clearGroupMessages');
const clearMessage = async () => {
   await conversationService.clearMessages(props.conversation.conversation_id);
   if (clearGroupMessages) {
     await clearGroupMessages();
   }
 };

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
  console.log( "父组件传进来的",props.group,props.conversation)
  fetchMembers()

})

</script>

<template>
  <div class="transition-container h-full flex flex-col">
    <Transition name="card-slide" mode="out-in">
      <!-- 群信息卡片 -->
      <div
          v-if="!showMembersList && !showAnnouncement"
          key="info"
          class="group-info-card bg-white rounded-lg shadow-sm p-6 h-full flex flex-col"
      >
        <!-- 群头像和基本信息 -->
        <div class="flex items-start mb-6">
          <div class="relative flex-shrink-0">
            <img
                :src="group.avatar_url || '/images/default-group-avatar.png'"
                class="w-20 h-20 rounded-full mr-5"
                alt="群头像"
            >
            <div
                v-if="myRole === 'owner' || myRole === 'admin'"
                class="absolute inset-0 flex items-center justify-center w-20 h-20 rounded-full mr-5 bg-black/80 opacity-0 hover:opacity-100 transition-opacity duration-200 cursor-pointer"
                @click="changeAvatar"
            >
              <span class="text-white text-xs">更改头像</span>
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <h2 class="text-xl font-semibold text-gray-800 truncate">{{ group.name }}</h2>
            <p class="text-gray-500 text-sm mt-1">创建时间: {{ group.create_at }}</p>
            <p class="text-gray-500 text-sm mt-1">我的角色: {{ roleTranslations[group.my_role] }}</p>
          </div>
        </div>

        <!-- 群详情内容区域 -->
        <div class="flex-1 overflow-y-auto space-y-4 pr-2 -mr-2">
          <!-- 群描述 -->
          <div v-if="group.description" class="bg-gray-50 p-3 rounded-lg">
            <h3 class="text-gray-500 text-sm font-medium mb-1">群描述</h3>
            <p class="text-gray-700">{{ group.description }}</p>
          </div>

          <!-- 群公告 -->
          <div class="bg-gray-100 p-3 rounded-lg">
            <h3 class="text-gray-500 text-sm font-medium mb-1">群公告</h3>
            <button
                class="w-full text-left text-gray-700 bg-white hover:text-primary transition-colors hover:bg-gray-50"
                @click="handleViewAnnouncement"
            >
              {{ group.announcement || '暂无公告' }}
            </button>
          </div>

          <!-- 群成员网格 -->
          <div v-if="members.length > 0" class="mt-6">
            <div class="flex items-center mb-3">
              <span class="text-gray-500 text-sm">群成员：</span>
              <button
                  class="ml-2 px-3 py-1 text-sm rounded bg-gray-100 hover:bg-gray-200 transition-colors"
                  @click="handleViewMembers"
              >
                查看全部 {{ group.member_count }} 人
              </button>
            </div>

            <div class="grid grid-cols-4 gap-y-4"> <!-- 只保留垂直间隙 -->
              <!-- 成员列表 -->
              <div
                  v-for="member in members.slice(0, 11)"
                  :key="member.user_info.user_id"
                  class="flex flex-col items-center hover:bg-gray-100 cursor-pointer" 
                  @click="handleMemberClick(member)" 
              >
                <div class="w-14 h-14 rounded-full bg-gray-100 p-1 flex justify-center items-center"> <!-- 灰色圆形背景 -->
                  <img
                      :src="member.user_info.avatar_url || '/images/default-avatar.png'"
                      class="w-12 h-12 rounded-full object-cover"
                      :alt="member.user_info.username"
                  >
                </div>
                <span class="text-xs mt-2 truncate w-full text-center">
        {{ member.user_info.nickname || member.user_info.username }}
      </span>
              </div>

              <!-- 邀请成员按钮 -->
              <div
                  class="flex flex-col items-center"
                  @click="showAddFriendModal = true"
              >
                <div class="w-14 h-14 rounded-full bg-white border border-gray-200 flex items-center justify-center hover:bg-gray-50 transition-colors cursor-pointer">
                  <span class="text-2xl text-gray-500">+</span>
                </div>
                <span class="text-xs mt-2 text-gray-500">邀请成员</span>
              </div>
            </div>
          </div>
          <!-- 群组设置 -->
          <div v-if="myRole === 'owner' || myRole === 'admin'" class="bg-gray-50 p-3 rounded-lg space-y-3">
            <h3 class="text-gray-500 text-sm font-medium">群组设置</h3>
            <div class="space-y-2">
              <div class="flex items-center justify-between py-2">
                <span class="text-gray-700 text-sm">允许成员邀请</span>
                <Switch v-model="groupSettings.allow_member_invite" @update:modelValue="val => handleSettingChange('allow_member_invite', val)"/>
              </div>
              <div class="flex items-center justify-between py-2">
                <span class="text-gray-700 text-sm">允许成员修改群名</span>
                <Switch v-model="groupSettings.allow_member_modify_name" @update:modelValue="val => handleSettingChange('allow_member_modify_name', val)"/>
              </div>
              <div class="flex items-center justify-between py-2">
                <span class="text-gray-700 text-sm">允许成员上传文件</span>
                <Switch v-model="groupSettings.allow_member_upload_file" @update:modelValue="val => handleSettingChange('allow_member_upload_file', val)"/>
              </div>
              <div class="flex items-center justify-between py-2">
                <span class="text-gray-700 text-sm">允许成员@所有人</span>
                <Switch v-model="groupSettings.allow_member_at_all" @update:modelValue="val => handleSettingChange('allow_member_at_all', val)"/>
              </div>
              <div class="flex items-center justify-between py-2">
                <span class="text-gray-700 text-sm">允许查看历史消息</span>
                <Switch v-model="groupSettings.allow_view_history_message" @update:modelValue="val => handleSettingChange('allow_view_history_message', val)"/>
              </div>
            </div>
          </div>

          <!-- 个人设置 -->
          <div class="bg-gray-50 p-3 rounded-lg space-y-3">
            <h3 class="text-gray-500 text-sm font-medium">个人设置</h3>
            <div class="flex items-center justify-between py-2">
              <span class="text-gray-700 text-sm">设为免打扰</span>
              <Switch v-model="isMute" @update:modelValue="val => setMute(val)"/>
            </div>
            <div class="flex items-center justify-between py-2">
              <span class="text-gray-700 text-sm">设为置顶</span>
              <Switch v-model="isPinned" @update:modelValue="val => setPinned(val)"/>
            </div>
          </div>
        </div>

        <!-- 底部操作按钮 -->
        <div class="mt-6 space-y-3">
          <Button
              class="w-full p-3 bg-white text-red-500 border border-red-500 hover:bg-red-50 transition-colors"
              @click="clearMessage"
          >
            清空聊天记录
          </Button>
          <Button
              v-if="group?.my_role === 'owner'"
              class="w-full p-3 bg-white text-red-500 border border-red-500 hover:bg-red-50 transition-colors"
              @click="handleDissolveGroup"
          >
            解散群组
          </Button>
        </div>
      </div>

      <!-- 群成员列表 -->
      <GroupMembersList
          v-else-if="showMembersList && !showAnnouncement"
          key="members"
          :groupId="group.group_id"
          :myRole="group.my_role"
          @back="showMembersList = false"
          ref="groupMembersListRef"
          @click.stop
      />

      <!-- 群公告组件 -->
      <!-- 修改为覆盖层显示 -->
    </Transition>

    <AddFriendModal
        v-if="showAddFriendModal"
        @close="showAddFriendModal = false"
        :group-id="group.group_id"
    />

    <!-- 群公告覆盖层 -->
    <Transition name="fade-slide" mode="out-in">
      <div v-if="showAnnouncement" class="fixed inset-0 flex z-50">
        <div class="fixed inset-0 bg-white/80 transition-opacity" @click="showAnnouncement = false"></div>
        <Announcement
            class="relative z-50 w-[80%] max-w-2xl m-auto"
            :groupId="group.group_id"
            @back="showAnnouncement = false"
        />
      </div>
    </Transition>

    <!-- 成员资料模态框 -->
    <GroupMemberProfileModal
        v-if="showMemberProfileModal"
        :member="selectedMember"
        @close="showMemberProfileModal = false"
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

/* 新增覆盖层过渡样式 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* 蒙版过渡 */
.bg-white\/80 {
  transition: opacity 0.3s ease;
}
.fade-slide-enter-from .bg-white\/80,
.fade-slide-leave-to .bg-white\/80 {
  opacity: 0;
}
</style>