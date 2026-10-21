<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Check, Search, X } from 'lucide-vue-next'
import { useListStore } from '@/stores/list'
import {
  FORWARD_MAX_TARGETS,
  filterForwardTargets,
  type ForwardMode,
  type ForwardTargetOption,
} from '@/utils/chat-record'

const props = withDefaults(defineProps<{
  open: boolean
  mode: ForwardMode
  messageCount: number
  busy?: boolean
}>(), {
  busy: false,
})

const emit = defineEmits<{
  close: []
  confirm: [targets: string[]]
}>()

const listStore = useListStore()
const query = ref('')
const selected = ref<string[]>([])
const searchRef = ref<HTMLInputElement | null>(null)

/** 好友（备注优先）与已加入的群 */
const options = computed<ForwardTargetOption[]>(() => {
  const friends: ForwardTargetOption[] = (listStore.friends || [])
    .map(friend => friend.friend_info)
    .filter(info => !!info?.user_id)
    .map(info => {
      const label = info.remark || info.nickname || info.username || info.user_id
      return {
        id: info.user_id,
        kind: 'friend' as const,
        label,
        sublabel: label !== (info.nickname || info.username) ? info.nickname || info.username : undefined,
        avatar_url: info.avatar_url,
        keywords: [info.remark, info.nickname, info.username].filter(Boolean).join(' ').toLowerCase(),
      }
    })
  const groups: ForwardTargetOption[] = (listStore.groups || [])
    .filter(group => !!group?.group_id)
    .map(group => ({
      id: group.group_id,
      kind: 'group' as const,
      label: group.name || group.group_id,
      sublabel: group.member_count ? `${group.member_count} 人` : undefined,
      avatar_url: group.avatar_url || '/images/group.png',
      keywords: (group.name || '').toLowerCase(),
    }))
  return [...friends, ...groups]
})

const filtered = computed(() => filterForwardTargets(options.value, query.value))
const friendOptions = computed(() => filtered.value.filter(option => option.kind === 'friend'))
const groupOptions = computed(() => filtered.value.filter(option => option.kind === 'group'))
const selectedOptions = computed(() =>
  selected.value.map(id => options.value.find(option => option.id === id)).filter(Boolean) as ForwardTargetOption[])
const atLimit = computed(() => selected.value.length >= FORWARD_MAX_TARGETS)
const title = computed(() => (props.mode === 'merged' ? '合并转发' : props.messageCount > 1 ? '逐条转发' : '转发'))

function toggle(id: string) {
  if (selected.value.includes(id)) {
    selected.value = selected.value.filter(item => item !== id)
  } else if (!atLimit.value) {
    selected.value = [...selected.value, id]
  }
}

function confirm() {
  if (!selected.value.length || props.busy) return
  emit('confirm', [...selected.value])
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) {
    event.stopPropagation()
    emit('close')
  }
}

watch(() => props.open, async value => {
  if (typeof window === 'undefined') return
  if (value) {
    query.value = ''
    selected.value = []
    window.addEventListener('keydown', onKeydown, true)
    await nextTick()
    // 手机上不自动弹出键盘
    if (window.matchMedia?.('(pointer: fine)').matches) searchRef.value?.focus()
  } else {
    window.removeEventListener('keydown', onKeydown, true)
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKeydown, true)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[70] flex items-stretch justify-center bg-black/40 sm:items-center"
      @click.self="emit('close')"
    >
      <div
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        data-testid="forward-picker"
        :data-mode="mode"
        class="flex h-full w-full flex-col bg-white sm:h-[70vh] sm:max-w-md sm:rounded-xl sm:shadow-xl"
      >
        <div class="flex items-center justify-between border-b px-4 py-3">
          <div class="min-w-0">
            <div class="font-medium">{{ title }}</div>
            <div class="text-xs text-slate-400">
              {{ messageCount }} 条消息 · 已选 {{ selected.length }}/{{ FORWARD_MAX_TARGETS }} 个会话
            </div>
          </div>
          <button
            type="button"
            aria-label="关闭"
            data-testid="forward-picker-close"
            class="flex h-8 w-8 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100"
            @click="emit('close')"
          >
            <X class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <div class="border-b px-4 py-2">
          <label class="flex items-center gap-2 rounded-lg bg-slate-100 px-3 py-2">
            <Search class="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
            <input
              ref="searchRef"
              v-model="query"
              data-testid="forward-search"
              type="search"
              placeholder="搜索好友或群聊"
              class="min-w-0 flex-1 bg-transparent text-sm outline-none"
            />
          </label>
          <div v-if="selectedOptions.length" class="mt-2 flex flex-wrap gap-1.5" data-testid="forward-selected">
            <button
              v-for="option in selectedOptions"
              :key="option.id"
              type="button"
              class="flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-700"
              @click="toggle(option.id)"
            >
              {{ option.label }}
              <X class="h-3 w-3" aria-hidden="true" />
            </button>
          </div>
        </div>

        <div class="flex-1 overflow-y-auto px-2 py-2">
          <template v-for="section in [{ name: '好友', items: friendOptions }, { name: '群聊', items: groupOptions }]" :key="section.name">
            <div v-if="section.items.length" class="px-2 pb-1 pt-2 text-xs text-slate-400">{{ section.name }}</div>
            <button
              v-for="option in section.items"
              :key="option.id"
              type="button"
              role="checkbox"
              data-testid="forward-target"
              :data-target-id="option.id"
              :aria-checked="selected.includes(option.id)"
              :disabled="!selected.includes(option.id) && atLimit"
              class="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left hover:bg-slate-50 disabled:opacity-50"
              @click="toggle(option.id)"
            >
              <span
                :class="[
                  'flex h-5 w-5 shrink-0 items-center justify-center rounded-full border',
                  selected.includes(option.id) ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-300',
                ]"
              >
                <Check v-if="selected.includes(option.id)" class="h-3 w-3" aria-hidden="true" />
              </span>
              <img
                :src="option.avatar_url || '/images/default-avatar.png'"
                alt=""
                class="h-9 w-9 shrink-0 rounded-full object-cover"
              />
              <span class="min-w-0 flex-1">
                <span class="block truncate text-sm">{{ option.label }}</span>
                <span v-if="option.sublabel" class="block truncate text-xs text-slate-400">{{ option.sublabel }}</span>
              </span>
            </button>
          </template>
          <div v-if="!filtered.length" class="py-10 text-center text-sm text-slate-400" data-testid="forward-empty">
            没有匹配的好友或群聊
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 border-t px-4 py-3">
          <button
            type="button"
            class="rounded-lg px-4 py-2 text-sm text-slate-600 hover:bg-slate-100"
            @click="emit('close')"
          >取消</button>
          <button
            type="button"
            data-testid="forward-confirm"
            :disabled="!selected.length || busy"
            class="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white disabled:opacity-50"
            @click="confirm"
          >{{ busy ? '发送中...' : `发送${selected.length ? `（${selected.length}）` : ''}` }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
