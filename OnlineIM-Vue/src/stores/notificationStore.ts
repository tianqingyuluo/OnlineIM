import { defineStore } from 'pinia';

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    hasnewfriend: false,
    hasnewgroup: false,
  }),
  getters: {
    hasnewall: (state) => state.hasnewfriend || state.hasnewgroup,
  },
  actions: {
    clear() {
      this.hasnewfriend = false;
      this.hasnewgroup = false;
    },
    readedfriend() {
      this.hasnewfriend = false;
    },
    readedgroup() {
      this.hasnewgroup = false;
    },
  },
  persist: true, // 添加这一行来启用持久化
});