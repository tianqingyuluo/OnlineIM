<script setup lang="ts">
import { computed, ref } from 'vue'
import { Settings } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  currentServerEndpoints,
  isTauriRuntime,
  setServerConfig,
} from '@/services/tauri-bridge'

const open = ref(false)
const apiBaseUrl = ref('')
const wsBaseUrl = ref('')
const errorMessage = ref('')
const saving = ref(false)
const visible = computed(() => isTauriRuntime())

function show() {
  const current = currentServerEndpoints()
  apiBaseUrl.value = current.api_base_url
  wsBaseUrl.value = current.ws_base_url
  errorMessage.value = ''
  open.value = true
}

async function save() {
  saving.value = true
  errorMessage.value = ''
  try {
    await setServerConfig(apiBaseUrl.value, wsBaseUrl.value)
    open.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存服务器地址失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <button
    v-if="visible"
    type="button"
    class="inline-flex size-9 items-center justify-center rounded-md text-gray-500 hover:bg-gray-100 hover:text-gray-800"
    aria-label="服务器地址"
    @click="show"
  >
    <Settings class="size-4" />
  </button>

  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="open = false">
    <div class="w-full max-w-md rounded-lg border border-gray-200 bg-white p-4 shadow-lg" role="dialog" aria-labelledby="server-endpoint-title">
      <h2 id="server-endpoint-title" class="text-base font-medium text-gray-900">服务器地址</h2>
      <p class="mt-1 text-sm text-gray-500">桌面端可以指向任意 OnlineIM 服务器，不必重新打包。</p>
      <form class="mt-4 space-y-3" @submit.prevent="save">
        <label class="block space-y-1 text-sm text-gray-700">
          <span>API 地址</span>
          <Input v-model="apiBaseUrl" type="url" placeholder="http://localhost:8080/api/v1" />
        </label>
        <label class="block space-y-1 text-sm text-gray-700">
          <span>WebSocket 地址</span>
          <Input v-model="wsBaseUrl" type="text" placeholder="ws://localhost:8081/api/v1/chat" />
        </label>
        <p v-if="errorMessage" class="text-sm text-red-500">{{ errorMessage }}</p>
        <div class="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" @click="open = false">取消</Button>
          <Button type="submit" :disabled="saving">保存</Button>
        </div>
      </form>
    </div>
  </div>
</template>
