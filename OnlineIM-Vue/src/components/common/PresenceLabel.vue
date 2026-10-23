<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { usePresenceStore } from '@/stores/presence'
import { formatPresence } from '@/utils/presence'

/** “刚刚 / N 分钟前”按墙钟变化，页面停着也要往前走。 */
const RELATIVE_TIME_INTERVAL_MS = 30_000

const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | null = null
let listeners = 0

function tick() {
  now.value = Date.now()
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') tick()
}

function retainClock() {
  listeners += 1
  if (listeners !== 1) return
  tick()
  timer = setInterval(tick, RELATIVE_TIME_INTERVAL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
}

function releaseClock() {
  listeners -= 1
  if (listeners > 0) return
  listeners = 0
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
}

const props = defineProps<{
  userId?: string | null
}>()

const presenceStore = usePresenceStore()
const record = computed(() => (props.userId ? presenceStore.byUserId[props.userId] : undefined))
const text = computed(() => formatPresence(record.value, now.value))
const online = computed(() => record.value?.visible !== false && record.value?.status === 'online')

// setup 里就对齐一次，第一次渲染不要用模块加载时的旧时间
retainClock()
onUnmounted(releaseClock)
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
