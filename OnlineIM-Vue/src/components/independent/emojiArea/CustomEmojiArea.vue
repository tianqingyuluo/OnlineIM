<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowUpToLine, LoaderCircle, Plus, X } from 'lucide-vue-next'
import { useEmojiStore } from '@/stores/emoji'
import { EMOJI_ACCEPT, type CustomEmoji } from '@/utils/sticker-message'
import { authorizeChatFileUrl } from '@/utils/chat-file-url'

const emit = defineEmits<{ send: [emoji: CustomEmoji] }>()

const emojiStore = useEmojiStore()
const managing = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const maxGifMb = computed(() => Math.round(emojiStore.limits.max_size / 1024 / 1024))

onMounted(() => {
  void emojiStore.load()
})

function pickFile() {
  if (emojiStore.adding) return
  fileInputRef.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) await emojiStore.add(file)
}

function handleClick(emoji: CustomEmoji) {
  if (managing.value) return
  emit('send', emoji)
}
</script>

<template>
  <div class="flex h-full flex-col" data-testid="custom-emoji-area">
    <div class="flex items-center justify-between px-1 pb-2 text-sm text-slate-600">
      <span>
        我的表情
        <span class="text-xs text-slate-400" data-testid="emoji-count">{{ emojiStore.emojis.length }}/{{ emojiStore.limits.max_count }}</span>
      </span>
      <button
        v-if="emojiStore.emojis.length"
        type="button"
        class="rounded px-2 py-0.5 text-xs text-blue-600 hover:bg-blue-50"
        data-testid="emoji-manage-toggle"
        @click="managing = !managing"
      >
        {{ managing ? '完成' : '管理' }}
      </button>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto pr-1">
      <div class="grid grid-cols-[repeat(auto-fill,minmax(68px,1fr))] gap-2">
        <button
          v-if="!managing"
          type="button"
          class="flex aspect-square items-center justify-center rounded-lg border border-dashed border-slate-300 text-slate-400 hover:border-blue-400 hover:text-blue-500 disabled:opacity-50"
          :title="emojiStore.isFull ? `已达上限 ${emojiStore.limits.max_count} 个` : '添加表情'"
          :disabled="emojiStore.adding"
          data-testid="emoji-add-button"
          @click="pickFile"
        >
          <LoaderCircle v-if="emojiStore.adding" class="h-6 w-6 animate-spin" />
          <Plus v-else class="h-7 w-7" />
        </button>
        <div
          v-for="(emoji, index) in emojiStore.emojis"
          :key="emoji.emoji_id"
          class="group relative aspect-square"
        >
          <button
            type="button"
            :class="[
              'flex h-full w-full items-center justify-center rounded-lg p-1 transition-colors',
              managing ? 'cursor-default ring-1 ring-slate-200' : 'hover:bg-slate-100',
            ]"
            :title="emoji.name"
            data-testid="custom-emoji"
            :data-emoji-id="emoji.emoji_id"
            @click="handleClick(emoji)"
          >
            <img :src="authorizeChatFileUrl(emoji.url)" :alt="emoji.name" class="max-h-full max-w-full object-contain" referrerpolicy="no-referrer" loading="lazy" draggable="false" />
          </button>
          <template v-if="managing">
            <button
              type="button"
              class="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-red-500 text-white shadow hover:bg-red-600"
              title="删除"
              aria-label="删除表情"
              data-testid="emoji-delete"
              @click="emojiStore.remove(emoji.emoji_id)"
            >
              <X class="h-3.5 w-3.5" />
            </button>
            <button
              v-if="index > 0"
              type="button"
              class="absolute -left-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-slate-700 text-white shadow hover:bg-slate-900"
              title="移到最前"
              aria-label="移到最前"
              data-testid="emoji-move-front"
              @click="emojiStore.moveToFront(emoji.emoji_id)"
            >
              <ArrowUpToLine class="h-3 w-3" />
            </button>
          </template>
        </div>
      </div>
      <p v-if="emojiStore.loaded && !emojiStore.emojis.length" class="px-1 pt-3 text-xs leading-5 text-slate-400" data-testid="emoji-empty">
        还没有表情。点 + 上传图片，或在聊天里右键 / 长按图片、表情选择“添加到表情”。
      </p>
      <p class="px-1 pt-3 text-[11px] text-slate-400">
        支持 PNG、JPG、GIF（动图不超过 {{ maxGifMb }}MB），最多 {{ emojiStore.limits.max_count }} 个
      </p>
    </div>
    <input
      ref="fileInputRef"
      type="file"
      class="hidden"
      :accept="EMOJI_ACCEPT"
      data-testid="emoji-upload-input"
      @change="handleFileChange"
    />
  </div>
</template>
