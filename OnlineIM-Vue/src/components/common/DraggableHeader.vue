<template>
  <div
      ref="headerRef"
      class="flex items-center h-12 w-full bg-gray-100  cursor-move rounded-t-lg "
      @mousedown="startDrag"
  >
    <slot></slot>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'

const emit = defineEmits(['drag'])

const headerRef = ref<HTMLElement | null>(null)
let isDragging = false
let initialX = 0
let initialY = 0

const startDrag = (e: MouseEvent) => {
  isDragging = true
  initialX = e.clientX
  initialY = e.clientY
  document.addEventListener('mousemove', drag)
  document.addEventListener('mouseup', stopDrag)
  document.addEventListener('mouseleave', stopDrag) // 新增边界情况处理
}

const drag = (e: MouseEvent) => {
  if (!isDragging) return
  e.preventDefault()
  const deltaX = e.clientX - initialX
  const deltaY = e.clientY - initialY
  initialX = e.clientX
  initialY = e.clientY
  emit('drag', { deltaX, deltaY })
}

const stopDrag = () => {
  isDragging = false
  document.removeEventListener('mousemove', drag)
  document.removeEventListener('mouseup', stopDrag)
  document.removeEventListener('mouseleave', stopDrag)
}

onUnmounted(stopDrag)
</script>