import { defineStore } from 'pinia'

export const useOtherStore = defineStore('other', {
  state: () => ({
    isContextMenuOpen: false
  }),
  actions: {
    setContextMenuOpen(isOpen: boolean) {
      console.log("setContextMenuOpen 触发了事件", isOpen)
      this.isContextMenuOpen = isOpen
    }
  }
})