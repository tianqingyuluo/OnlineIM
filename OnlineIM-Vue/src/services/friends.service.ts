import api from './api.service';
import type { FormContext } from 'vee-validate';
import { useListStore } from '@/stores/list';
import {type FriendRequestsResponse, type FriendsResponse} from "@/type/Friends.ts";


export const friendsService = {
  async getFriends(
    formContext?: FormContext
  ): Promise<FriendsResponse> {
    try {
      const response = await api.get<FriendsResponse>('/friends');
      const listStore = useListStore();
      listStore.friends = response.data.friends;
      listStore.friendTotal = response.data.total;
      return response.data;
    } catch (error: any) {
      if (formContext && error.response?.data?.errors) {
        formContext.setErrors(error.response.data.errors);
      }
      throw error;
    }
  },

  // 删除好友
  async deleteFriend(friendUserId: string) {
    try {
      await api.delete(`/friends/${friendUserId}`);
    } catch (error) {
      console.error('删除好友失败:', error);
      throw error;
    }
  },

  // 设置好友备注
  async setFriendRemark(friendshipId: string, remark: string) {
    try {
      const response = await api.put(`/friends/${friendshipId}/remark`, { remark });
      return response.data;
    } catch (error) {
      console.error('设置好友备注失败:', error);
      throw error;
    }
  },

  // 更新好友分组
  async setFriendGroup(friendId: string, friendGroupId: string): Promise<{friendship_id: string, friendship_group_id: string}> {
    try {
      const response = await api.put(`/api/v1/friends/${friendId}/group`, {
        friend_group_id: friendGroupId
      });
      return response.data;
    } catch (error) {
      console.error('更新好友分组失败:', error);
      throw error;
    }
  },


  // 获取收到的好友请求列表
  async getReceivedFriendRequests(params?: {
    offset?: number;
  }): Promise<FriendRequestsResponse> {
    try {
      const response = await api.get<FriendRequestsResponse>('/friends/requests/received', { params });
      return response.data;
    } catch (error) {
      console.error('获取好友请求列表失败:', error);
      throw error;
    }
  },
  async sendFriendRequest(
      data:{receiver_id: string, message?: string}) {

    try {
      const response = await api.post(`/api/v1/friends/request/send`, data);
      return response.data;
    } catch (error) {
      console.error('添加好友请求发出失败:', error);
      throw error;
    }
  },

};