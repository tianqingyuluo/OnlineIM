<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { groupAnnouncementService } from '@/services/announcement.service'
import { useUserStore } from '@/stores/user'
import type { GroupAnnouncement, GroupAnnouncementReads } from '@/type/groupAnnouncement'
import { isGroupManager } from '@/utils/mention'
import {
  applyAnnouncementFrame,
  onAnnouncementFrame,
  pickUnreadAnnouncement,
} from '@/utils/announcement'

const props = defineProps<{
  groupId: string
  myRole?: string
}>()

const userStore = useUserStore()
const items = ref<GroupAnnouncement[]>([])
const popupOpen = ref(false)
const opened = ref<GroupAnnouncement | null>(null)
const busy = ref(false)
const reads = ref<GroupAnnouncementReads | null>(null)
const manager = computed(() => isGroupManager(props.myRole))
const current = computed(() => pickUnreadAnnouncement(items.value))
const shown = computed(() => opened.value || current.value)

async function load() {
  const groupId = props.groupId
  try {
    const list = await groupAnnouncementService.getAnnouncementsByGroupId(groupId)
    if (groupId === props.groupId) items.value = list
  } catch (error) {
    const status = (error as { status?: number })?.status
    if (groupId === props.groupId) items.value = status === 404 ? [] : items.value
  }
}

async function loadReads(announcementId: string) {
  if (!manager.value) {
    reads.value = null
    return
  }
  try {
    const detail = await groupAnnouncementService.getAnnouncementReads(props.groupId, announcementId)
    if (popupOpen.value && shown.value?.announcement_id === announcementId) reads.value = detail
  } catch (error) {
    console.error('获取公告已读名单失败:', error)
  }
}

function applyRead(updated: GroupAnnouncement) {
  const next = { ...updated, read_by_me: true as const }
  items.value = items.value.map(item => item.announcement_id === updated.announcement_id ? { ...item, ...next } : item)
  if (opened.value?.announcement_id === updated.announcement_id) opened.value = { ...opened.value, ...next }
}

function openPopup() {
  const announcement = current.value
  if (!announcement) return
  opened.value = announcement
  popupOpen.value = true
  void loadReads(announcement.announcement_id)
  if (!announcement.read_by_me) void markViewed(announcement)
}

async function markViewed(announcement: GroupAnnouncement) {
  try {
    const updated = await groupAnnouncementService.markAnnouncementRead(props.groupId, announcement.announcement_id)
    applyRead(updated)
  } catch (error) {
    console.error('记录公告已读失败:', error)
  }
}

function closePopup() {
  popupOpen.value = false
  opened.value = null
  reads.value = null
}

async function confirmRead() {
  const announcement = shown.value
  if (!announcement || busy.value) return
  if (announcement.read_by_me) {
    closePopup()
    return
  }
  busy.value = true
  try {
    const updated = await groupAnnouncementService.markAnnouncementRead(props.groupId, announcement.announcement_id)
    applyRead(updated)
    reads.value = null
    if (!pickUnreadAnnouncement(items.value)) closePopup()
    else if (manager.value && shown.value) void loadReads(shown.value.announcement_id)
  } catch (error) {
    toast.error((error as { message?: string })?.message || '确认已读失败')
  } finally {
    busy.value = false
  }
}

const stop = onAnnouncementFrame(frame => {
  if (frame.message?.group_id && frame.message.group_id !== props.groupId) return
  const selfId = userStore.loggedInUser?.user_id
  const applied = applyAnnouncementFrame(items.value, frame, selfId)
  items.value = applied.items
  if (applied.openPopup) {
    popupOpen.value = true
    const unread = pickUnreadAnnouncement(items.value)
    if (unread) void loadReads(unread.announcement_id)
  }
  if (opened.value) {
    const fresh = items.value.find(item => item.announcement_id === opened.value?.announcement_id)
    if (fresh) opened.value = fresh
  }
  const readAnnouncementId = frame.message?.announcement_id
  if (frame.type === 'GROUP_ANNOUNCEMENT_READ' && manager.value && popupOpen.value
    && readAnnouncementId && shown.value?.announcement_id === readAnnouncementId) {
    void loadReads(readAnnouncementId)
  }
})

watch(() => props.groupId, () => {
  closePopup()
  void load()
}, { immediate: true })

onBeforeUnmount(stop)
</script>

<template>
  <div v-if="current || popupOpen" class="border-b border-amber-200 bg-amber-50" data-testid="announcement-bar">
    <button
      v-if="current"
      type="button"
      class="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-amber-900"
      data-testid="announcement-bar-open"
      @click="openPopup"
    >
      <span class="shrink-0 rounded bg-amber-200 px-1.5 py-0.5 text-xs font-medium">群公告</span>
      <span class="min-w-0 flex-1 truncate">{{ current.title }}</span>
      <span class="shrink-0 text-xs text-amber-700">查看</span>
    </button>

    <div
      v-if="popupOpen && shown"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      data-testid="announcement-popup"
      @click.self="closePopup"
    >
      <div class="max-h-[80vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-5 shadow-xl" role="dialog" aria-label="群公告">
        <div class="mb-3 flex items-start justify-between gap-3">
          <h3 class="text-lg font-semibold text-slate-900" data-testid="announcement-popup-title">{{ shown.title }}</h3>
          <button type="button" class="text-slate-400" aria-label="关闭公告" @click="closePopup">×</button>
        </div>
        <p class="whitespace-pre-wrap text-sm leading-6 text-slate-700">{{ shown.content }}</p>
        <p v-if="manager && (shown.read_count != null || reads)" class="mt-3 text-xs text-slate-500" data-testid="announcement-read-counts">
          已读 {{ reads?.read_count ?? shown.read_count ?? 0 }} · 未读 {{ reads?.unread_count ?? shown.unread_count ?? 0 }}
        </p>
        <div v-if="manager && reads" class="mt-3 grid gap-3 text-xs text-slate-600 sm:grid-cols-2" data-testid="announcement-read-lists">
          <div>
            <div class="mb-1 font-medium text-slate-800">已读</div>
            <p v-if="!reads.readers.length" class="text-slate-400">还没有人确认</p>
            <p v-for="reader in reads.readers" :key="reader.user_id">{{ reader.nickname || reader.username || reader.user_id }}</p>
          </div>
          <div>
            <div class="mb-1 font-medium text-slate-800">未读</div>
            <p v-if="!reads.unread_users.length" class="text-slate-400">全部已读</p>
            <p v-for="reader in reads.unread_users" :key="reader.user_id">{{ reader.nickname || reader.username || reader.user_id }}</p>
          </div>
        </div>
        <div class="mt-5 flex justify-end gap-2">
          <button type="button" class="rounded-md px-3 py-1.5 text-sm text-slate-600" @click="closePopup">稍后</button>
          <button
            type="button"
            class="rounded-md bg-amber-500 px-3 py-1.5 text-sm text-white disabled:opacity-60"
            data-testid="announcement-confirm"
            :disabled="busy"
            @click="confirmRead"
          >我知道了</button>
        </div>
      </div>
    </div>
  </div>
</template>
