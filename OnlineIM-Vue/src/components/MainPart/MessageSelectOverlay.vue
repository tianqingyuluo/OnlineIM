<script setup lang="ts">
import { Check } from 'lucide-vue-next'

/** 多选模式下覆盖在一条消息上：点击整行切换选中，左侧显示勾选圈 */
defineProps<{
  selected: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()
</script>

<template>
  <button
    type="button"
    data-testid="select-message"
    :aria-pressed="selected"
    :aria-label="disabled ? '这条消息不能转发' : selected ? '取消选择' : '选择这条消息'"
    :disabled="disabled"
    :class="[
      'absolute inset-0 z-20 flex items-center rounded-xl pl-1 text-left transition-colors disabled:cursor-not-allowed',
      selected ? 'bg-blue-500/[0.06]' : 'hover:bg-slate-500/[0.04]',
    ]"
    @click.stop="emit('toggle')"
  >
    <span
      :class="[
        'flex h-5 w-5 shrink-0 items-center justify-center rounded-full border',
        selected ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-300 bg-white',
        disabled ? 'opacity-40' : '',
      ]"
    >
      <Check v-if="selected" class="h-3 w-3" aria-hidden="true" />
    </span>
  </button>
</template>
