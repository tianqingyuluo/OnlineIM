import api from './api.service';
import { useListStore } from '@/stores/list';
import type {
  Conversation,
  MessageReadersResponse,
  ReadStateResponse,
} from '@/type/Conversation';

export const conversationService = {
  async getConversations(
    params?: {
      limit?: number;
      offset?: number;
    },
  ): Promise<Conversation[]> {
    const response = await api.get<Conversation[]>('/conversations', { params });
    const listStore = useListStore();
    listStore.conversations = response.data;
    return response.data;
  },

  async getConversationById(conversationId: string): Promise<Conversation> {
    const response = await api.get<Conversation>(`/conversations/${conversationId}`);
    return response.data;
  },

  async getReadState(conversationId: string): Promise<ReadStateResponse> {
    const response = await api.get<ReadStateResponse>(
      `/conversations/${conversationId}/read-state`,
    );
    return response.data;
  },

  async getUnreadCount(conversationId: string, seqId: string) {
    const response = await api.get<ReadStateResponse & { from_seq_id: string }>(
      `/conversations/${conversationId}/unread-count/${seqId}`,
    );
    return response.data;
  },

  async getMessageReaders(
    conversationId: string,
    messageId: string,
  ): Promise<MessageReadersResponse> {
    const response = await api.get<MessageReadersResponse>(
      `/conversations/${conversationId}/messages/${messageId}/readers`,
    );
    return response.data;
  },

  async topConversation(conversationId: string[]) {
    const response = await api.put(`/conversations/${conversationId}/top`);
    return response.data;
  },

  async unTopConversation(conversationId: string) {
    const response = await api.delete(`/conversations/${conversationId}/top`);
    return response.data;
  },

  async muteConversation(conversationId: string) {
    const response = await api.put(`/conversations/${conversationId}/mute`);
    return response.data;
  },

  async unmuteConversation(conversationId: string) {
    const response = await api.delete(`/conversations/${conversationId}/mute`);
    return response.data;
  },

  async clearMessages(conversationId: string) {
    const response = await api.delete(`/conversations/${conversationId}/messages`);
    return response.data;
  },

  async createConversation(target_id: string, type: string): Promise<Conversation> {
    const response = await api.post<Conversation>('/conversations/create', {
      target_id,
      type,
    });
    return response.data;
  },
};
