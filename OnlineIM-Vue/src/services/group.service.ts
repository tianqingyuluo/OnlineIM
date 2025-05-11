import api from './api.service';
import type { FormContext } from 'vee-validate';
import {type GroupSearchResponse}from'@/type/group.ts'
export const groupService = {
  // 创建群组
  async createGroup(
    data: {
      name: string;
      avatar_url?: string;
      description?: string;
      initial_members?: string[];
    },
    formContext?: FormContext
  ) {
    try {
      const response = await api.post('/groups', data);
      return response.data;
    } catch (error: any) {
      if (formContext && error.response?.data?.errors) {
        formContext.setErrors(error.response.data.errors);
      }
      throw error;
    }
  },

  // 搜索群组
    async searchGroups(
        query: string,
        page: number,
        params?: {
            limit?: number;
            offset?: number;
        },
        formContext?: FormContext
    ): Promise<GroupSearchResponse> {
        try {
            const response = await api.get<GroupSearchResponse>(`/groups/search/${encodeURIComponent(query)}/${page}`, {
                params
            });

            return response.data;
        } catch (error: any) {
            if (formContext && error.response?.data?.errors) {
                formContext.setErrors(error.response.data.errors);
            }
            console.error('搜索群组失败:', error);
            throw error;
        }
    },
  // 获取群组信息
  async getGroupInfo(groupId: string) {
    try {
      const response = await api.get(`/groups/${groupId}`);
      return response.data;
    } catch (error) {
      console.error('获取群组信息失败:', error);
      throw error;
    }
  },

  // 获取群组成员列表
  async getGroupMembers(
    groupId: string,
    params?: {
      limit?: number;
      offset?: number;
    }
  )
   {
    try {
      const response = await api.get(`/groups/${groupId}/members`, {
        params
      });
      return response.data;
    } catch (error) {
      console.error('获取群组成员列表失败:', error);
      throw error;
    }
  }
};