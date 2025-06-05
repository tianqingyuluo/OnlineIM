import api from './api.service';
import type {GroupAnnouncement, GroupAnnouncementRequest} from '@/type/groupAnnouncement';

export const groupAnnouncementService = {
  async getAnnouncementsByGroupId(groupId: string): Promise<GroupAnnouncement[]> {
    try {
      const response = await api.get<GroupAnnouncement[]>(`/groups/${groupId}/announcements`);
      return response.data;
    } catch (error) {
      console.error('获取群公告失败:', error);
      throw error;
    }
  },

  async createAnnouncement(groupId: string, announcement: { title: string; content: string }) {
    try {
      const response = await api.post(`/groups/${groupId}/announcements`, announcement);
      return response.data;
    } catch (error) {
      console.error('创建群公告失败:', error);
      throw error;
    }
  },
    async getAnnouncement(groupId: string, announcementId: string): Promise<GroupAnnouncement> {
      try {
          const response = await api.get<GroupAnnouncement>(`/groups/${groupId}/announcements/${announcementId}`);
          return response.data;
      }catch (error) {
          throw error;
      }
    },
  async publishAnnouncement(groupId: string, announcement: GroupAnnouncementRequest): Promise<GroupAnnouncement> {
      try {
          const response=await api.post<GroupAnnouncement>(`/groups/${groupId}/announcements`, announcement);
          return response.data;
      }catch (error) {
          throw error;
      }
  },
  async updateAnnouncement(groupId: string, announcement: GroupAnnouncementRequest,announcementId:string): Promise<GroupAnnouncement> {
      try {
          const response =await api.post<GroupAnnouncement>(`/groups/${groupId}/announcements/${announcementId}`, announcement);
          return response.data;
      }catch (error) {
          throw error;
      }
  },
    async deleteAnnouncement(groupId: string,announcementId:string) {
      try {
          const response = await api.delete(`/groups/${groupId}/announcements/${announcementId}`);
          return response.data;
      }catch (error) {
          throw error;
      }
    },
    async pinnedAnnouncement(groupId: string, announcementId:string) {
      try {
          const response = await api.put(`/groups/${groupId}/announcements/${announcementId}/pin`);
          return response.data;
      }catch (error) {
          throw error;
      }
    }
};