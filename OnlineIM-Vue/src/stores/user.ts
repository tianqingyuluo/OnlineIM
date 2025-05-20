import { defineStore } from 'pinia'
import {meService} from "@/services/me.service.ts";
import { useListStore } from '@/stores/list'
import {type User,} from '@/type/User.ts'
import {authService} from '@/services/auth.service.ts';
import type {Conversation} from "@/type/Conversation.ts";
import type {Friend} from "@/type/Friends.ts";
import { TokenService } from '@/services/token.service'
import type {GroupResponse} from "@/type/group.ts";

// 默认用户对象（所有字段为空值）
const DEFAULT_USER: User = {
  user_id: '',
  username: '未登录用户',
  nickname: '游客',
  avatar_url: '/images/group.png',
  region: undefined,
  gender: 0,
  email: undefined,
  phone: undefined,
  signature: '这个用户很懒，什么都没写~',
  created_at: undefined
}

export const useUserStore = defineStore('user', {
  state: () => ({
    loggedInUser: JSON.parse(JSON.stringify(DEFAULT_USER)) as User,
    token: "",
  }),
  persist: true, // 启用持久化存储
  actions: {
    setLoggedInUser(user: User) {
      this.loggedInUser = user
    },
    async clearUser() {
      this.loggedInUser = JSON.parse(JSON.stringify(DEFAULT_USER))
      this.token = ""
      const listStore = useListStore()
      listStore.conversations = [] as Conversation[]
      listStore.total = 0
      listStore.friends = [] as Friend[]
      listStore.friendTotal = 0
      listStore.groups = [] as GroupResponse[]
      listStore.groupTotal = 0
      listStore.groupedFriends=[]
      listStore.userGroups=[]
      await authService.logout()
      TokenService.clear()
    },
    isAuthenticated(): boolean {//是否拥有token
      return !!this.token
    },
    async fetchUserData() {
      if (!this.loggedInUser.user_id) {
        await meService.me();
      }
    },
    async updateToken() {
      TokenService.init(this.token)
    },
  }
})