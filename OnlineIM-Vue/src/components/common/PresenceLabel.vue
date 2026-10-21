<script setup lang="ts">
import { computed } from 'vue'
import { usePresenceStore } from '@/stores/presence'
import { formatPresence } from '@/utils/presence'

const props = defineProps<{
  userId?: string | null
}>()

const presenceStore = usePresenceStore()
const record = computed(() => (props.userId ? presenceStore.byUserId[props.userId] : undefined))
const text = computed(() => formatPresence(record.value))
const online = computed(() => record.value?.visible !== false && record.value?.status === 'online')
</script>

<template>
  <span
    class="inline-flex items-center text-[13px]"
    :class="online ? 'text-green-600' : 'text-gray-500'"
    :data-online="online ? 'true' : 'false'"
  >
    <span
      class="mr-1 inline-block h-2 w-2 rounded-full"
      :class="online ? 'bg-green-500' : 'bg-gray-300'"
      aria-hidden="true"
    />
    {{ text }}
  </span>
</template>
