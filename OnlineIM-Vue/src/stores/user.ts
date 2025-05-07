import { defineStore } from 'pinia'
interface User {
  user_id: string; // 根据 API 规范，使用 user_id 而不是 userId
  username: string;
  nickname: string;
  avatar_url: string | null; // 根据 API 规范，使用 avatar_url 而不是 avatarUrl
  token: string;
  region?: string; // 可选字段
  gender?: 'male' | 'female'; // 可选字段
  email?: string; // 可选字段
  phone?: string; // 可选字段
  signature?: string; // 可选字段
  created_at?: string; // 可选字段，ISO 8601 格式
}

export const useUserStore = defineStore('user', {
  state: () => ({
    selectedUser: null,
    loggedInUser: null
  }),
  persist: true, // 启用持久化
  actions: {
    setLoggedInUser(user) {
      this.loggedInUser = user;
    },
    setSelectedUser(user) {
      this.selectedUser = user
    },
    clearUser() {
      this.loggedInUser = null;
    }
  }
})