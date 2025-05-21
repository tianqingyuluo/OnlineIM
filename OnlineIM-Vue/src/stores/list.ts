import { defineStore } from 'pinia';
import {  friendsService} from '@/services/friends.service';
import { conversationService} from '@/services/conversation.service';
import {type Conversation}from'@/type/Conversation.ts'
import { groupService } from '@/services/group.service';
import type {GroupJoinRequestResponse, GroupResponse} from "@/type/group.ts";
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
    groupedFriends: [] as GroupedFriends[],
    groupJoinRequestList:[] as GroupJoinRequestResponse[],
  }),
  actions: {
    async fetchUserData() {
      if (this.userGroups.length === 0) {
        this.userGroups=await friendGroupsService.getFriendGroups()
      }
      if (this.conversations.length === 0) {
        await conversationService.getConversations()
      }
      if (this.friends.length === 0) {
        await friendsService.getFriends()
      }
      if (this.groupedFriends.length === 0) {
        this.groupedFriends =await groupAndSortFriends(this.friends, this.userGroups)
      }
      if (this.groups.length === 0) {
        await groupService.getJoinedGroups()
      }
      if (this.groupJoinRequestList.length === 0) {
        await this.getGroupJoinRequestList()
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
    // 在 useListStore 的 actions 中添加这个方法
async getGroupJoinRequestList() {
  try {
    // 临时存储所有请求，避免重复添加
    const allRequests: GroupJoinRequestResponse[] = [];
    
    // 遍历用户的所有群组
    for (const group of this.groups) {
      // 只处理角色不是 'member' 的群组（管理员或群主）
      if (group.my_role && group.my_role !== 'member') {
        try {
          // 获取该群组的加群请求
          const requests = await groupService.getGroupJoinRequests(group.group_id);
          
          // 如果返回的是数组，直接合并
          if (Array.isArray(requests)) {
            allRequests.push(...requests);
          } 
          // 如果返回的是单个对象，包装成数组
          else if (typeof requests === 'object' && requests !== null) {
            allRequests.push(requests);
          }
        } catch (error) {
          console.error(`获取群组 ${group.name} 的加群请求失败:`, error);
          // 继续处理下一个群组，不中断整个流程
          continue;
        }
      }
    }
    this.groupJoinRequestList = allRequests;
    return allRequests;
  } catch (error) {
    console.error('获取加群请求列表失败:', error);
    throw error;
    }
  },
},
  persist: true,
});