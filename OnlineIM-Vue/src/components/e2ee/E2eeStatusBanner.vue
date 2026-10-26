<script setup lang="ts">
import { computed } from 'vue'
import { Lock } from 'lucide-vue-next'

const props = defineProps<{
  enabled: boolean
  warning?: string | null
}>()

const text = computed(() => {
  if (!props.enabled) return ''
  if (props.warning) return '对方公钥已变化，请重新核对安全码'
  return '端到端加密已开启'
})
</script>

<template>
  <div
    v-if="enabled"
    data-testid="e2ee-status-banner"
    :class="[
      'mt-1 inline-flex max-w-full items-center gap-1 rounded-full px-2 py-0.5 text-xs',
      warning ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-700',
    ]"
  >
    <Lock class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
    <span class="truncate">🔒 {{ text }}</span>
  </div>
</template>
