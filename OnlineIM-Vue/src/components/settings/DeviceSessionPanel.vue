<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deviceService, type DeviceSession } from '@/services/device.service'
import { deviceDisplayName } from '@/utils/device-session'
import { toast } from 'vue-sonner'

const emit = defineEmits<{ close: [] }>()

const devices = ref<DeviceSession[]>([])
const loading = ref(true)
const failed = ref(false)
const loggingOut = ref('')

async function load() {
  loading.value = true
  failed.value = false
  try {
    devices.value = await deviceService.list()
  } catch {
    failed.value = true
    devices.value = []
  } finally {
    loading.value = false
  }
}

async function logout(device: DeviceSession) {
  if (device.current || loggingOut.value) return
  if (!window.confirm(`登出${deviceDisplayName(device)}？该设备需要重新登录。`)) return
  loggingOut.value = device.device_id
  try {
    await deviceService.logout(device.device_id)
    toast.success('已登出该设备')
    await load()
  } catch {
    toast.error('登出失败，请稍后再试')
  } finally {
    loggingOut.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="fixed inset-0 z-[10000] flex items-center justify-center bg-black/40" @click.self="emit('close')">
    <div class="w-[92%] max-w-md rounded-xl bg-white p-4 shadow-xl" role="dialog" aria-label="登录设备">
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-base font-medium text-gray-800">登录设备</h2>
        <button type="button" class="px-2 text-gray-500" @click="emit('close')">关闭</button>
      </div>
      <p class="mb-3 text-xs text-gray-500">网页、桌面和手机可以同时在线。同一类型再次登录时，旧的那一台会被下线。</p>
      <p v-if="loading" class="py-6 text-center text-sm text-gray-500">加载中…</p>
      <p v-else-if="failed" class="py-6 text-center text-sm text-red-500">设备列表加载失败</p>
      <p v-else-if="devices.length === 0" class="py-6 text-center text-sm text-gray-500">暂无登录设备</p>
      <ul v-else class="space-y-2">
        <li
          v-for="device in devices"
          :key="device.device_id"
          class="flex items-center justify-between rounded-lg border border-gray-200 px-3 py-2"
        >
          <div>
            <p class="text-sm text-gray-800 break-words">
              {{ deviceDisplayName(device) }}
              <span v-if="device.current" class="ml-1 text-xs text-blue-600">当前设备</span>
            </p>
            <p class="text-xs text-gray-500">{{ device.online ? '在线' : '已登录，当前不在线' }}</p>
          </div>
          <button
            v-if="!device.current"
            type="button"
            class="text-sm text-red-600 disabled:opacity-50"
            :disabled="loggingOut === device.device_id"
            @click="logout(device)"
          >
            登出
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
