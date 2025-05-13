import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useWidthStore = defineStore('width', () => {
  const sidebarWidth = ref(350)
  
  function setSidebarWidth(width: number) {
    sidebarWidth.value = Math.max(320, Math.min(500, width))
  }

  return { sidebarWidth, setSidebarWidth }
}, {
  persist: true
})