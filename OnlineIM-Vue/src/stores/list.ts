import { defineStore } from 'pinia';
import {  friendsService} from '@/services/friends.service';
import { conversationService} from '@/services/conversation.service';
import {type Conversation}from'@/type/Conversation.ts'
import { groupService } from '@/services/group.service';
import type {GroupResponse} from "@/type/group.ts";
import type {UserGroupInfo} from "@/type/userGroup.ts";
import {friendGroupsService} from "@/services/friendGroups.servise.ts";
import {type GroupedFriends, groupAndSortFriends } from '@/utils/friendGroupUtils';
import type {Friend} from "@/type/Friends.ts";
import { toast } from 'vue-sonner';

export const useListStore = defineStore('list', {
  state: () => ({
    conversations: [] as Conversation[],//全会话列表
    total: 0,
    userGroups: [] as UserGroupInfo[],//全好友列表
    friends: [] as Friend[],
    friendTotal: 0,
    groups: [] as GroupResponse[],//全群组列表
    groupTotal: 0,
    groupedFriends: [] as GroupedFriends[]
  }),
  actions: {
    async fetchUserData() {
      if (this.userGroups.length === 0) {
        this.userGroups = await friendGroupsService.getFriendGroups()
      }
      if (this.conversations.length === 0) {
        await conversationService.getConversations()
      }
      if (this.friends.length === 0) {
        await friendsService.getFriends()
      }
      if (this.groupedFriends.length === 0) {
        this.groupedFriends = await groupAndSortFriends(this.friends, this.userGroups)
      }
      if (this.groups.length === 0) {
        await groupService.getJoinedGroups()
      }
    },
    
    async deleteFriendGroup(groupId: string) {
      // 检查分组是否为空
      const groupIndex = this.groupedFriends.findIndex(g => g.group.id === groupId);
      if (groupIndex !== -1 && this.groupedFriends[groupIndex].friends.length > 0) {
        toast.error('分组不为空，无法删除');
        throw new Error('分组不为空，无法删除');
      }
      
      try {
        await friendGroupsService.deleteFriendGroup(groupId);
        
        // 从userGroups中删除
        const userGroupIndex = this.userGroups.findIndex(g => g.id === groupId);
        if (userGroupIndex !== -1) {
          this.userGroups.splice(userGroupIndex, 1);
        }
        
        // 从groupedFriends中删除
        if (groupIndex !== -1) {
          this.groupedFriends.splice(groupIndex, 1);
        }
      } catch (error) {
        throw error;
      }
    },
    
    // 更新好友分组信息，同时更新friends和groupedFriends数组
    updateFriendGroup(friendshipId: string, newGroupId: string) {
      // 更新friends数组中的好友分组信息
      const friendIndex = this.friends.findIndex(friend => friend.friendship_id === friendshipId);
      if (friendIndex !== -1) {
        this.friends[friendIndex].friend_info.friend_group_id = newGroupId;
      }
      
      // 更新groupedFriends数组
      // 1. 找到好友当前所在的分组
      let foundFriend: Friend | null = null;
      let oldGroupIndex = -1;
      let friendIndexInGroup = -1;
      
      for (let i = 0; i < this.groupedFriends.length; i++) {
        const groupFriends = this.groupedFriends[i].friends;
        const index = groupFriends.findIndex(f => f.friendship_id === friendshipId);
        if (index !== -1) {
          foundFriend = groupFriends[index];
          oldGroupIndex = i;
          friendIndexInGroup = index;
          break;
        }
      }
      
      // 如果找到了好友
      if (foundFriend) {
        // 2. 从旧分组中移除
        if (oldGroupIndex !== -1 && friendIndexInGroup !== -1) {
          this.groupedFriends[oldGroupIndex].friends.splice(friendIndexInGroup, 1);
        }
        
        // 3. 更新好友的分组ID
        foundFriend.friend_info.friend_group_id = newGroupId;
        
        // 4. 添加到新分组
        const newGroupIndex = this.groupedFriends.findIndex(g => g.group.id === newGroupId);
        if (newGroupIndex !== -1) {
          this.groupedFriends[newGroupIndex].friends.push(foundFriend);
        }
      } else {
        // 如果groupedFriends中没有找到，则重新生成groupedFriends
        groupAndSortFriends(this.friends, this.userGroups, true);
      }
    },
  },
  persist: true
})