import { defineStore } from 'pinia';
import {  friendsService} from '@/services/friends.service';
import { conversationService} from '@/services/conversation.service';
import {type Conversation}from'@/type/Conversation.ts'
import { groupService } from '@/services/group.service';
import type {GroupJoinRequestResponse, GroupResponse} from "@/type/group.ts";
import type {UserGroupInfo} from "@/type/userGroup.ts";
import {friendGroupsService} from "@/services/friendGroups.servise.ts";
import type {Friend, FriendInFriendGroup} from "@/type/Friends.ts";
import { toast } from 'vue-sonner';
import { blacklistService, type BlacklistUser} from "@/services/blacklist.service.ts";
import {useUserStore} from "@/stores/user.ts";
import { dbService, STORES } from '@/utils/indexedDB';

export const useListStore = defineStore('list', {
  state: () => ({
    conversations: [] as Conversation[],//全会话列表
    total: 0,
    userGroups: [] as UserGroupInfo[],//全好友分组列表
    friends: [] as Friend[],
    friendTotal: 0,
    groups: [] as GroupResponse[],//全群组列表
    groupTotal: 0,
    groupJoinRequestList:[] as GroupJoinRequestResponse[],
    hasInit : false,
    blacklist: [] as BlacklistUser[],

  }),
  actions: {
    async fetchUserData() {
      const userStore = useUserStore();
      if (!userStore.token) {
        window.location.href = '/login';
        return;
      }
      
      if (!this.hasInit) {
        this.hasInit = false
        console.log('开始初始化用户数据...')
        console.log('当前用户ID:', userStore.loggedInUser.user_id)

        // 从IndexedDB加载缓存数据
        try {
          const userId = userStore.loggedInUser.user_id;
          const [dbConvs, dbFriends, dbGroups, dbUserGroups, dbBlacklist] = await Promise.all([
            dbService.getAll(STORES.CONVERSATIONS, userId),
            dbService.getAll(STORES.FRIENDS, userId),
            dbService.getAll(STORES.GROUPS,userId),
            dbService.getAll(STORES.USER_GROUPS, userId),
            dbService.getAll(STORES.BLACKLIST, userId)
          ]);

          if (dbConvs.length) {
            this.conversations = dbConvs;
            console.log('从IndexedDB加载会话数据:', dbConvs.length, '条记录');
            console.debug('会话数据详情:', dbConvs);
          }
          if (dbFriends.length) {
            this.friends = dbFriends;
            console.log('从IndexedDB加载好友数据:', dbFriends.length, '条记录');
            console.debug('好友数据详情:', dbFriends);
          }
          if (dbGroups.length) {
            this.groups = dbGroups;
            console.log('从IndexedDB加载群组数据:', dbGroups.length, '条记录');
            console.debug('群组数据详情:', dbGroups);
          }
          if (dbUserGroups.length) {
            this.userGroups = dbUserGroups;
            console.log('从IndexedDB加载好友分组数据:', dbUserGroups.length, '条记录');
            console.debug('好友分组数据详情:', dbUserGroups);
          }
          if (dbBlacklist.length) {
            this.blacklist = dbBlacklist;
            console.log('从IndexedDB加载黑名单数据:', dbBlacklist.length, '条记录');
            console.debug('黑名单数据详情:', dbBlacklist);
          }
        } catch (error) {
          console.error('从IndexedDB加载数据失败:', error);
        }

        // 获取最新数据并更新到IndexedDB
        try {
          const results = await Promise.allSettled([
            friendGroupsService.getFriendGroups(),
            conversationService.getConversations(),
            friendsService.getFriends(),
            groupService.getJoinedGroups(),
            blacklistService.getBlacklist()
          ]);
          
          // 处理每个请求的结果
          const [userGroupsResult, convsResult, friendsResult, groupsResult, blacklistResult] = results;
          
          // 记录失败的服务
          results.forEach((result, index) => {
            if (result.status === 'rejected') {
              const serviceNames = ['好友分组服务', '会话服务', '好友服务', '群组服务', '黑名单服务'];
              console.error(`${serviceNames[index]}加载失败:`, result.reason);
            }
          });

          // 更新成功返回的数据
          if (userGroupsResult.status === 'fulfilled') {
            this.userGroups = userGroupsResult.value;
            await dbService.bulkPut(STORES.USER_GROUPS, this.userGroups);
            for (const group of this.userGroups) {
              for (const friend of group.friends) {
                const url = friend.avatar_url;
                if (url) {
                  try {
                    await dbService.addImageFromUrl(url);
                  } catch (error) {
                    console.error('缓存头像失败:', error);
                    console.error('错误的头像URL:', url);
                  }
                }
              }
            }
          }
          if (convsResult.status === 'fulfilled') {
            this.conversations = convsResult.value;
          }
          if (friendsResult.status === 'fulfilled') {
            this.friends = friendsResult.value.friends;
          }
          if (groupsResult.status === 'fulfilled') {
            this.groups = groupsResult.value;
            await dbService.bulkPut(STORES.GROUPS, this.groups);
            const groupAvatars = this.groups.map(group => group.avatar_url);
            for (const url of groupAvatars) {
              if (url) {
                try {
                  await dbService.addImageFromUrl(url);
                } catch (error) {
                  console.error('缓存头像失败:', error);
                  console.error('错误的头像URL:', url);
                  // 继续执行，不中断整个进程
                }
              }
            }
          }
          if (blacklistResult.status === 'fulfilled') {
            this.blacklist = blacklistResult.value.blacklist;
          }
          
          console.debug('API返回数据详情:', {
            userGroups: this.userGroups,
            conversations: this.conversations,
            friends: this.friends,
            groups: this.groups,
            blacklist: this.blacklist
          });

          // 同步到IndexedDB
          try {
            // 序列化数据，确保所有属性可克隆
            const serializeData = (data: any) => JSON.parse(JSON.stringify(data));
            
            // 验证数据有效性
            const isValidData = (data: any) => {
              return data !== undefined && data !== null && (Array.isArray(data) ? data.length > 0 : true);
            };
            
            await Promise.allSettled([
              isValidData(this.conversations) && dbService.bulkPut(STORES.CONVERSATIONS, serializeData(this.conversations)),
              isValidData(this.friends) && dbService.bulkPut(STORES.FRIENDS, serializeData(this.friends)),
              isValidData(this.groups) && dbService.bulkPut(STORES.GROUPS, serializeData(this.groups)),
              isValidData(this.userGroups) && dbService.bulkPut(STORES.USER_GROUPS, serializeData(this.userGroups)),
              isValidData(this.blacklist) && dbService.bulkPut(STORES.BLACKLIST, serializeData(this.blacklist))
            ]);
          } catch (error) {
            console.error('IndexedDB存储失败:', error);
            throw error;
          }

          await this.getGroupJoinRequestList();
          await useUserStore().updateToken();
        } catch (error) {
          console.error('获取最新数据失败:', error);
        }
      }



    },

    async deleteFriendGroup(groupId: string) {
      // 检查分组是否为空
      const userGroup = this.userGroups.find(g => g.group_id === groupId);
      if (userGroup && userGroup.friends.length > 0) {
        toast.error('分组不为空，无法删除');
        throw new Error('分组不为空，无法删除');
      }

      try {
        await friendGroupsService.deleteFriendGroup(groupId);
        // 从userGroups中删除
        const userGroupIndex = this.userGroups.findIndex(g => g.group_id === groupId);
        if (userGroupIndex !== -1) {
          this.userGroups.splice(userGroupIndex, 1);
          // 同步到IndexedDB
          await dbService.bulkPut(STORES.USER_GROUPS, this.userGroups);
        }
      } catch (error) {
        throw error;
      }
    },

    // 更新好友分组信息
    async updateFriendGroup(friendshipId: string, newGroupId: string) {
      // 更新friends数组中的好友分组信息
      const friendIndex = this.friends.findIndex(friend => friend.friendship_id === friendshipId);
      if (friendIndex !== -1) {
        this.friends[friendIndex].friend_info.friend_group_id = newGroupId;
      }

      // 更新userGroups数组中的好友分组信息
      const friend = this.friends.find(f => f.friendship_id === friendshipId);
      if (!friend) return;

      // 从原分组中移除好友
      for (const group of this.userGroups) {
        const index = group.friends.findIndex(f => f.friendship_id === friendshipId);
        if (index !== -1) {
          group.friends.splice(index, 1);
          break;
        }
      }

      // 将好友添加到新分组
      const newGroup = this.userGroups.find(g => g.group_id === newGroupId);
      if (newGroup) {
        const friendInGroup: FriendInFriendGroup = {
          friendship_id: friend.friendship_id,
          user_id: friend.friend_info.user_id,
          friend_group_id: newGroupId,
          created_at: friend.created_at,
          username: friend.friend_info.username,
          nickname: friend.friend_info.nickname,
          avatar_url: friend.friend_info.avatar_url,
          remark: friend.friend_info.remark,
          online_status: friend.friend_info.online_status
        };
        newGroup.friends.push(friendInGroup);
      }

      // 同步到IndexedDB
      await Promise.all([
        dbService.bulkPut(STORES.FRIENDS, this.friends),
        dbService.bulkPut(STORES.USER_GROUPS, this.userGroups)
      ]);
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