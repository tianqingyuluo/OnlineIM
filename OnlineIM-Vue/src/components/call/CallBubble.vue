<script setup lang="ts">
import { Phone, Video } from 'lucide-vue-next'
import { computed } from 'vue'
import { callPreviewText, callTypeOf } from '@/utils/call-message'

const props = defineProps<{
  content: string
}>()

const emit = defineEmits<{
  redial: [callType: 'video' | 'audio']
}>()

const callType = computed(() => callTypeOf(props.content))
const label = computed(() => callPreviewText(props.content))

function redial() {
  emit('redial', callType.value)
}
</script>

<template>
  <button
    type="button"
    data-testid="call-bubble"
    class="flex items-center gap-2 text-left text-[15px] leading-6 text-slate-800"
    @click="redial"
  >
    <Video v-if="callType === 'video'" class="h-4 w-4 shrink-0 text-blue-600" aria-hidden="true" />
    <Phone v-else class="h-4 w-4 shrink-0 text-blue-600" aria-hidden="true" />
    <span data-testid="call-bubble-text">{{ label }}</span>
  </button>
</template>
