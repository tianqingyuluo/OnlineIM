import api from './api.service';
import { useListStore } from '@/stores/list';
import type {
  Conversation,
  MessageReadersResponse,
  ReadStateResponse,
} from '@/type/Conversation';

// 置顶/免打扰只作用于当前用户自己的会话记录；成功后同步本地列表，侧栏的置顶分组/免打扰样式随之更新
function patchLocalConversation(conversationId: string, patch: Partial<Conversation>) {
  const listStore = useListStore();
  const conversation = listStore.conversations?.find((c) => c.conversation_id === conversationId);
  if (conversation) {
    Object.assign(conversation, patch);
  }
}

export const conversationService = {
  async getConversations(
    params?: {
      limit?: number;
      offset?: number;
    },
  ): Promise<Conversation[]> {
    const response = await api.get<Conversation[]>('/conversations', { params });
    // 服务端 unread_count 是当前用户自己的未读数，用它初始化侧栏未读角标
    const conversations = (response.data ?? []).map((c) => ({
      ...c,
      notreadednumber: c.unread_count ?? 0,
    }));
    const listStore = useListStore();
    listStore.conversations = conversations;
    return conversations;
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

  async topConversation(conversationId: string) {
    const response = await api.post(`/conversations/${conversationId}/top`);
    patchLocalConversation(conversationId, { is_pinned: true });
    return response.data;
  },

  async unTopConversation(conversationId: string) {
    const response = await api.delete(`/conversations/${conversationId}/top`);
    patchLocalConversation(conversationId, { is_pinned: false });
    return response.data;
  },

  async muteConversation(conversationId: string) {
    const response = await api.post(`/conversations/${conversationId}/mute`);
    patchLocalConversation(conversationId, { is_muted: true });
    return response.data;
  },

  async unmuteConversation(conversationId: string) {
    const response = await api.delete(`/conversations/${conversationId}/mute`);
    patchLocalConversation(conversationId, { is_muted: false });
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
