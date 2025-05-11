import api from './api.service';
import type { FormContext } from 'vee-validate';
import { useListStore } from '@/stores/list';
import {type FriendsResponse} from "@/type/Friends.ts";


export const friendsService = {
  async getFriends(
    params?: {
      limit?: number;
      offset?: number;
    },
    formContext?: FormContext
  ): Promise<FriendsResponse> {
    try {
      const response = await api.get<FriendsResponse>('/friends', {
        params
      });
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


  // 获取收到的好友请求列表
  async getReceivedFriendRequests(params?: {
    limit?: number;
    offset?: number;
  }) {
    try {
      const response = await api.get('/friends/requests/received', { params });
      return response.data;
    } catch (error) {
      console.error('获取好友请求列表失败:', error);
      throw error;
    }
  },

};