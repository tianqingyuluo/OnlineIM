import { defineStore } from 'pinia';
import type { MessageResponse } from '@/type/message';
import { dbService } from '@/utils/indexedDB';
import { MessageService } from '@/services/message.service';

interface PendingMessageRange {
  id?: number; // IndexedDB 自动生成的 ID
  minSeqId: string;
  maxSeqId: string;
}

interface PendingMessageInfo {
  userId: string;
  conversationId: string;
  ranges: PendingMessageRange[];
}

export const useHistoryStore = defineStore('history', {
  state: () => ({
    pendingMessages: {} as Record<string, PendingMessageInfo>,
    groupMessages: [] as MessageResponse[],
    chatMessages: [] as MessageResponse[],
    isLoading: false,
    hasMore: true,
    noMoreInfo: false,
    lastHistorySeqId: null as string | null,
    inMyHistory: false,
    hasInit: false,
  }),

  actions: {
    async init() {
      this.isLoading = false;
      this.hasMore = true;
      this.lastHistorySeqId = null;
      this.inMyHistory = false;
      this.noMoreInfo = false;
      this.groupMessages = [];
      this.chatMessages = [];
      this.pendingMessages = {};
      this.hasInit = false;
    },

    async loadInitialHistory(userId: string, conversationId: string, isGroup: boolean = false) {
      const ranges = await dbService.getPendingRanges(userId, conversationId);
      console.log("initial history", ranges);
      const pendingRanges = ranges.map(r => ({
        id: r.id,
        minSeqId: r.min_seq_id, // 保持为字符串
        maxSeqId: r.max_seq_id  // 保持为字符串
      }));

      if (pendingRanges.length > 0) {
        this.pendingMessages[conversationId] = {
          userId,
          conversationId,
          ranges: pendingRanges
        };
        // 使用字符串比较找出最大的 maxSeqId
        this.lastHistorySeqId = pendingRanges.reduce((max, range) =>
                range.maxSeqId.localeCompare(max || "0") > 0 ? range.maxSeqId : max || "0",
            this.lastHistorySeqId || "0"
        );
      } else {
        delete this.pendingMessages[conversationId];
        this.lastHistorySeqId = null;
      }
      await this.loadMessages(conversationId, isGroup);
    },

    async loadMessages(conversationId: string, isGroup: boolean = false) {
      if (this.isLoading || !this.hasMore) return;
      console.log("开始拉数据")
      this.isLoading = true;
      try {
        const before_message_id = isGroup ?
            (this.groupMessages.length > 0 ? this.groupMessages[0].seq_id : undefined) :
            (this.chatMessages.length > 0 ? this.chatMessages[0].seq_id : undefined);

        let shouldFetch = this.shouldFetchFromServer(conversationId, before_message_id);
        console.log("是否从服务器拉数据",shouldFetch);
        let messages: MessageResponse[] = [];
        let has_more = false;

        if (shouldFetch) {
          const response = await MessageService.getMessageHistory(
              conversationId,
              before_message_id
          );
          console.log("服务器返回数据", response.messages,response.has_more_before);
          messages = response.messages;
          has_more = response.has_more_before;

          if (messages.length > 0) {
            await this.updatePendingRanges(conversationId, messages);
            console.log("开始推送历史记录");
            await dbService.putHistory(messages);
            console.log("推送历史记录成功");
          }
        } else {
          console.log("试图从本地拉数据");
          if (!this.lastHistorySeqId)this.lastHistorySeqId='0'
          const response = await MessageService.getHistoryByIndexDB(
              conversationId,
              this.lastHistorySeqId,
              before_message_id
          );
          console.log("拉到的内容", response.messages,response.has_more_before);
          messages = response.messages;
          has_more = response.has_more_before;
          if (!has_more) {
            has_more = true;
            this.lastHistorySeqId = this.pendingMessages[conversationId].ranges[-1].minSeqId;
          }
        }

        this.processMessages(messages, isGroup, before_message_id);
        this.updateLoadState(has_more);

      } catch (error) {
        console.error('获取消息历史失败:', error);
      } finally {
        this.isLoading = false;
      }
    },

    shouldFetchFromServer(conversationId: string, beforeSeqId?: string): boolean {
      if (!this.hasInit) {this.hasInit = true; return true}
      if (!beforeSeqId) return true;

      const pendingInfo = this.pendingMessages[conversationId];
      if (!pendingInfo) return false;

      return pendingInfo.ranges.some(range =>
          beforeSeqId.localeCompare(range.minSeqId) >= 0 &&
          beforeSeqId.localeCompare(range.maxSeqId) <= 0
      );
    },

    async updatePendingRanges(conversationId: string, newMessages: MessageResponse[]) {
      if (newMessages.length === 0) return;

      // 1. 获取当前最新记录
      const lastSeq = await dbService.getLastHistorySeq(
          this.pendingMessages[conversationId]?.userId || '',
          conversationId
      );

      // 2. 对新消息排序（升序）
      const sortedMessages = [...newMessages].sort((a, b) =>
          a.seq_id.localeCompare(b.seq_id)
      );
      const newEarliest = sortedMessages[0].seq_id;

      // 3. 判断是否需要创建顶部空洞
      if (lastSeq && newEarliest.localeCompare(lastSeq) > 0) {
        await this.addPendingRange(
            this.pendingMessages[conversationId].userId,
            conversationId,
            lastSeq,    // 空洞开始（已知最后一条）
            newEarliest // 空洞结束（新数据的第一条）
        );
      }

      // 4. 原有区间填补逻辑（保持不变）
      const pendingInfo = this.pendingMessages[conversationId];
      if (!pendingInfo) return;
      const newMin = sortedMessages[0].seq_id;
      const newMax = sortedMessages[sortedMessages.length - 1].seq_id;

      for (const range of [...pendingInfo.ranges]) {
        if (
            newMin.localeCompare(range.minSeqId) <= 0 &&
            newMax.localeCompare(range.maxSeqId) >= 0
        ) {
          // 完全填补，删除区间
          await dbService.deletePendingRange(range.id!);
        } else if (
            newMax.localeCompare(range.minSeqId) >= 0 &&
            newMin.localeCompare(range.maxSeqId) <= 0
        ) {
          // 部分填补，分割区间
          await this.splitPendingRange(
              pendingInfo.userId,
              conversationId,
              range,
              newMin,
              newMax
          );
        }
      }
    },

    async splitPendingRange(
        userId: string,
        conversationId: string,
        range: PendingMessageRange,
        newMin: string,
        newMax: string
    ) {
      // 先移除原区间
      await dbService.deletePendingRange(range.id!);

      // 添加可能的新区间（填补后剩余的部分）
      if (range.minSeqId.localeCompare(newMin) < 0) {
        await dbService.addPendingRange(
            userId,
            conversationId,
            range.minSeqId,
            newMin
        );
      }
      if (range.maxSeqId.localeCompare(newMax) > 0) {
        await dbService.addPendingRange(
            userId,
            conversationId,
            newMax, // 直接使用字符串，不进行加1操作
            range.maxSeqId
        );
      }
    },

    processMessages(messages: MessageResponse[], isGroup: boolean, beforeSeqId?: string) {

      if (messages.length === 0) return;

      const sorted = messages.sort((a, b) => a.seq_id.localeCompare(b.seq_id));

      if (isGroup) {
        this.groupMessages = beforeSeqId ? [...sorted, ...this.groupMessages] : sorted;
      } else {
        this.chatMessages = beforeSeqId ? [...sorted, ...this.chatMessages] : sorted;
      }
      console.log("当前历史记录",this.groupMessages);
    },

    updateLoadState(has_more: boolean) {
      this.hasMore = has_more;
      this.noMoreInfo = !has_more;
    },

    async addPendingRange(
        userId: string,
        conversationId: string,
        minSeqId: string,
        maxSeqId: string
    ) {
      const id = await dbService.addPendingRange(
          userId,
          conversationId,
          minSeqId, // 直接存储字符串
          maxSeqId  // 直接存储字符串
      );

      if (!this.pendingMessages[conversationId]) {
        this.pendingMessages[conversationId] = {
          userId,
          conversationId,
          ranges: []
        };
      }
      if (id){
        this.pendingMessages[conversationId].ranges.push({
          id,
          minSeqId,
          maxSeqId
        });
      }

      // 更新 lastHistorySeqId
      if (!this.lastHistorySeqId || maxSeqId.localeCompare(this.lastHistorySeqId) > 0) {
        this.lastHistorySeqId = maxSeqId;
      }
    },

    async removePendingRange(
        userId: string,
        conversationId: string,
        rangeId: number
    ) {
      await dbService.deletePendingRange(rangeId);

      const pendingInfo = this.pendingMessages[conversationId];
      if (!pendingInfo) return;

      pendingInfo.ranges = pendingInfo.ranges.filter(r => r.id !== rangeId);
      if (pendingInfo.ranges.length === 0) {
        delete this.pendingMessages[conversationId];
        this.lastHistorySeqId = null;
      } else {
        this.lastHistorySeqId = pendingInfo.ranges.reduce((max, range) =>
                range.maxSeqId.localeCompare(max || "0") > 0 ? range.maxSeqId : max || "0",
            "0"
        );
      }
    }
  },

  getters: {
    hasPendingMessages: (state) => (conversationId: string) => {
      return !!state.pendingMessages[conversationId]?.ranges?.length;
    }
  }
});