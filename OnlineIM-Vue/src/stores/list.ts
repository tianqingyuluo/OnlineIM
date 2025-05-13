import { defineStore } from 'pinia';
import {  friendsService} from '@/services/friends.service';
import {type Friend}from'@/type/Friends.ts'
import { conversationService} from '@/services/conversation.service';
import {type Conversation}from'@/type/Conversation.ts'
import { groupService } from '@/services/group.service';
import type {GroupResponse} from "@/type/group.ts";

export const useListStore = defineStore('list', {
  state: () => ({
    conversations: [] as Conversation[],//全会话列表
    total: 0,
    friends: [] as Friend[],//全好友列表
    friendTotal: 0,
    groups: [] as GroupResponse[],//全群组列表
    groupTotal: 0,

  }),
  actions: {
    async fetchUserData() {
      await conversationService.getConversations()
      await friendsService.getFriends()
      await groupService.getJoinedGroups()
    },
  },
  persist: true
})