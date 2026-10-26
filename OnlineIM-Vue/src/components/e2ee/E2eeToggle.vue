<script setup lang="ts">
import { Switch } from '@/components/ui/switch'

defineProps<{
  enabled: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:enabled': [value: boolean]
}>()
</script>

<template>
  <div class="flex items-center justify-between gap-3 py-2" data-testid="e2ee-toggle">
    <div class="min-w-0">
      <div class="text-sm text-gray-800">{{ enabled ? '关闭端到端加密' : '开启端到端加密' }}</div>
      <p class="text-xs text-gray-500">仅加密文本和回复。图片、文件、表情仍按原方式发送。</p>
    </div>
    <Switch
      :model-value="enabled"
      :disabled="disabled"
      aria-label="开启或关闭端到端加密"
      @update:model-value="emit('update:enabled', $event === true)"
    />
  </div>
</template>
