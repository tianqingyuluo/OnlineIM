import api from './api.service';
import { useListStore } from '@/stores/list';
import type {
  Conversation,
  MessageReadersResponse,
  ReadStateResponse,
} from '@/type/Conversation';
import type { DraftMention, DraftPayload } from '@/utils/draft';

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

  async getDraft(conversationId: string): Promise<DraftPayload | null> {
    const response = await api.get<DraftPayload | ''>(`/conversations/${conversationId}/draft`);
    if (response.status === 204 || !response.data || typeof response.data === 'string') return null;
    return response.data;
  },

  async saveDraft(
    conversationId: string,
    body: { content: string; reply_to_message_id: string | null; at_users: DraftMention[] },
  ): Promise<DraftPayload | null> {
    const response = await api.put<DraftPayload | ''>(`/conversations/${conversationId}/draft`, body);
    if (response.status === 204 || !response.data || typeof response.data === 'string') return null;
    return response.data;
  },

  async clearDraft(conversationId: string): Promise<void> {
    await api.delete(`/conversations/${conversationId}/draft`);
  },

  /**
   * 页面即将关闭时提交草稿。普通 axios 在刷新时会被浏览器取消；
   * fetch keepalive 把请求留到文档卸载之后。调用方同时把正文记在本地，请求失败也能在下次打开时恢复。
   * 不弹错误提示。
   */
  flushDraftKeepalive(
    conversationId: string,
    body: { content: string; reply_to_message_id: string | null; at_users: DraftMention[] },
    token: string,
  ): void {
    if (!conversationId || !token || typeof fetch !== 'function') return;
    const base = String(api.defaults?.baseURL || '').replace(/\/$/, '');
    if (!base) return;
    const url = `${base}/conversations/${encodeURIComponent(conversationId)}/draft`;
    const blank = !body.content.trim();
    try {
      void fetch(url, {
        method: blank ? 'DELETE' : 'PUT',
        keepalive: true,
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
          ...(blank ? {} : { 'Content-Type': 'application/json' }),
        },
        body: blank ? undefined : JSON.stringify({
          content: body.content,
          reply_to_message_id: body.reply_to_message_id,
          at_users: body.at_users,
        }),
      }).catch(() => {});
    } catch {
      // URL 非法等同步错误不能挡住页面关闭
    }
  },

  async createConversation(target_id: string, type: string): Promise<Conversation> {
    const response = await api.post<Conversation>('/conversations/create', {
      target_id,
      type,
    });
    return response.data;
  },
};
