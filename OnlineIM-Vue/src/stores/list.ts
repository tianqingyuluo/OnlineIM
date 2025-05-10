import { defineStore } from 'pinia';
import {  friendsService} from '@/services/friends.service';
import {type Friend}from'@/type/Friends.ts'
import { conversationService} from '@/services/conversation.service';
import {type Conversation}from'@/type/Conversation.ts'
import { type UserSearchResult} from '@/type/User.ts'
import type {GroupSearchResult} from "@/type/group.ts";
// import {groupService} from "@/services/group.service.ts";
export const useListStore = defineStore('list', {
  state: () => ({
    conversations: [] as Conversation[],//全会话列表
    total: 0,
    friends: [] as Friend[],//全好友列表
    friendTotal: 0,
    searchResults: [] as UserSearchResult[],//查询结果（好友）
    searchResultsTotal: 0,
    groupSearchResults:[] as GroupSearchResult[],
    groupSearchResultsTotal: 0,

  }),
  actions: {
    async fetchUserData() {
      await conversationService.getConversations()
      await friendsService.getFriends()
    },
    async clearSearchResults() {
      this.searchResults=[] as UserSearchResult[]
      this.searchResultsTotal=0
    }
  },
  persist: true
})