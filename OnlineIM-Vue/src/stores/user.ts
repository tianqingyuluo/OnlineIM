import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    selectedUser: null,
    loggedInUser: null
  }),
  actions: {
    setLoggedInUser(user) {
      this.loggedInUser = user;
    },
    setSelectedUser(user) {
      this.selectedUser = user
    }
  }
})