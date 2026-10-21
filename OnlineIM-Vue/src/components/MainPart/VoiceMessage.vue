<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref } from 'vue'
import { toast } from 'vue-sonner'
import { formatVoiceClock, formatVoiceSeconds, type ChatMedia } from '@/utils/media-message'

const props = defineProps<{ media: ChatMedia; isSelf?: boolean }>()

const playing = ref(false)
const loading = ref(false)
const positionMs = ref(0)
let audio: HTMLAudioElement | null = null
const self = { stop: () => pause() }

const totalMs = computed(() => props.media.duration_ms || (props.media.duration || 0) * 1000)
const progress = computed(() => (totalMs.value > 0 ? Math.min(1, positionMs.value / totalMs.value) : 0))
/** 气泡宽度随时长增长（类似常见 IM），上限避免手机窄屏溢出 */
const bubbleWidth = computed(() => {
  const seconds = Math.max(1, Math.round(totalMs.value / 1000))
  return `${Math.min(240, 120 + seconds * 2)}px`
})

function ensureAudio(): HTMLAudioElement {
  if (audio) return audio
  audio = new Audio()
  audio.preload = 'none'
  audio.src = props.media.url
  audio.addEventListener('timeupdate', () => {
    if (audio) positionMs.value = audio.currentTime * 1000
  })
  audio.addEventListener('playing', () => { loading.value = false; playing.value = true })
  audio.addEventListener('pause', () => { playing.value = false })
  audio.addEventListener('ended', () => {
    playing.value = false
    positionMs.value = 0
    if (activeVoice.value === self) activeVoice.value = null
  })
  audio.addEventListener('error', () => {
    loading.value = false
    playing.value = false
    toast.error('语音加载失败')
  })
  return audio
}

async function play() {
  const element = ensureAudio()
  // 同一时间只播放一条语音
  if (activeVoice.value && activeVoice.value !== self) activeVoice.value.stop()
  activeVoice.value = self
  loading.value = true
  try {
    await element.play()
  } catch (error) {
    loading.value = false
    playing.value = false
    if ((error as { name?: string })?.name !== 'AbortError') toast.error('语音播放失败')
  }
}

function pause() {
  audio?.pause()
  playing.value = false
  loading.value = false
}

function toggle() {
  if (playing.value || loading.value) pause()
  else void play()
}

function seek(event: MouseEvent) {
  const target = event.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
  const element = ensureAudio()
  const seconds = (ratio * totalMs.value) / 1000
  positionMs.value = ratio * totalMs.value
  try {
    element.currentTime = seconds
  } catch {
    // 元数据未加载时无法定位，播放后再拖动即可
  }
}

onBeforeUnmount(() => {
  pause()
  if (activeVoice.value === self) activeVoice.value = null
  if (audio) {
    audio.removeAttribute('src')
    audio.load()
    audio = null
  }
})
</script>

<script lang="ts">
import { shallowRef } from 'vue'
/** 模块级：当前播放中的语音（切换时暂停上一条） */
const activeVoice = shallowRef<{ stop: () => void } | null>(null)
</script>

<template>
  <div
    class="flex items-center gap-2 select-none"
    :class="isSelf ? 'flex-row-reverse' : ''"
    :style="{ width: bubbleWidth, maxWidth: '100%' }"
    data-testid="media-voice"
    :data-state="playing ? 'playing' : loading ? 'loading' : 'paused'"
  >
    <button
      type="button"
      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-blue-500 text-white hover:bg-blue-600"
      :title="playing ? '暂停' : '播放语音'"
      data-testid="voice-play"
      @click="toggle"
    >
      <Pause v-if="playing || loading" class="h-4 w-4" />
      <Play v-else class="ml-0.5 h-4 w-4" />
    </button>
    <div
      class="relative h-1.5 min-w-8 flex-1 cursor-pointer rounded bg-slate-300/70"
      data-testid="voice-progress"
      :data-progress="progress.toFixed(3)"
      @click="seek"
    >
      <div class="absolute inset-y-0 left-0 rounded bg-blue-500" :style="{ width: `${progress * 100}%` }" />
    </div>
    <span class="shrink-0 text-xs tabular-nums text-slate-500" data-testid="voice-duration" :title="formatVoiceClock(totalMs)">
      {{ playing || positionMs > 0 ? formatVoiceClock(positionMs || 1) : formatVoiceSeconds(totalMs) }}
    </span>
  </div>
</template>
