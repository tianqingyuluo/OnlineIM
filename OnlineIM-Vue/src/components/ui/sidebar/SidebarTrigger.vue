<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { ArrowLeftToLine, ArrowRightToLine } from "lucide-vue-next"
import { useSidebar } from './utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()

const { state, toggleSidebar } = useSidebar()
</script>

<template>
  <Button
      data-sidebar="trigger"
      data-slot="sidebar-trigger"
      variant="ghost"
      size="icon"
      :class="cn('h-7 w-7', props.class)"
      @click="toggleSidebar"
  >
    <ArrowLeftToLine v-if="state === 'expanded'" />
    <ArrowRightToLine v-else />
    <span class="sr-only">Toggle Sidebar</span>
  </Button>
</template>