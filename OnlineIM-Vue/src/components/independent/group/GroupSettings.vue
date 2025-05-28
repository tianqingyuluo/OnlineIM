<template>
    <div class="fixed inset-0 z-50 flex items-start justify-center bg-white/80 bg-opacity-50" @click.self="closeModal">
      <div ref="modalRef" class="fixed top-[10%] w-[40%] rounded-md bg-white shadow-lg">
        <DraggableHeader @drag="handleDrag">
          <h2 class="text-lg font-medium text-gray-800">群组设置</h2>
        </DraggableHeader>
        
        <div class="p-6 border-t border-gray-200">
          <!-- 群头像 -->
          <div class="mb-5 flex flex-col items-center">
            <div 
              class="w-24 h-24 rounded-full overflow-hidden bg-gray-100 border border-gray-200 relative cursor-pointer hover:opacity-90 transition-opacity"
              @click="changeAvatar"
            >
              <img
                :src="previewAvatarUrl || groupInfo?.avatar_url || '/images/group.png'"
                class="w-full h-full object-cover"
              />
              <div class="absolute inset-0 hover:bg-black/80 bg-opacity-30 flex items-center justify-center transition-all">
                <span class="text-white opacity-0 hover:opacity-100 text-sm">更换群头像</span>
              </div>
            </div>
          </div>
    
          <!-- 表单字段 -->
          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">群名称</label>
              <input
                v-model="name"
                class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
                :class="{ 'border-red-500': errors.name }"
              />
              <p v-if="errors.name" class="text-red-500 text-sm mt-1">{{ errors.name }}</p>
            </div>
    
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-2">群描述</label>
              <textarea
                v-model="description"
                rows="3"
                class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
              />
            </div>
          </div>
        </div>
    
        <div class="px-6 py-4 bg-gray-50 flex justify-end space-x-3">
          <button
            @click="closeModal"
            class="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md transition-colors"
          >
            取消
          </button>
          <button
            @click="saveSettings"
            class="px-4 py-2 bg-gray-800 text-white rounded-md hover:bg-gray-700 transition-colors"
          >
            保存
          </button>
        </div>
      </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { toTypedSchema } from '@vee-validate/zod';
import { useForm } from 'vee-validate';
import * as z from 'zod';
import DraggableHeader from '@/components/common/DraggableHeader.vue';
import { groupService } from '@/services/group.service';
import type { GroupResponse } from '@/type/group';

const props = defineProps<{
  groupId: string
}>()

const groupInfo = ref<GroupResponse | null>(null);
const previewAvatarUrl = ref<string | null>(null);

// 表单验证规则
const formSchema = toTypedSchema(
  z.object({
    name: z.string()
      .min(2, "群名称至少需要2个字符")
      .max(20, "群名称最多20个字符"),
    description: z.string()
      .max(100, "群描述最多100个字符")
      .optional()
  })
);

// 获取群组信息
const fetchGroupInfo = async () => {
  try {
    groupInfo.value = await groupService.getGroupInfo(props.groupId);
    if (groupInfo.value) {
      resetForm({
        values: {
          name: groupInfo.value.name || '',
          description: groupInfo.value.description || ''
        }
      });
    }
  } catch (error) {
    console.error('获取群组信息失败:', error);
  }
};

// 初始化表单
const { handleSubmit, errors, defineField, resetForm } = useForm({
  validationSchema: formSchema
});

const [name] = defineField('name');
const [description] = defineField('description');

const saveSettings = handleSubmit(async (values) => {
  if (!groupInfo.value) return;
  
  try {
    const updatedGroup = await groupService.updateGroupByID(props.groupId, {
      name: values.name,
      description: values.description,
      avatar_url: previewAvatarUrl.value || groupInfo.value.avatar_url
    });
    
    // 更新本地数据
    groupInfo.value = updatedGroup;
    if (previewAvatarUrl.value) {
      URL.revokeObjectURL(previewAvatarUrl.value);
      previewAvatarUrl.value = null;
    }
    emit('close');
  } catch (error) {
    console.error('更新群信息失败:', error);
  }
});

const emit = defineEmits(['close']);

// 更换群头像逻辑（与用户头像逻辑一致）
const changeAvatar = () => {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/png, image/jpeg';
  
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      alert('请选择PNG或JPG格式的图片');
      return;
    }
    
    // 创建预览URL
    previewAvatarUrl.value = URL.createObjectURL(file);
    
    try {
      const uploadedUrl = await groupService.uploadGroupAvatar(props.groupId, file);
      if (groupInfo.value) {
        groupInfo.value.avatar_url = uploadedUrl;
      }
    } catch (error) {
      console.error('头像上传失败:', error);
      // 错误提示已集成到全局通知系统
    }
  };
  
  input.click();
};

const closeModal = () => {
  if (previewAvatarUrl.value) {
    URL.revokeObjectURL(previewAvatarUrl.value);
    previewAvatarUrl.value = null;
  }
  emit('close');
};

// 组件卸载时清理
onUnmounted(() => {
  if (previewAvatarUrl.value) {
    URL.revokeObjectURL(previewAvatarUrl.value);
  }
});

onMounted(() => {
  fetchGroupInfo();
});

const modalRef = ref<HTMLElement | null>(null);

const handleDrag = ({ deltaX, deltaY }: { deltaX: number; deltaY: number }) => {
  if (!modalRef.value) return;
  const rect = modalRef.value.getBoundingClientRect();
  modalRef.value.style.top = `${rect.top + deltaY}px`;
  modalRef.value.style.left = `${rect.left + deltaX}px`;
};
</script>