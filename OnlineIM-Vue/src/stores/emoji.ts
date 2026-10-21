import { defineStore } from 'pinia'
import { toast } from 'vue-sonner'
import {
  deleteEmoji,
  listEmojis,
  reorderEmojis,
  saveEmojiFromMessage,
  uploadEmoji,
} from '@/services/emoji.service'
import {
  DEFAULT_EMOJI_LIMITS,
  moveToFront,
  validateEmojiFile,
  type CustomEmoji,
  type EmojiLimits,
} from '@/utils/sticker-message'
import { uploadErrorMessage } from '@/utils/media-message'

/** api 拦截器已对 400/403 弹过提示，这里只补充其他状态（404/409/413/415/网络等） */
function notifyError(error: unknown, fallback: string) {
  const status = (error as { status?: number })?.status
  if (status === 400 || status === 403 || status === 0) return
  const message = uploadErrorMessage(error)
  toast.error(message === '上传失败，请重试' ? fallback : message)
}

/** 我的表情：面板与“添加到表情”共用，跨组件保持同一份列表 */
export const useEmojiStore = defineStore('emoji', {
  state: () => ({
    emojis: [] as CustomEmoji[],
    limits: { ...DEFAULT_EMOJI_LIMITS } as EmojiLimits,
    loaded: false,
    loading: false,
    adding: false,
  }),
  getters: {
    isFull: state => state.emojis.length >= state.limits.max_count,
  },
  actions: {
    async load(force = false) {
      if (this.loading || (this.loaded && !force)) return
      this.loading = true
      try {
        const data = await listEmojis()
        this.emojis = data.emojis || []
        this.limits = {
          max_count: data.max_count ?? DEFAULT_EMOJI_LIMITS.max_count,
          max_size: data.max_size ?? DEFAULT_EMOJI_LIMITS.max_size,
          max_dimension: data.max_dimension ?? DEFAULT_EMOJI_LIMITS.max_dimension,
          formats: data.formats ?? DEFAULT_EMOJI_LIMITS.formats,
        }
        this.loaded = true
      } catch (error) {
        notifyError(error, '表情加载失败')
      } finally {
        this.loading = false
      }
    },

    fullMessage(): string {
      return `表情数量已达上限（${this.limits.max_count} 个），请先删除一些再添加`
    },

    async add(file: File): Promise<CustomEmoji | null> {
      const invalid = validateEmojiFile(file, this.limits)
      if (invalid) {
        toast.error(invalid)
        return null
      }
      if (this.loaded && this.isFull) {
        toast.error(this.fullMessage())
        return null
      }
      this.adding = true
      try {
        const emoji = await uploadEmoji(file)
        this.emojis.unshift(emoji)
        toast.success('已添加到表情')
        return emoji
      } catch (error) {
        notifyError(error, '添加表情失败')
        return null
      } finally {
        this.adding = false
      }
    },

    async saveFromMessage(conversationId: string, messageId: string): Promise<CustomEmoji | null> {
      if (!messageId || messageId.startsWith('local_')) {
        toast.info('消息发送成功后才能添加到表情')
        return null
      }
      try {
        const emoji = await saveEmojiFromMessage(conversationId, messageId)
        if (this.loaded) this.emojis.unshift(emoji)
        toast.success('已添加到表情')
        return emoji
      } catch (error) {
        notifyError(error, '添加表情失败')
        return null
      }
    },

    async remove(emojiId: string) {
      const index = this.emojis.findIndex(emoji => emoji.emoji_id === emojiId)
      if (index < 0) return
      const [removed] = this.emojis.splice(index, 1)
      try {
        await deleteEmoji(emojiId)
      } catch (error) {
        const status = (error as { status?: number })?.status
        if (status !== 404) {
          this.emojis.splice(index, 0, removed)
          notifyError(error, '删除表情失败')
        }
      }
    },

    async moveToFront(emojiId: string) {
      const previous = this.emojis.slice()
      const order = moveToFront(previous.map(emoji => emoji.emoji_id), emojiId)
      const byId = new Map(previous.map(emoji => [emoji.emoji_id, emoji]))
      this.emojis = order.map(id => byId.get(id)!).filter(Boolean)
      try {
        const data = await reorderEmojis(order)
        this.emojis = data.emojis || this.emojis
      } catch (error) {
        this.emojis = previous
        notifyError(error, '调整顺序失败')
        // 列表可能与服务端不一致（例如另一端增删过），重新拉取
        void this.load(true)
      }
    },

    reset() {
      this.emojis = []
      this.loaded = false
    },
  },
})
