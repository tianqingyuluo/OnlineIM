import api from './api.service';
import type { FormContext } from 'vee-validate';
import {
  type GroupJoinRequestResponse,
    type GroupMembersResponse,
    type GroupResponse,
    type GroupSearchResponse,
} from '@/type/group.ts'
import { useListStore } from '@/stores/list';
import { useUserStore } from '@/stores/user';
import axios from 'axios';
import { API_BASE_URL } from '../../shared/config.ts';
export const groupService = {
  // 创建群组
  async createGroup(
    data: {
      name: string;
      avatar_url?: string;
      description?: string;
      initial_members?: string[];
      max_members: number
    },
    formContext?: FormContext
  ) {
    try {
        const postData = {
            ...data,
            max_members: data.max_members ?? 50, // 设置默认值
        };
      const response = await api.post('/groups', postData);
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
            const response = await api.get<GroupSearchResponse>(`/groups/search/${encodeURIComponent(query)}`, {
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
      console.log("获取群组消息",response.data);
      return response.data;
    } catch (error) {
      console.error('获取群组信息失败:', error);
      throw error;
    }
  },
  async delGroup(groupId: string) {
      try {
          const response = await api.delete(`/groups/${groupId}`);
          return response.data;
      } catch (error) {
          console.error('解散群失败:', error);
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
  async getJoinedGroups(
      params?: {
          offset?: number;
      }
  ): Promise<GroupResponse[]> {
    try {
      const response = await api.get<GroupResponse[]>('/groups/joined',{params});
      useListStore().groups =response.data;
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

  // 上传群头像
  async uploadGroupAvatar(
    groupId: string,
    avatar: File,
  ) {
    const formData = new FormData();
    formData.append('file', avatar);
    
    try {
      const userStore = useUserStore();
      const instance = axios.create({
        baseURL: API_BASE_URL,
        headers: {
                  'Authorization': `Bearer ${userStore.token}`,
                }
        });
      const response = await instance.post(`/groups/${groupId}/avatar`,formData);
      return response.data.avatarUrl;
    } catch (error) {
      console.error('上传群头像失败:', error);
      throw error;
    }
  },

  // 邀请用户加入群组
  async inviteUsersToGroup(
    groupId: string,
    userIdsToInvite: string[],
  ) {
    try {
      const response = await api.post(`/groups/${groupId}/invite`, {
        user_ids: userIdsToInvite,
      });
      return response.data.success;
    } catch (error) {
      console.error('邀请用户加入群组失败:', error);
      throw error;
    }
  },

  // 处理加群请求
  async handleJoinRequest(
    groupId: string,
    requestId: string,
    action: string,
  ) {
    try {
      const response = await api.post(`/groups/${groupId}/join-requests/${requestId}/${action}`, );
      return response.data;
    } catch (error) {
      console.error('处理加群请求失败:', error);
      throw error;
    }
  },

  // 获取群组加群请求列表
  async getGroupJoinRequests(
    groupId: string,
  ) {
    try {
      const response = await api.get<GroupJoinRequestResponse>(`/groups/${groupId}/join-requests`);
      return response.data;
    } catch (error) {
      console.error('获取群组加群请求列表失败:', error);
      throw error;
    }
  },

  // 申请加入群组
  async requestToJoinGroup(
    groupId: string,
    message?: string
  ) {
    try {
      const response = await api.post(`/groups/${groupId}/join`, {
        message
      });
      return response.data.message;
    } catch (error: any) {
      if (error.response?.status === 403) {
        throw new Error('该群只允许邀请加入');
      }
      console.error('申请加入群组失败:', error);
      throw error;
    }
  },
  
  async updateGroupByID(groupId: string, request: any): Promise<GroupResponse | null> {
    try {
      if (request.avatar_url && typeof request.avatar_url === 'object') {
        request.avatar_url = request.avatar_url.avatar_url;
    }
        const response = await api.put<GroupResponse>(`/groups/${groupId}`, request);
        return response.data;
    } catch (error) {
        console.error('更新群组信息失败:', error);
        return null;
    }
  },
  async joinGroup(groupId: string, request: string): Promise<string> {
      try {
          const response = await api.post<string>(`/groups/${groupId}/join`, {message: request});
          return response.data
      }catch(error) {
          console.error('<UNK>:', error);
          throw error;
      }

  }
};
