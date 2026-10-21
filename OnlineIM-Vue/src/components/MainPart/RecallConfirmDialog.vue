<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  open: boolean
  busy?: boolean
}>()

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()

const confirmRef = ref<HTMLButtonElement | null>(null)

// 右键菜单关闭时会把焦点还给消息气泡，所以 Esc 监听挂在 document 上，
// 并在菜单收尾后再把焦点放到“撤回”按钮。
watch(() => props.open, async open => {
  if (!open) {
    document.removeEventListener('keydown', handleKeydown, true)
    return
  }
  document.addEventListener('keydown', handleKeydown, true)
  await nextTick()
  confirmRef.value?.focus()
  window.setTimeout(() => {
    if (props.open) confirmRef.value?.focus()
  }, 50)
}, { immediate: true })

onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown, true))

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    event.preventDefault()
    if (!props.busy) emit('cancel')
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[80] flex items-center justify-center bg-black/40 px-6"
      data-testid="recall-confirm-overlay"
      @click.self="emit('cancel')"
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="recall-confirm-title"
        data-testid="recall-confirm"
        class="w-full max-w-xs rounded-2xl bg-white p-5 shadow-xl"
      >
        <h2 id="recall-confirm-title" class="text-base font-semibold text-slate-900">撤回这条消息？</h2>
        <p class="mt-2 text-sm leading-5 text-slate-500">撤回后双方都看不到这条消息的内容，聊天里会显示“撤回了一条消息”。</p>
        <div class="mt-5 flex justify-end gap-2">
          <button
            type="button"
            data-testid="recall-cancel"
            class="rounded-lg px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-400/40"
            :disabled="busy"
            @click="emit('cancel')"
          >取消</button>
          <button
            ref="confirmRef"
            type="button"
            data-testid="recall-confirm-button"
            class="rounded-lg bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600 disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-400/50"
            :disabled="busy"
            @click="emit('confirm')"
          >{{ busy ? '撤回中…' : '撤回' }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
