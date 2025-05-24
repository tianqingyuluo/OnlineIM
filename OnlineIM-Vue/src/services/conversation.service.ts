import api from './api.service';
import { useListStore } from '@/stores/list';
import {type Conversation} from "@/type/Conversation.ts";

export const conversationService = {
  async getConversations(
    params?: {
      limit?: number;
      offset?: number;
    },
  ): Promise<Conversation[]> {
    try {
      const response = await api.get<Conversation[]>('/conversations', {
        params
      });
      console.log(response.data);
      const listStore = useListStore();
      listStore.conversations = response.data;
      return response.data;
    } catch (error: any) {
      throw error;
    }
  },
  async getConversationById(conversationId: string): Promise<Conversation> {
    try {
      const response = await api.get<Conversation>(`/conversations/${conversationId}`);
      return response.data
    }catch(error: any) {
      throw error;
    }
  },
  async topConversation(conversationId: string[]) {
    try {
      const response= await api.put(`/conversations/${conversationId}/top`);
      return response.data
    } catch(error: any) {
      throw error;
    }
  },
  async unTopConversation(conversationId: string){
    try {
      const response= await api.delete(`/conversations/${conversationId}/top`);
      return response.data
    } catch(error: any) {
      throw error;
    }
  },
  async muteConversation(conversationId: string) {
    try {
      const response= await api.put(`/conversations/${conversationId}/mute`);
      return response.data
    } catch(error: any) {
      throw error;
    }
  },
  async unmuteConversation(conversationId: string) {
    try {
      const response= await api.delete(`/conversations/${conversationId}/mute`);
      return response.data
    } catch(error: any) {
      throw error;
    }
  },
  async clearMessages(conversationId: string) {
    try{
      const response= await api.delete(`/conversations/${conversationId}/messages`);
      return response.data
    }catch(error: any) {
      throw error;
    }
  }
};