import api from './api.service';
import type { FormContext } from 'vee-validate';

export interface BlacklistUser {
  user_id: string;
  nickname: string;
  avatar_url?: string;
  added_at: string;
}

export interface BlacklistResponse {
  blacklist: BlacklistUser[];
  total: number;
}

export const blacklistService = {
  // 添加用户到黑名单
  async addToBlacklist(
      targetUserId: string,
      formContext?: FormContext
  ): Promise<void> {
    try {
      await api.put(`friends/${targetUserId}/block`);
    } catch (error: any) {
      if (formContext && error.response?.data?.errors) {
        formContext.setErrors(error.response.data.errors);
      }
      throw error;
    }
  },

  // 从黑名单移除用户
  async removeFromBlacklist(
      targetUserId: string,
      formContext?: FormContext
  ): Promise<void> {
    try {
      await api.delete(`friends/${targetUserId}/block`);
    } catch (error: any) {
      if (formContext && error.response?.data?.errors) {
        formContext.setErrors(error.response.data.errors);
      }
      throw error;
    }
  },

  // 获取黑名单列表
  async getBlacklist(
      params?: {
        limit?: number;
        offset?: number;
      },
      formContext?: FormContext
  ): Promise<BlacklistResponse> {
    try {
      const response = await api.get<BlacklistResponse>('friends/blacklist', {
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