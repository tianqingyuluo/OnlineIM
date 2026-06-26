<script setup lang="ts">
import { computed } from 'vue'
import { useWebSocketStore } from '@/stores/websocketStore'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'

const wsStore = useWebSocketStore()

const stateConfig = computed(() => {
  switch (wsStore.connectionState) {
    case 'online':
      return { color: 'bg-green-500', text: '在线' }
    case 'connecting':
      return { color: 'bg-blue-400 animate-pulse', text: '连接中…' }
    case 'reconnecting':
      return { color: 'bg-yellow-500 animate-pulse', text: '重连中…' }
    case 'offline':
      return { color: 'bg-gray-400', text: '离线·点击重试' }
    default:
      return { color: 'bg-gray-300', text: '未知' }
  }
})

const isOffline = computed(() => wsStore.connectionState === 'offline')

function handleClick() {
  if (isOffline.value) {
    wsStore.manualRetry()
  }
}
</script>

<template>
  <TooltipProvider>
    <Tooltip>
      <TooltipTrigger as-child>
        <button
          class="flex items-center justify-center w-full py-1"
          :class="{ 'cursor-pointer': isOffline }"
          @click="handleClick"
        >
          <span
            class="inline-block w-2.5 h-2.5 rounded-full transition-colors"
            :class="stateConfig.color"
          />
        </button>
      </TooltipTrigger>
      <TooltipContent side="right" :hide-delay="0">
        {{ stateConfig.text }}
      </TooltipContent>
    </Tooltip>
  </TooltipProvider>
</template>
