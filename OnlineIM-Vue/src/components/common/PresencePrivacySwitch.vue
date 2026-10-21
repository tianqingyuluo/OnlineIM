<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Switch } from '@/components/ui/switch'
import { usePresenceStore } from '@/stores/presence'
import { toast } from 'vue-sonner'

const presenceStore = usePresenceStore()
const busy = ref(false)

onMounted(() => {
  void presenceStore.loadSettings().catch(() => {
    // 设置读取失败时保持默认“不隐藏”，保存时会再提交。
  })
})

async function onToggle(value: boolean) {
  busy.value = true
  try {
    await presenceStore.setHidden(value)
  } catch (error) {
    console.error('更新在线状态隐私失败:', error)
    toast.error('在线状态设置失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex items-center justify-between gap-3 py-2" data-testid="presence-privacy">
    <div class="min-w-0">
      <div class="text-sm font-medium text-gray-800">隐藏在线状态</div>
      <p class="text-xs text-gray-500">开启后好友和群成员看到你离线，也不会看到上次在线时间</p>
    </div>
    <Switch
      :model-value="presenceStore.hideOnlineStatus"
      :disabled="busy"
      aria-label="隐藏在线状态"
      @update:model-value="onToggle"
    />
  </div>
</template>
