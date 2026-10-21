<script setup lang="ts">
import { Mic, Send, X } from 'lucide-vue-next'
import { onBeforeUnmount, ref } from 'vue'
import { toast } from 'vue-sonner'
import { formatVoiceClock, VOICE_MAX_MS } from '@/utils/media-message'
import { microphoneErrorMessage, VoiceRecorder, type RecordedVoice } from '@/utils/voice-recorder'

const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ recorded: [voice: RecordedVoice] }>()

/** 按住超过该时长视为“按住说话”，松开即发送；更短的点击进入“点击录音”模式 */
const HOLD_THRESHOLD_MS = 450

const state = ref<'idle' | 'requesting' | 'recording'>('idle')
const elapsed = ref(0)
const holdMode = ref(false)
const pointerOutside = ref(false)
let pressedAt = 0
let pressing = false

const recorder = new VoiceRecorder(
  ms => { elapsed.value = ms },
  () => {
    toast.info('已达到最长 60 秒，自动发送')
    void send()
  },
)

async function begin() {
  if (props.disabled || state.value !== 'idle') return
  state.value = 'requesting'
  elapsed.value = 0
  try {
    await recorder.start()
    state.value = recorder.state === 'recording' ? 'recording' : 'idle'
  } catch (error) {
    state.value = 'idle'
    holdMode.value = false
    toast.error(microphoneErrorMessage(error))
  }
}

async function send() {
  if (state.value === 'idle') return
  holdMode.value = false
  try {
    const voice = await recorder.stop()
    if (voice) emit('recorded', voice)
  } catch (error) {
    toast.warning((error as Error).message || '录音失败')
  } finally {
    state.value = 'idle'
    elapsed.value = 0
  }
}

function cancel() {
  holdMode.value = false
  recorder.cancel()
  state.value = 'idle'
  elapsed.value = 0
}

// 按下即开始录音；按住超过阈值后松开 = 发送（松开时手指已移出按钮 = 取消），短按 = 进入点击录音模式
async function onPointerDown(event: PointerEvent) {
  if (event.button !== 0 || state.value !== 'idle') return
  pressing = true
  pressedAt = performance.now()
  pointerOutside.value = false
  ;(event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId)
  await begin()
  if (pressing && (state.value as string) === 'recording') holdMode.value = true
}

function onPointerMove(event: PointerEvent) {
  if (!pressing || !holdMode.value) return
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const margin = 40
  pointerOutside.value = event.clientX < rect.left - margin || event.clientX > rect.right + margin
    || event.clientY < rect.top - margin || event.clientY > rect.bottom + margin
}

function onPointerUp() {
  if (!pressing) return
  pressing = false
  const held = performance.now() - pressedAt
  if (state.value !== 'recording') {
    holdMode.value = false
    return
  }
  if (held >= HOLD_THRESHOLD_MS && holdMode.value) {
    if (pointerOutside.value) cancel()
    else void send()
  } else {
    // 短按：保持录音，用“发送/取消”按钮结束
    holdMode.value = false
  }
  pointerOutside.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (state.value === 'idle') void begin()
  }
}

onBeforeUnmount(() => recorder.dispose())

defineExpose({ cancel, send, state })
</script>

<template>
  <div class="flex items-center">
    <button
      type="button"
      class="voice-button h-[40px] flex items-center justify-center hover:text-blue-400 disabled:opacity-40"
      :class="state !== 'idle' ? 'text-red-500' : ''"
      title="语音（点击录音，或按住说话、松开发送）"
      data-testid="voice-record-button"
      :disabled="disabled && state === 'idle'"
      :data-state="state"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @keydown="onKeydown"
      @contextmenu.prevent
    >
      <Mic style="width: 27px; height: 27px" />
    </button>
      <div
        v-if="state !== 'idle'"
        class="voice-panel absolute inset-x-0 bottom-full z-40 mx-2 mb-1 flex items-center gap-3 rounded-lg border bg-white px-3 py-2 shadow-lg"
        data-testid="voice-recording-panel"
        role="status"
      >
        <span class="relative flex h-3 w-3 shrink-0">
          <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
          <span class="relative inline-flex h-3 w-3 rounded-full bg-red-500" />
        </span>
        <span class="text-sm tabular-nums text-slate-700" data-testid="voice-timer">
          {{ state === 'requesting' ? '请求麦克风…' : `${formatVoiceClock(elapsed || 1)} / ${formatVoiceClock(VOICE_MAX_MS)}` }}
        </span>
        <div class="h-1.5 min-w-10 flex-1 overflow-hidden rounded bg-slate-200">
          <div class="h-full bg-red-400 transition-all" :style="{ width: `${Math.min(100, (elapsed / VOICE_MAX_MS) * 100)}%` }" />
        </div>
        <span v-if="holdMode" class="hidden text-xs text-slate-500 sm:inline" data-testid="voice-hold-hint">
          {{ pointerOutside ? '松开取消' : '松开发送，移开取消' }}
        </span>
        <template v-else>
          <button
            type="button"
            class="flex items-center gap-1 rounded px-2 py-1 text-sm text-slate-600 hover:bg-slate-100"
            data-testid="voice-cancel"
            @click="cancel"
          >
            <X class="h-4 w-4" /> 取消
          </button>
          <button
            type="button"
            class="flex items-center gap-1 rounded bg-blue-500 px-2 py-1 text-sm text-white hover:bg-blue-600 disabled:opacity-50"
            data-testid="voice-send"
            :disabled="state !== 'recording'"
            @click="send"
          >
            <Send class="h-4 w-4" /> 发送
          </button>
        </template>
      </div>
  </div>
</template>

<style scoped>
.voice-button {
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
</style>
