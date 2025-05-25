import { defineStore } from 'pinia';
import type { MessageResponse } from '@/type/message';

interface PendingMessageInfo {
  userId: string;
  conversationId: string;
  minSeqId?: number;
  maxSeqId?: number;
}

export const useHistoryStore = defineStore('history', {
  state: () => ({
    pendingMessages: {} as Record<string, PendingMessageInfo>, // 使用 conversationId 作为 key
    grooupMessages: [] as MessageResponse[],
    chatMessages: [] as MessageResponse[],
  }),
  actions: {
    // 可以根据需要添加 actions 来操作 state
    // 例如：添加消息、更新 pendingMessages 等
  },
  getters: {
    // 可以根据需要添加 getters 来获取计算后的 state
  }
});