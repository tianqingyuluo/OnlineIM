import { defineStore } from 'pinia'
import {meService} from "@/services/me.service.ts";
import { useListStore } from '@/stores/list'
import {type User, type UserSearchResult} from '@/type/User.ts'
import {authService} from '@/services/auth.service.ts';
import type {Conversation} from "@/type/Conversation.ts";
import type {Friend} from "@/type/Friends.ts";
// 默认用户对象（所有字段为空值）
const DEFAULT_USER: User = {
  user_id: '',
  username: '未登录用户',
  nickname: '游客',
  avatar_url: '/images/group.png',
  region: undefined,
  gender: undefined,
  email: undefined,
  phone: undefined,
  signature: '这个用户很懒，什么都没写~',
  created_at: undefined
}

export const useUserStore = defineStore('user', {
  state: () => ({

    // 当前登录用户（初始设为DEFAULT_USER的拷贝）
    loggedInUser: JSON.parse(JSON.stringify(DEFAULT_USER)) as User,
    token:""
  }),

  persist: true, // 启用持久化存储

  actions: {

    // 设置登录用户
    setLoggedInUser(user: User) {
      this.loggedInUser = user
    },
    

    // 清除当前登录用户（重置为默认用户）
     async clearUser() {
       // 清空当前store
       this.loggedInUser = JSON.parse(JSON.stringify(DEFAULT_USER))
       await authService.logout()
       this.token = ""
       // 清空list store
       const listStore = useListStore()
       listStore.conversations = [] as Conversation[]
       listStore.total = 0
       listStore.friends = [] as Friend[]
       listStore.friendTotal = 0
       listStore.searchResults = [] as UserSearchResult[]
       listStore.searchResultsTotal = 0
     },

    isAuthenticated(): boolean {//是否拥有token
      return !!this.token
    },
    async fetchUserData() {
      await meService.me();
    }

  }
})