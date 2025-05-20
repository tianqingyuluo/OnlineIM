import api from './api.service';
import type { FormContext } from 'vee-validate';
import {
    type GroupMembersResponse,
    type GroupResponse,
    type GroupSearchResponse,
    type JoinedGroupsResponse
} from '@/type/group.ts'
import { useListStore } from '@/stores/list';
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
      response.data={
          ...response.data,
          my_role:"owner"
      }
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
  async getGroupInfo(groupId: string): Promise<GroupResponse> {
    try {
      const response = await api.get<GroupResponse>(`/groups/${groupId}`);
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
      const response = await api.get<GroupMembersResponse>(`/groups/${groupId}/members`, {
        params
      });
      return response.data;
    } catch (error) {
      console.error('获取群组成员列表失败:', error);
      throw error;
    }
  },
  // 获取已加入的群组列表
  async getJoinedGroups(): Promise<JoinedGroupsResponse> {
    try {
      const response = await api.get<JoinedGroupsResponse>('/groups/joined');
      useListStore().groups =[...useListStore().groups,...response.data.groups];
      useListStore().groupTotal =useListStore().groupTotal+ response.data.total;
      return response.data;
    } catch (error) {
      console.error('获取已加入群组失败:', error);
      throw error;
    }
  },

  // 更新群成员昵称
  async updateMemberNickname(
    groupId: string,
    memberId: string,
    nickname: string
  ) {
    try {
      const response = await api.put(`/groups/${groupId}/members/${memberId}/nickname`, {
        nickname
      });
      return response.data;
    } catch (error) {
      console.error('更新群成员昵称失败:', error);
      throw error;
    }
  },

};