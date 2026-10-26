<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { LOCKED_TEXT } from '@/utils/e2ee-message'

const emit = defineEmits<{ import: [] }>()
</script>

<template>
  <div class="max-w-sm rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700" data-testid="e2ee-locked-message">
    <p>{{ LOCKED_TEXT }}</p>
    <Button class="mt-2" size="sm" variant="outline" type="button" @click="emit('import')">导入密钥</Button>
  </div>
</template>
