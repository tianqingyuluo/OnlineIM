<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { formatSafetyNumber } from '@/crypto/e2ee'
import { useE2eeStore } from '@/stores/e2ee.store'

const props = defineProps<{
  open: boolean
  peerUserId: string
  peerName?: string
}>()

const emit = defineEmits<{
  close: []
}>()

const e2ee = useE2eeStore()
const safety = ref('')
const loading = ref(false)

const peer = computed(() => e2ee.peerKeys[props.peerUserId])
const warning = computed(() => e2ee.keyWarnings[props.peerUserId])
const verified = computed(() => e2ee.isVerified(props.peerUserId))

watch(() => props.open, (open) => {
  if (!open) return
  loading.value = true
  safety.value = ''
  void e2ee.safetyNumberFor(props.peerUserId)
    .then(value => { safety.value = formatSafetyNumber(value) })
    .catch(error => {
      toast.error(error instanceof Error ? error.message : '暂时无法生成安全码')
    })
    .finally(() => { loading.value = false })
}, { immediate: true })

function markVerified() {
  e2ee.markVerified(props.peerUserId)
  toast.success('已标记为已验证')
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-[80] flex items-center justify-center bg-black/40 p-4" data-testid="e2ee-verify-dialog">
    <div class="w-full max-w-md rounded-xl bg-white p-5 shadow-xl">
      <h2 class="text-base font-semibold">安全码核验</h2>
      <p class="mt-1 text-sm text-gray-500">请和 {{ peerName || '对方' }} 通过其他渠道对照这组 60 位安全码。一致后再标记为已验证。</p>
      <p v-if="warning" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
        对方公钥已变化，之前的验证已失效。请重新核对。
      </p>
      <div class="mt-4 space-y-2 text-xs text-gray-600">
        <p>我的指纹：<span class="break-all font-mono text-gray-900">{{ e2ee.publicProfile?.fingerprint || '生成中' }}</span></p>
        <p>对方指纹：<span class="break-all font-mono text-gray-900">{{ peer?.fingerprint || '对方尚未上传公钥' }}</span></p>
      </div>
      <p class="mt-4 whitespace-pre-wrap break-all rounded-md bg-slate-50 px-3 py-3 text-center font-mono text-sm tracking-wide text-slate-800" data-testid="e2ee-safety-number">
        {{ loading ? '正在计算安全码…' : (safety || '暂无安全码') }}
      </p>
      <div class="mt-4 flex justify-end gap-2">
        <Button variant="outline" type="button" @click="emit('close')">关闭</Button>
        <Button type="button" :disabled="!peer || verified" @click="markVerified">
          {{ verified ? '已验证' : '标记为已验证' }}
        </Button>
      </div>
    </div>
  </div>
</template>
