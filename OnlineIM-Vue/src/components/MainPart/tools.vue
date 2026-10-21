<script setup lang="ts">
import { Smile, Image as ImageIcon, Paperclip, X } from "lucide-vue-next"
import { ref } from "vue"
import { toast } from "vue-sonner"
import EmojiArea from "@/components/independent/emojiArea/EmojiArea.vue"
import VoiceRecordButton from "@/components/MainPart/VoiceRecordButton.vue"
import type { RecordedVoice } from "@/utils/voice-recorder"
import { uploadChatMedia } from "@/services/upload.service"
import {
  IMAGE_ACCEPT,
  uploadErrorMessage,
  validateBeforeUpload,
  type ChatMedia,
  type ChatMediaKind,
} from "@/utils/media-message"

const emojiAreaRef = ref<InstanceType<typeof EmojiArea> | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const uploading = ref<{ kind: ChatMediaKind; name: string; percent: number } | null>(null)
let uploadController: AbortController | null = null

const emit = defineEmits<{
  select: [emoji: string]
  uploaded: [kind: ChatMediaKind, media: ChatMedia]
}>()

function handleEmojiSelect(emoji: string) {
  emit('select', emoji)
}

function toggleEmojiArea(e?: MouseEvent) {
  emojiAreaRef.value?.toggleEmojiArea(e)
}

function pick(kind: ChatMediaKind) {
  if (uploading.value) {
    toast.info('请等待当前文件上传完成')
    return
  }
  const input = kind === 'image' ? imageInputRef.value : fileInputRef.value
  input?.click()
}

async function handleFileChange(kind: ChatMediaKind, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  await upload(kind, file)
}

async function handleVoiceRecorded(voice: RecordedVoice) {
  await upload('voice', voice.file, voice.durationMs)
}

async function upload(kind: ChatMediaKind, file: File, durationMs?: number) {
  const invalid = validateBeforeUpload(kind, file)
  if (invalid) {
    toast.error(invalid)
    return
  }
  uploadController = new AbortController()
  uploading.value = { kind, name: file.name, percent: 0 }
  try {
    const media = await uploadChatMedia(kind, file, {
      signal: uploadController.signal,
      durationMs,
      onProgress: percent => {
        if (uploading.value) uploading.value.percent = percent
      },
    })
    emit('uploaded', kind, media)
  } catch (error) {
    const status = (error as { status?: number })?.status
    // 400/403 已由 api 拦截器统一提示，这里只补充其他错误
    if (status !== 400 && status !== 403) toast.error(uploadErrorMessage(error))
  } finally {
    uploading.value = null
    uploadController = null
  }
}

function cancelUpload() {
  uploadController?.abort()
}

defineExpose({ upload })
</script>

<template>
  <!-- 新增工具栏 -->
  <div class="flex items-center justify-between px-4 py-3.5 border-t relative" style="height: 40px">
    <div class="flex items-center space-x-2">
      <div class="h-[40px] flex items-center justify-center hover:text-blue-400 cursor-pointer" title="表情" @click="toggleEmojiArea">
        <Smile style="width: 30px; height: 30px" />
      </div>
      <button
        type="button"
        class="h-[40px] flex items-center justify-center hover:text-blue-400 disabled:opacity-40"
        title="发送图片"
        data-testid="send-image-button"
        :disabled="!!uploading"
        @click="pick('image')"
      >
        <ImageIcon style="width: 28px; height: 28px" />
      </button>
      <button
        type="button"
        class="h-[40px] flex items-center justify-center hover:text-blue-400 disabled:opacity-40"
        title="发送文件"
        data-testid="send-file-button"
        :disabled="!!uploading"
        @click="pick('file')"
      >
        <Paperclip style="width: 27px; height: 27px" />
      </button>
      <VoiceRecordButton :disabled="!!uploading" @recorded="handleVoiceRecorded" />
      <input
        ref="imageInputRef"
        type="file"
        class="hidden"
        :accept="IMAGE_ACCEPT"
        data-testid="image-input"
        @change="handleFileChange('image', $event)"
      />
      <input
        ref="fileInputRef"
        type="file"
        class="hidden"
        data-testid="file-input"
        @change="handleFileChange('file', $event)"
      />
    </div>
    <div
      v-if="uploading"
      class="flex min-w-0 flex-1 items-center justify-end gap-2 pl-4 text-xs text-slate-500"
      data-testid="upload-progress"
    >
      <span class="truncate max-w-[40%]">{{ uploading.kind === 'image' ? '图片' : uploading.kind === 'voice' ? '语音' : '文件' }} {{ uploading.kind === 'voice' ? '' : uploading.name }}</span>
      <div class="h-1.5 w-28 shrink-0 overflow-hidden rounded bg-slate-200">
        <div class="h-full bg-blue-500 transition-all" :style="{ width: `${uploading.percent}%` }" />
      </div>
      <span class="w-9 shrink-0 text-right">{{ uploading.percent }}%</span>
      <button type="button" class="shrink-0 hover:text-red-500" title="取消上传" @click="cancelUpload">
        <X class="h-4 w-4" />
      </button>
    </div>
    <transition name="fade-slide" mode="out-in">
    <EmojiArea ref="emojiAreaRef" @select="handleEmojiSelect" />
    </transition>
  </div>
</template>
<style scoped>
/* 统一过渡效果 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 拖动光标反馈 */
.cursor-move {
  cursor: move;
  user-select: none;
}
</style>
