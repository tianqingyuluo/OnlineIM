<script setup lang="ts">
import { computed, ref } from 'vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import E2eeStatusBanner from '@/components/e2ee/E2eeStatusBanner.vue'
import E2eeToggle from '@/components/e2ee/E2eeToggle.vue'
import { useE2eeStore } from '@/stores/e2ee.store'

const props = defineProps<{
  conversationId: string
  peerUserId: string
  peerName?: string
  compact?: boolean
}>()

const e2ee = useE2eeStore()
const busy = ref(false)
const enabled = computed(() => e2ee.isEnabled(props.conversationId))
const warning = computed(() => e2ee.keyWarnings[props.peerUserId] || '')

async function onToggle(next: boolean) {
  busy.value = true
  try {
    await e2ee.setEnabled(props.conversationId, props.peerUserId, next)
    toast.success(next ? '已开启端到端加密' : '已关闭端到端加密')
  } catch (error) {
    const message = error instanceof Error
      ? error.message
      : (error as { message?: string }).message || '设置加密会话失败'
    toast.error(message)
  } finally {
    busy.value = false
  }
}

function openVerify() {
  e2ee.openVerify(props.peerUserId)
}

function openBackup() {
  e2ee.openBackup()
}
</script>

<template>
  <div data-testid="e2ee-settings">
    <E2eeStatusBanner v-if="compact" :enabled="enabled" :warning="warning" />
    <E2eeToggle :enabled="enabled" :disabled="busy" @update:enabled="onToggle" />
    <p v-if="!compact" class="pb-1 text-xs text-gray-400">与 {{ peerName || '对方' }} 的私聊</p>
    <div v-if="!compact" class="flex flex-wrap gap-2 pb-2">
      <Button size="sm" variant="outline" type="button" @click="openVerify">核对安全码</Button>
      <Button size="sm" variant="outline" type="button" @click="openBackup">密钥备份</Button>
      <span v-if="e2ee.isVerified(peerUserId)" class="self-center text-xs text-emerald-700">已验证</span>
      <span v-else-if="warning" class="self-center text-xs text-amber-700">公钥已变化</span>
    </div>
  </div>
</template>
