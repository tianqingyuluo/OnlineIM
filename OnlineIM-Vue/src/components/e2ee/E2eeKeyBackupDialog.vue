<script setup lang="ts">
import { ref } from 'vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { useE2eeStore } from '@/stores/e2ee.store'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const e2ee = useE2eeStore()
const passphrase = ref('')
const importPassphrase = ref('')
const importText = ref('')
const busy = ref(false)

async function exportBackup() {
  if (passphrase.value.length < 6) {
    toast.error('备份口令至少 6 位')
    return
  }
  busy.value = true
  try {
    const payload = await e2ee.exportBackup(passphrase.value)
    const blob = new Blob([payload], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'onlineim-e2ee-backup.json'
    link.click()
    URL.revokeObjectURL(url)
    toast.success('密钥备份已导出，请妥善保管口令')
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '导出失败')
  } finally {
    busy.value = false
  }
}

async function importBackup() {
  if (!importText.value.trim()) {
    toast.error('请先选择或粘贴备份文件')
    return
  }
  busy.value = true
  try {
    await e2ee.importBackup(importText.value.trim(), importPassphrase.value)
    toast.success('密钥已导入，历史加密消息将重新解密')
    emit('close')
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '导入失败')
  } finally {
    busy.value = false
  }
}

function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    importText.value = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.readAsText(file)
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-[80] flex items-center justify-center bg-black/40 p-4" data-testid="e2ee-backup-dialog">
    <div class="w-full max-w-md rounded-xl bg-white p-5 shadow-xl">
      <h2 class="text-base font-semibold">密钥备份</h2>
      <p class="mt-1 text-sm text-gray-500">私钥用口令加密后导出。换设备或清空浏览器后，导入同一份备份才能解开历史加密消息。</p>
      <label class="mt-4 block text-sm text-gray-700">
        导出备份
        <input v-model="passphrase" type="password" autocomplete="new-password" placeholder="设置备份口令" class="mt-1 w-full rounded-md border px-3 py-2 text-sm">
      </label>
      <Button class="mt-2" type="button" :disabled="busy" @click="exportBackup">导出私钥备份</Button>
      <label class="mt-5 block text-sm text-gray-700">
        导入备份
        <input class="mt-1 block w-full text-sm" type="file" accept="application/json,.json" @change="onFile">
        <textarea v-model="importText" rows="3" placeholder="或粘贴备份 JSON" class="mt-2 w-full rounded-md border px-3 py-2 font-mono text-xs" />
        <input v-model="importPassphrase" type="password" autocomplete="current-password" placeholder="备份口令" class="mt-2 w-full rounded-md border px-3 py-2 text-sm">
      </label>
      <div class="mt-4 flex justify-end gap-2">
        <Button variant="outline" type="button" @click="emit('close')">关闭</Button>
        <Button type="button" :disabled="busy" data-testid="e2ee-import-submit" @click="importBackup">导入密钥</Button>
      </div>
    </div>
  </div>
</template>
