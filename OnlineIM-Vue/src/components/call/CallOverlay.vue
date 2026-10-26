<script setup lang="ts">
import { Mic, MicOff, Phone, PhoneOff, Video, VideoOff, Minimize2, Maximize2 } from 'lucide-vue-next'
import { nextTick, onMounted, ref, watch } from 'vue'
import { bindCallSignaling, useCallStore } from '@/stores/call.store'
import { formatCallDuration } from '@/utils/call-message'

const call = useCallStore()
const remoteVideo = ref<HTMLVideoElement | null>(null)
const localVideo = ref<HTMLVideoElement | null>(null)
const remoteAudio = ref<HTMLAudioElement | null>(null)

onMounted(() => {
  bindCallSignaling()
  attachStreams()
})

watch(() => [call.localStream, call.remoteStream, call.phase, call.minimized], async () => {
  await nextTick()
  attachStreams()
})

function attachStreams() {
  if (remoteVideo.value) remoteVideo.value.srcObject = call.remoteStream
  if (localVideo.value) localVideo.value.srcObject = call.localStream
  if (remoteAudio.value) remoteAudio.value.srcObject = call.remoteStream
}

const durationText = () => formatCallDuration(call.durationSeconds)
</script>

<template>
  <div v-if="call.phase !== 'idle'" class="pointer-events-none fixed inset-0 z-[80]" data-testid="call-overlay" :data-phase="call.phase" :data-minimized="call.minimized ? 'true' : 'false'">
    <div
      v-if="call.phase === 'incoming'"
      class="pointer-events-auto absolute inset-0 flex items-center justify-center bg-slate-900/45 p-4"
      data-testid="call-incoming"
    >
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 text-center shadow-xl">
        <img v-if="call.peerAvatar" :src="call.peerAvatar" alt="" class="mx-auto h-16 w-16 rounded-full object-cover" />
        <div v-else class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-slate-100 text-slate-500">
          <Video v-if="call.callType === 'video'" class="h-7 w-7" />
          <Phone v-else class="h-7 w-7" />
        </div>
        <div class="mt-3 text-lg font-semibold text-slate-900">{{ call.peerName }}</div>
        <div class="mt-1 text-sm text-slate-500">{{ call.callType === 'video' ? '邀请你视频通话' : '邀请你语音通话' }}</div>
        <div class="mt-6 flex justify-center gap-8">
          <button type="button" data-testid="call-reject" class="flex h-14 w-14 items-center justify-center rounded-full bg-red-500 text-white" @click="call.rejectIncoming()">
            <PhoneOff class="h-6 w-6" />
          </button>
          <button type="button" data-testid="call-accept" class="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-500 text-white" @click="call.acceptIncoming()">
            <Phone class="h-6 w-6" />
          </button>
        </div>
      </div>
    </div>

    <div
      v-else-if="call.phase === 'active' && call.minimized"
      class="pointer-events-auto absolute bottom-4 right-4 flex items-center gap-3 rounded-full bg-slate-900 px-3 py-2 text-white shadow-xl"
      data-testid="call-minimized"
    >
      <span class="max-w-28 truncate text-sm">{{ call.peerName }}</span>
      <span data-testid="call-duration" class="text-sm tabular-nums">{{ durationText() }}</span>
      <button type="button" data-testid="call-expand" class="rounded-full p-1 hover:bg-white/10" @click="call.toggleMinimize()">
        <Maximize2 class="h-4 w-4" />
      </button>
      <button type="button" data-testid="call-hangup" class="rounded-full bg-red-500 p-2" @click="call.hangup()">
        <PhoneOff class="h-4 w-4" />
      </button>
    </div>

    <div
      v-else
      class="pointer-events-auto absolute bottom-4 right-4 w-[min(100vw-2rem,22rem)] overflow-hidden rounded-2xl bg-slate-900 text-white shadow-2xl"
      data-testid="call-panel"
    >
      <div class="relative bg-slate-800">
        <video
          v-if="call.phase === 'active' && call.callType === 'video'"
          ref="remoteVideo"
          autoplay
          playsinline
          class="h-56 w-full bg-black object-cover"
          data-testid="call-remote-video"
        />
        <div v-else class="flex h-40 flex-col items-center justify-center gap-2">
          <img v-if="call.peerAvatar" :src="call.peerAvatar" alt="" class="h-16 w-16 rounded-full object-cover" />
          <Video v-else-if="call.callType === 'video'" class="h-8 w-8 text-slate-300" />
          <Phone v-else class="h-8 w-8 text-slate-300" />
          <div class="text-base font-medium">{{ call.peerName }}</div>
          <div class="text-sm text-slate-300">
            {{ call.phase === 'outgoing' ? '正在呼叫…' : call.phase === 'connecting' ? '正在接通…' : call.callType === 'video' ? '视频通话' : '语音通话' }}
          </div>
        </div>
        <video
          v-if="call.phase === 'active' && call.callType === 'video' && call.cameraOn"
          ref="localVideo"
          autoplay
          playsinline
          muted
          class="absolute bottom-3 right-3 h-20 w-28 rounded-lg border border-white/20 object-cover"
          data-testid="call-local-video"
        />
        <audio ref="remoteAudio" autoplay data-testid="call-remote-audio" />
      </div>
      <div class="flex items-center justify-between px-4 py-3">
        <span v-if="call.phase === 'active'" data-testid="call-duration" class="text-sm tabular-nums">{{ durationText() }}</span>
        <span v-else class="text-sm text-slate-300">{{ call.callType === 'video' ? '视频通话' : '语音通话' }}</span>
        <div class="flex items-center gap-2">
          <template v-if="call.phase === 'active'">
            <button type="button" data-testid="call-mute" class="rounded-full bg-white/10 p-2" :data-muted="call.muted ? 'true' : 'false'" @click="call.toggleMute()">
              <MicOff v-if="call.muted" class="h-4 w-4" />
              <Mic v-else class="h-4 w-4" />
            </button>
            <button
              v-if="call.callType === 'video'"
              type="button"
              data-testid="call-camera"
              class="rounded-full bg-white/10 p-2"
              :data-camera="call.cameraOn ? 'on' : 'off'"
              @click="call.toggleCamera()"
            >
              <VideoOff v-if="!call.cameraOn" class="h-4 w-4" />
              <Video v-else class="h-4 w-4" />
            </button>
            <button type="button" data-testid="call-minimize" class="rounded-full bg-white/10 p-2" @click="call.toggleMinimize()">
              <Minimize2 class="h-4 w-4" />
            </button>
            <button type="button" data-testid="call-hangup" class="rounded-full bg-red-500 p-2" @click="call.hangup()">
              <PhoneOff class="h-4 w-4" />
            </button>
          </template>
          <button v-else type="button" data-testid="call-cancel" class="rounded-full bg-red-500 px-3 py-2 text-sm" @click="call.cancelOutgoing()">
            取消
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
