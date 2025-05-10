import api from './api.service';
import type { FormContext } from 'vee-validate';
import {type User,type UserSearchResponse } from "@/type/User.ts";



export const searchService = {
  async searchUsers(
    query: string,
    params?: {
      limit?: number;
      offset?: number;
    },
    formContext?: FormContext
  ): Promise<UserSearchResponse> {
    try {
      const response = await api.get<UserSearchResponse>(`/users/search/${encodeURIComponent(query)}`, {
        params
      });
      return response.data;
    } catch (error: any) {
      if (formContext && error.response?.data?.errors) {
        formContext.setErrors(error.response.data.errors);
      }
      throw error;
    }
  }
};

export const userService = {
  async getUserById(userId: string) {
    try {
      const response = await api.get<User>(`/users/${userId}`);
      return response.data;
    } catch (error) {
      console.error('获取用户信息失败:', error);
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
  async setFriendRemark(friendUserId: string, remark: string) {
    try {
      const response = await api.put(`/friends/${friendUserId}/remark`, { remark });
      return response.data;
    } catch (error) {
      console.error('设置好友备注失败:', error);
      throw error;
    }
  }
};