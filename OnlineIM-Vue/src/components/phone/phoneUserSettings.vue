<script setup lang="ts">
import {computed,  ref} from 'vue';
import {useUserStore} from "@/stores/user.ts";
import {meService} from "@/services/me.service.ts";
import {toTypedSchema} from '@vee-validate/zod';
import {useForm} from 'vee-validate';
import * as z from 'zod';
import DraggableHeader from "@/components/common/DraggableHeader.vue";

// 表单验证规则
const formSchema = toTypedSchema(
    z.object({
      nickname: z.union([
        z.string()
            .min(2, "昵称至少需要2个字符")
            .max(20, "昵称最多20个字符"),
        z.null()
      ]).transform(val => val || ''),
      email: z.union([
        z.string()
            .max(50, "邮箱最多50个字符")
            .refine(
                (val) => !val || z.string().email().safeParse(val).success,
                "请输入有效的邮箱地址"
            ),
        z.null()
      ]),
      phone: z.union([
        z.string()
            .refine(
                (val) => !val || /^1[3-9]\d{9}$/.test(val),
                "请输入有效的手机号"
            ),
        z.null()
      ]),
      region: z.union([
        z.string()
            .max(20, "地区最多20个字符"),
        z.null()
      ]).transform(val => val || ''),
      signature: z.union([
        z.string()
            .max(100, "个性签名最多100个字符"),
        z.null()
      ]).transform(val => val || ''),
      gender: z.union([
        z.number().min(0).max(2),
        z.null()
      ]).transform(val => val === null ? 0 : val).default(0)
    })
)


const userStore = useUserStore();

const tempSettings = computed(() => ({
  ...userStore.loggedInUser,
  nickname: userStore.loggedInUser?.nickname || '',
  email: userStore.loggedInUser?.email || '',
  region: userStore.loggedInUser?.region || '',
  gender: userStore.loggedInUser?.gender || 0, // 确保默认值
  phone: userStore.loggedInUser?.phone || '',
  signature: userStore.loggedInUser?.signature || '',
  avatar_url: userStore.loggedInUser?.avatar_url || ''
}));
const { handleSubmit, errors, defineField } = useForm({
  validationSchema: formSchema,
  initialValues: tempSettings.value
})
// 为每个字段添加绑定
const [nickname] = defineField('nickname');
const [email] = defineField('email');
const [phone] = defineField('phone');
const [region] = defineField('region');
const [signature] = defineField('signature');
const [gender] = defineField('gender'); // 确保正确绑定

const saveSettings = handleSubmit(async (values) => {
  try {
    const updatedUser = await meService.updateMe({
      nickname: values.nickname || undefined,
      email: values.email || undefined,
      region: values.region || undefined,
      gender: (values.gender as 0 | 1 | 2 | undefined) || 0, // 0表示未设置，1表示男，2表示女
      phone: values.phone || undefined,
      signature: values.signature || undefined,
      avatar_url: tempSettings.value.avatar_url || undefined
    });
    userStore.setLoggedInUser(updatedUser);
    emit('close');
  } catch (error) {
    console.error('更新失败:', error);
  }
});
const emit = defineEmits(['close']);


// 更换头像逻辑
const changeAvatar = () => {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/png, image/jpeg';

  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    // 验证文件类型
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      alert('请选择PNG或JPG格式的图片');
      return;
    }

    try {
      // 使用uploadAvatar方法上传文件并获取URL
      tempSettings.value.avatar_url = await meService.uploadAvatar(file);
    } catch (error) {
      console.error('头像上传失败:', error);
      alert('头像上传失败，请重试');
    }
  };

  input.click();
};
// 关闭模态框
const closeModal = () => {
  emit('close');
};

const modalRef = ref<HTMLElement | null>(null)

const handleDrag = ({ deltaX, deltaY }: { deltaX: number; deltaY: number }) => {
  if (!modalRef.value) return

  // 使用 getBoundingClientRect 获取精确位置
  const rect = modalRef.value.getBoundingClientRect()
  modalRef.value.style.top = `${rect.top + deltaY}px`
  modalRef.value.style.left = `${rect.left + deltaX}px`
}

</script>
<template>
  <div ref="modalRef" class="fixed z-50 top-[2.5%] left-[30%] w-[40%] rounded-md overflow-y-auto">
    <DraggableHeader @drag="handleDrag">
      <h2 class="text-lg font-medium text-gray-800">用户设置</h2>
    </DraggableHeader>
    <div class="bg-white  p-6 w-full shadow-md border border-gray-200">

      <!-- 头像区域 - 居中显示并整合更换功能 -->
      <div class="mb-5 flex flex-col items-center">
        <div
            class="w-24 h-24 rounded-full overflow-hidden bg-gray-100 border border-gray-200 relative cursor-pointer hover:opacity-90 transition-opacity"
            @click="changeAvatar"
        >
          <img
              :src="tempSettings.avatar_url || '/images/help.png'"
              class="w-full h-full object-cover"
              alt="用户头像"
          >
          <div class="absolute inset-0  hover:bg-black/80 bg-opacity-30 flex items-center justify-center transition-all">
            <span class="text-white opacity-0 hover:opacity-100 text-sm">更换头像</span>
          </div>
        </div>
      </div>

      <!-- 表单字段 -->
      <div class="space-y-1.5">
        <!-- 昵称字段 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">昵称</label>
          <!-- 昵称字段 -->
          <input
              v-model="nickname"
              type="text"
              class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
              placeholder="请输入昵称"
              :class="{'border-red-500': errors.nickname}"
          >
          <!-- 其他字段也类似修改v-model绑定 -->
          <p v-if="errors.nickname" class="text-red-500 text-sm mt-1">{{ errors.nickname }}</p>
        </div>

        <!-- 邮箱和手机号并排放置 -->
        <div class="grid grid-cols-2 gap-4">
          <!-- 邮箱 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">邮箱</label>
            <input
                v-model="email"
                type="email"
                class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
                placeholder="请输入邮箱"
                :class="{'border-red-500': errors.email}"
            >
            <p v-if="errors.email" class="text-red-500 text-sm mt-1">{{ errors.email }}</p>
          </div>
          <!-- 手机号码 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">手机号</label>
            <input
                v-model="phone"
                type="tel"
                class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
                placeholder="请输入手机号码"
                :class="{'border-red-500': errors.phone}"
            >
            <p v-if="errors.phone" class="text-red-500 text-sm mt-1">{{ errors.phone }}</p>
          </div>
        </div>

        <!-- 地区和性别并排放置 -->
        <div class="grid grid-cols-2 gap-4">
          <!-- 地区 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">地区</label>
            <input
                v-model="region"
                type="text"
                class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
                placeholder="请输入所在地区"
                :class="{'border-red-500': errors.region}"
            >
            <p v-if="errors.region" class="text-red-500 text-sm mt-1">{{ errors.region }}</p>
          </div>
          <!-- 性别选择 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">性别</label>
            <select
                v-model="gender"
                class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
            >
              <option :value="0">未设置</option>
              <option :value="1">男</option>
              <option :value="2">女</option>
            </select>
          </div>
        </div>

        <!-- 个性签名 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">个性签名</label>
          <textarea
              v-model="signature"
              rows="3"
              class="w-full p-2.5 border border-gray-200 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400 focus:border-gray-400 bg-white text-gray-800"
              placeholder="请输入个性签名"
              :class="{'border-red-500': errors.signature}"
          ></textarea>
          <p v-if="errors.signature" class="text-red-500 text-sm mt-1">{{ errors.signature }}</p>
        </div>
      </div>

      <!-- 按钮区域 -->
      <div class="flex justify-end space-x-3 mt-6">
        <button
            @click="closeModal"
            class="px-4 py-2 border border-gray-200 rounded-md text-gray-600 hover:bg-gray-50 transition-colors"
        >
          取消
        </button>
        <button
            @click="saveSettings"
            class="px-4 py-2 bg-gray-800 text-white rounded-md hover:bg-gray-700 transition-colors"
        >
          保存设置
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 原有样式保持不变 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-content-enter-active,
.modal-content-leave-active {
  transition: all 0.3s ease;
}
.modal-content-enter-from,
.modal-content-leave-to {
  transform: translateY(-20px);
  opacity: 0;
}
</style>
