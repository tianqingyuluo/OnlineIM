import { defineStore } from 'pinia';
import type {
  MessageAckPayload,
  MessageResponse,
  MessageUserBrief,
  ReadReceiptPayload,
  ReceiptPayload,
} from '@/type/message';
import { advanceDeliveryState, type DeliveryState } from '@/utils/message-state';
import { dbService } from '@/utils/indexedDB';
import { MessageService } from '@/services/message.service';
import { useUserStore } from '@/stores/user';
import { websocketService } from '@/services/websocket.service';
import { conversationService } from '@/services/conversation.service';
import { compareSeqId, maxSeqId } from '@/utils/seq-id';

interface PendingMessageRange {
  id?: number;
  minSeqId: string;
  maxSeqId: string;
}

interface PendingMessageInfo {
  userId: string;
  conversationId: string;
  ranges: PendingMessageRange[];
}

interface PendingMessageInfoItem {
  clientId: string;
  content: string;
  messageType: string;
  timestamp: string;
  createAt: string;
  conversationId: string;
  receiverId: string;
  isGroup: boolean;
  atUsers?: string[];
  attempts: number;
  state: DeliveryState;
  timeoutId?: ReturnType<typeof setTimeout>;
}

interface ConversationReadState {
  conversation_id: string;
  delivered_seq: string;
  read_seq: string;
  latest_seq: string;
  unread_count: number;
}

const MAX_SEND_ATTEMPTS = 3;
const SEND_CONFIRM_TIMEOUT_MS = 5000;

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
    pendingMessagesInfo: [] as PendingMessageInfoItem[],
    activeConversationId: null as string | null,
    activeConversationIsGroup: false,
    readStates: {} as Record<string, ConversationReadState>,
    groupReadCursors: {} as Record<string, Record<string, string>>,
    deliveredCursors: {} as Record<string, Record<string, string>>,
    readersByMessage: {} as Record<string, MessageUserBrief[]>,
    visibleMessageIds: {} as Record<string, Record<string, boolean>>,
  }),

  actions: {
    generateClientId(): string {
      return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 11)}`;
    },

    async init() {
      // 待发送消息及其重试定时器跨会话保留，避免切换页面后丢失失败重试入口。
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

    async loadInitialHistory(userId: string, conversationId: string, isGroup = false) {
      this.activeConversationId = conversationId;
      this.activeConversationIsGroup = isGroup;
      await this.refreshReadState(conversationId);

      const ranges = await dbService.getPendingRanges(userId, conversationId);
      const pendingRanges = ranges.map(range => ({
        id: range.id,
        minSeqId: range.min_seq_id,
        maxSeqId: range.max_seq_id,
      }));
      if (pendingRanges.length > 0) {
        this.pendingMessages[conversationId] = { userId, conversationId, ranges: pendingRanges };
        this.lastHistorySeqId = pendingRanges.reduce(
          (max, range) => compareSeqId(range.maxSeqId, max || '0') > 0 ? range.maxSeqId : max || '0',
          this.lastHistorySeqId || '0',
        );
      } else {
        delete this.pendingMessages[conversationId];
        this.lastHistorySeqId = null;
      }
      await this.loadMessages(conversationId, isGroup);
    },

    async loadMessages(conversationId: string, isGroup = false) {
      if (this.isLoading || !this.hasMore) return;
      this.isLoading = true;
      try {
        const currentMessages = isGroup ? this.groupMessages : this.chatMessages;
        const beforeSeqId = currentMessages.length > 0 ? currentMessages[0].seq_id : undefined;
        const shouldFetch = this.shouldFetchFromServer(conversationId, beforeSeqId);
        let messages: MessageResponse[] = [];
        let hasMore = false;

        if (shouldFetch) {
          const response = isGroup
            ? await MessageService.getMessageHistory(conversationId, beforeSeqId)
            : await MessageService.getPrivateHistory(conversationId, beforeSeqId);
          messages = response.messages;
          hasMore = response.has_more_before;
          if (messages.length > 0) {
            await this.updatePendingRanges(conversationId, messages);
            await dbService.putHistory(messages);
            await this.sendDeliveredReceipts(messages);
          }
        } else {
          const response = await MessageService.getHistoryByIndexDB(
            conversationId,
            this.lastHistorySeqId || '0',
            beforeSeqId,
          );
          messages = response.messages;
          hasMore = response.has_more_before;
        }

        this.processMessages(messages, isGroup, beforeSeqId);
        await this.refreshReadState(conversationId);
        this.updateLoadState(hasMore);
      } catch (error) {
        console.error('获取消息历史失败:', error);
      } finally {
        this.isLoading = false;
      }
    },

    shouldFetchFromServer(conversationId: string, beforeSeqId?: string): boolean {
      if (!this.hasInit) {
        this.hasInit = true;
        return true;
      }
      if (!beforeSeqId) return true;
      const pendingInfo = this.pendingMessages[conversationId];
      if (!pendingInfo) return false;
      return pendingInfo.ranges.some(range =>
        compareSeqId(beforeSeqId, range.minSeqId) >= 0
        && compareSeqId(beforeSeqId, range.maxSeqId) <= 0,
      );
    },

    async updatePendingRanges(conversationId: string, newMessages: MessageResponse[]) {
      if (!newMessages.length) return;
      const userId = this.pendingMessages[conversationId]?.userId || useUserStore().loggedInUser.user_id;
      const lastSeq = await dbService.getLastHistorySeq(userId, conversationId);
      const sortedMessages = [...newMessages].sort((left, right) => compareSeqId(left.seq_id, right.seq_id));
      const newEarliest = sortedMessages[0]?.seq_id;
      if (newEarliest && lastSeq && compareSeqId(newEarliest, lastSeq) > 0) {
        await this.addPendingRange(userId, conversationId, lastSeq, newEarliest);
      }

      const pendingInfo = this.pendingMessages[conversationId];
      if (!pendingInfo) return;
      const newMin = sortedMessages[0]?.seq_id || '0';
      const newMax = sortedMessages.at(-1)?.seq_id || '0';
      for (const range of [...pendingInfo.ranges]) {
        if (compareSeqId(newMin, range.minSeqId) <= 0 && compareSeqId(newMax, range.maxSeqId) >= 0) {
          if (range.id) await dbService.deletePendingRange(range.id);
          pendingInfo.ranges = pendingInfo.ranges.filter(item => item.id !== range.id);
        } else if (
          compareSeqId(newMax, range.minSeqId) >= 0
          && compareSeqId(newMin, range.maxSeqId) <= 0
        ) {
          await this.splitPendingRange(userId, conversationId, range, newMin, newMax);
        }
      }
    },

    async splitPendingRange(
      userId: string,
      conversationId: string,
      range: PendingMessageRange,
      newMin: string,
      newMax: string,
    ) {
      if (range.id) await dbService.deletePendingRange(range.id);
      const pendingInfo = this.pendingMessages[conversationId];
      if (pendingInfo) pendingInfo.ranges = pendingInfo.ranges.filter(item => item.id !== range.id);
      if (compareSeqId(range.minSeqId, newMin) < 0) {
        await this.addPendingRange(userId, conversationId, range.minSeqId, newMin);
      }
      if (compareSeqId(range.maxSeqId, newMax) > 0) {
        await this.addPendingRange(userId, conversationId, newMax, range.maxSeqId);
      }
    },

    processMessages(messages: MessageResponse[], isGroup: boolean, beforeSeqId?: string) {
      if (!messages.length) return;
      const existing = isGroup ? this.groupMessages : this.chatMessages;
      const merged = new Map<string, MessageResponse>();
      for (const message of [...existing, ...messages]) {
        const normalized = normalizeMessage(message);
        const key = normalized.client_message_id || normalized.message_id;
        merged.set(key, normalized);
      }
      const sorted = [...merged.values()].sort((left, right) => compareSeqId(left.seq_id, right.seq_id));
      if (isGroup) this.groupMessages = sorted;
      else this.chatMessages = sorted;

      const cachePromises = messages
        .filter(message => Boolean(message.sender_info?.avatar_url))
        .map(message => dbService.addImageFromUrl(message.sender_info.avatar_url as string));
      void Promise.all(cachePromises);

      if (beforeSeqId && this.lastHistorySeqId && compareSeqId(this.lastHistorySeqId, beforeSeqId) < 0) {
        this.lastHistorySeqId = beforeSeqId;
      }
    },

    updateLoadState(hasMore: boolean) {
      this.hasMore = hasMore;
      this.noMoreInfo = !hasMore;
    },

    async addPendingRange(userId: string, conversationId: string, minSeqId: string, maxSeqId: string) {
      const id = await dbService.addPendingRange(userId, conversationId, minSeqId, maxSeqId);
      if (!this.pendingMessages[conversationId]) {
        this.pendingMessages[conversationId] = { userId, conversationId, ranges: [] };
      }
      if (typeof id === 'number') {
        this.pendingMessages[conversationId].ranges.push({ id, minSeqId, maxSeqId });
      }
      if (!this.lastHistorySeqId || compareSeqId(maxSeqId, this.lastHistorySeqId) > 0) {
        this.lastHistorySeqId = maxSeqId;
      }
    },

    addPendingMessageContent(content: string, conversationId: string): string {
      const clientId = this.generateClientId();
      const now = new Date().toISOString();
      const item: PendingMessageInfoItem = {
        clientId,
        content,
        messageType: 'text',
        timestamp: now,
        createAt: now,
        conversationId,
        receiverId: '',
        isGroup: false,
        attempts: 1,
        state: 'sending',
      };
      this.pendingMessagesInfo.push(item);
      this.schedulePendingRetry(clientId);
      return clientId;
    },

    async updateMessageTimestamp(clientId: string) {
      const item = this.pendingMessagesInfo.find(entry => entry.clientId === clientId);
      if (!item) return;
      if (item.timeoutId) clearTimeout(item.timeoutId);
      item.timestamp = new Date().toISOString();
      item.state = 'sending';
      item.attempts = 1;
      this.updateMessageState(clientId, 'sending');
      this.schedulePendingRetry(clientId);
    },

    sendMessage(
      conversationId: string,
      receiverId: string,
      messageType: string,
      content: string,
      isGroup: boolean,
      atUsers?: string[],
    ): string {
      const clientId = this.generateClientId();
      const timestamp = new Date().toISOString();
      const currentUser = useUserStore().loggedInUser;
      const tempMessage: MessageResponse = {
        message_id: `local_${clientId}`,
        conversation_id: conversationId,
        sender_info: {
          user_id: currentUser.user_id,
          username: currentUser.username,
          nickname: currentUser.nickname,
          avatar_url: currentUser.avatar_url,
        },
        message_type: messageType,
        content,
        timestamp,
        seq_id: '0',
        status: 0,
        delivery_state: 'sending',
        is_recalled: false,
        client_message_id: clientId,
        mentioned_user_ids: atUsers,
      };
      if (isGroup) this.groupMessages.push(tempMessage);
      else this.chatMessages.push(tempMessage);

      const item: PendingMessageInfoItem = {
        clientId,
        content,
        messageType,
        timestamp,
        createAt: timestamp,
        conversationId,
        receiverId,
        isGroup,
        atUsers,
        attempts: 1,
        state: 'sending',
      };
      this.pendingMessagesInfo.push(item);
      this.schedulePendingRetry(clientId);

      const payload: Record<string, unknown> = isGroup
        ? {
            group_id: conversationId,
            message_type: messageType,
            content,
            client_message_id: clientId,
            ...(atUsers ? { at_users: atUsers } : {}),
          }
        : {
            conversation_id: conversationId,
            receiver_id: receiverId,
            message_type: messageType,
            content,
            client_message_id: clientId,
          };
      websocketService.sendMessage({
        type: isGroup ? 'GROUP_MESSAGE_REQUEST' : 'PRIVATE_MESSAGE_REQUEST',
        message: payload,
      });
      return clientId;
    },

    retryMessage(message: MessageResponse, isGroup: boolean, receiverId: string): void {
      const existing = this.pendingMessagesInfo.find(item => item.clientId === message.client_message_id);
      if (existing) {
        if (existing.timeoutId) clearTimeout(existing.timeoutId);
        existing.state = 'sending';
        existing.attempts = 1;
        existing.timestamp = new Date().toISOString();
        message.delivery_state = 'sending';
        this.sendPendingMessage(existing);
        this.schedulePendingRetry(existing.clientId);
        return;
      }

      const item: PendingMessageInfoItem = {
        clientId: message.client_message_id,
        content: message.content,
        messageType: message.message_type,
        timestamp: new Date().toISOString(),
        createAt: message.timestamp,
        conversationId: message.conversation_id,
        receiverId,
        isGroup,
        atUsers: message.mentioned_user_ids,
        attempts: 1,
        state: 'sending',
      };
      this.pendingMessagesInfo.push(item);
      message.delivery_state = 'sending';
      this.sendPendingMessage(item);
      this.schedulePendingRetry(item.clientId);
    },

    isMessagePending(clientId: string): boolean {
      return this.pendingMessagesInfo.some(item => item.clientId === clientId);
    },

    isMessageTimeout(clientId: string): boolean {
      return this.pendingMessagesInfo.find(item => item.clientId === clientId)?.state === 'failed';
    },

    getMessageDeliveryState(message: MessageResponse): DeliveryState {
      return message.delivery_state || 'sent';
    },

    handleIncomingMessage(message: MessageResponse, isGroup: boolean) {
      const normalized = normalizeMessage(message);
      const existing = this.findMessage(normalized.client_message_id, normalized.message_id);
      if (existing) {
        Object.assign(existing, normalized);
      } else {
        this.processMessages([normalized], isGroup);
      }
      void dbService.putHistory([normalized])
        .then(() => this.sendDeliveredReceipts([normalized]))
        .catch(error => console.error('保存实时消息失败:', error));
    },

    async sendDeliveredReceipts(messages: MessageResponse[]) {
      const currentUserId = useUserStore().loggedInUser.user_id;
      for (const message of messages) {
        if (!message.message_id || !message.seq_id || message.seq_id === '0') continue;
        if (message.sender_info?.user_id === currentUserId) continue;
        websocketService.sendReceipt({
          receipt_type: 'delivered',
          message_id: message.message_id,
          conversation_id: message.conversation_id,
          seq_id: message.seq_id,
        });
      }
    },

    findMessage(
      clientMessageId?: string,
      messageId?: string,
      conversationId?: string,
      seqId?: string,
    ): MessageResponse | undefined {
      const messages = [...this.chatMessages, ...this.groupMessages];
      return messages.find(message =>
        (clientMessageId && message.client_message_id === clientMessageId)
        || (messageId && message.message_id === messageId)
        || (conversationId && seqId
          && message.conversation_id === conversationId
          && compareSeqId(message.seq_id, seqId) === 0),
      );
    },

    messagesForConversation(conversationId: string): MessageResponse[] {
      return [
        ...this.chatMessages.filter(message => message.conversation_id === conversationId),
        ...this.groupMessages.filter(message => message.conversation_id === conversationId),
      ];
    },

    updateReadStateLatest(conversationId: string, seqId: string) {
      const state = this.readStates[conversationId] || emptyReadState(conversationId);
      state.latest_seq = maxSeqId(state.latest_seq, seqId);
      this.readStates[conversationId] = state;
    },

    updateReadStateDelivered(conversationId: string, seqId: string) {
      const state = this.readStates[conversationId] || emptyReadState(conversationId);
      state.delivered_seq = maxSeqId(state.delivered_seq, seqId);
      this.readStates[conversationId] = state;
      this.updateReadStateLatest(conversationId, seqId);
    },

    applyReadStateToMessages(conversationId: string, state: ConversationReadState) {
      const currentUserId = useUserStore().loggedInUser.user_id;
      for (const message of this.messagesForConversation(conversationId)) {
        if (message.sender_info?.user_id === currentUserId) continue;
        if (compareSeqId(message.seq_id, state.delivered_seq) <= 0) {
          message.delivery_state = advanceDeliveryState(
            normalizeDeliveryState(message.delivery_state),
            'delivered',
          );
        }
      }
    },

    updateMessageState(clientId: string, event: DeliveryState) {
      const message = this.findMessage(clientId);
      if (message) {
        message.delivery_state = advanceDeliveryState(
          normalizeDeliveryState(message.delivery_state),
          event,
        );
      }
    },

    markMessageFailed(clientId: string) {
      const item = this.pendingMessagesInfo.find(entry => entry.clientId === clientId);
      if (item?.timeoutId) clearTimeout(item.timeoutId);
      if (item) item.state = 'failed';
      this.updateMessageState(clientId, 'failed');
    },

    sendPendingMessage(item: PendingMessageInfoItem) {
      const payload: Record<string, unknown> = item.isGroup
        ? {
            group_id: item.conversationId,
            message_type: item.messageType,
            content: item.content,
            client_message_id: item.clientId,
            ...(item.atUsers ? { at_users: item.atUsers } : {}),
          }
        : {
            conversation_id: item.conversationId,
            receiver_id: item.receiverId,
            message_type: item.messageType,
            content: item.content,
            client_message_id: item.clientId,
          };
      websocketService.sendMessage({
        type: item.isGroup ? 'GROUP_MESSAGE_REQUEST' : 'PRIVATE_MESSAGE_REQUEST',
        message: payload,
      });
    },

    async handlePendingTimeout(clientId: string) {
      const item = this.pendingMessagesInfo.find(entry => entry.clientId === clientId);
      if (!item || item.state !== 'sending') return;
      if (item.attempts < MAX_SEND_ATTEMPTS) {
        item.attempts += 1;
        this.sendPendingMessage(item);
        this.schedulePendingRetry(clientId);
      } else {
        this.markMessageFailed(clientId);
      }
    },

    handleMessageAck(payload: MessageAckPayload) {
      if (!payload?.client_message_id) return;
      const message = this.findMessage(payload.client_message_id, payload.message_id);
      if (message) {
        message.message_id = payload.message_id;
        message.conversation_id = payload.conversation_id;
        message.seq_id = payload.seq_id;
        message.delivery_state = advanceDeliveryState(
          normalizeDeliveryState(message.delivery_state),
          payload.delivery_state || 'sent',
        );
        message.status = 1;
      }
      const pendingIndex = this.pendingMessagesInfo.findIndex(item => item.clientId === payload.client_message_id);
      if (pendingIndex !== -1) {
        const pending = this.pendingMessagesInfo[pendingIndex];
        if (pending.timeoutId) clearTimeout(pending.timeoutId);
        this.pendingMessagesInfo.splice(pendingIndex, 1);
      }
      this.updateReadStateLatest(payload.conversation_id, payload.seq_id);
    },

    handleWebSocketMessage(message: MessageResponse) {
      this.handleIncomingMessage(message, false);
    },

    handleGroupWebSocketMessage(message: MessageResponse) {
      this.handleIncomingMessage(message, true);
    },

    handleReceipt(payload: ReceiptPayload) {
      if (!payload?.message_id || !payload.conversation_id || !payload.seq_id) return;
      const currentUserId = useUserStore().loggedInUser.user_id;
      const receiverId = payload.receiver_id || currentUserId;
      const cursors = this.deliveredCursors[payload.conversation_id] || {};
      cursors[receiverId] = maxSeqId(cursors[receiverId], payload.seq_id);
      this.deliveredCursors[payload.conversation_id] = cursors;

      // 送达游标属于回执中的 receiver，而不是当前登录用户；因此不能写入
      // readStates[conversationId]，否则发送者收到回执后会错误地跳过自己的已读上报。
      const deliveredSeq = cursors[receiverId];
      for (const message of this.messagesForConversation(payload.conversation_id)) {
        if (message.sender_info?.user_id !== currentUserId) continue;
        if (compareSeqId(message.seq_id, deliveredSeq) > 0) continue;
        message.delivery_state = advanceDeliveryState(
          normalizeDeliveryState(message.delivery_state),
          'delivered',
        );
      }
    },

    handleReadReceipt(payload: ReadReceiptPayload) {
      if (!payload?.conversation_id || !payload.read_seq) return;
      const currentUserId = useUserStore().loggedInUser.user_id;
      const readerId = payload.reader_id || currentUserId;
      const cursors = this.groupReadCursors[payload.conversation_id] || {};
      cursors[readerId] = maxSeqId(cursors[readerId], payload.read_seq);
      this.groupReadCursors[payload.conversation_id] = cursors;

      // 这是其他成员的阅读游标，不是当前用户自己的 read_seq。当前用户的
      // readStates 只能由本地可视检测或 HTTP read-state 同步更新。
      const messages = this.messagesForConversation(payload.conversation_id);
      for (const message of messages) {
        if (message.sender_info?.user_id !== currentUserId) continue;
        if (compareSeqId(message.seq_id, payload.read_seq) > 0) continue;
        message.delivery_state = advanceDeliveryState(
          normalizeDeliveryState(message.delivery_state),
          'read',
        );
        if (payload.reader_id && payload.reader_id !== currentUserId) {
          void this.refreshMessageReaders(message);
        }
      }
    },

    handleServerError(payload: { client_message_id?: string; message?: string }) {
      if (!payload?.client_message_id) return;
      const message = this.findMessage(payload.client_message_id);
      if (message) this.markMessageFailed(message.client_message_id);
    },

    async refreshReadState(conversationId: string) {
      try {
        const state = await conversationService.getReadState(conversationId);
        this.readStates[conversationId] = state;
        this.applyReadStateToMessages(conversationId, state);
        const currentUserId = useUserStore().loggedInUser.user_id;
        const ownMessages = this.messagesForConversation(conversationId)
          .filter(message => message.sender_info.user_id === currentUserId)
          .slice(-50);
        await Promise.all(ownMessages.map(message => this.refreshMessageReaders(message)));
      } catch (error) {
        console.error('读取会话回执状态失败:', error);
      }
    },

    async refreshMessageReaders(message: MessageResponse) {
      if (!message.message_id || message.message_id.startsWith('local_')) return;
      try {
        const response = await conversationService.getMessageReaders(
          message.conversation_id,
          message.message_id,
        );
        const readers = response.readers || [];
        const deliveredReaders = response.delivered_readers || [];
        this.readersByMessage[message.message_id] = readers;
        const target = this.findMessage(message.client_message_id, message.message_id);
        if (target) {
          target.readers = readers;
          if (deliveredReaders.length > 0) {
            target.delivery_state = advanceDeliveryState(
              normalizeDeliveryState(target.delivery_state),
              'delivered',
            );
          }
          if (readers.length > 0) {
            target.delivery_state = advanceDeliveryState(
              normalizeDeliveryState(target.delivery_state),
              'read',
            );
          }
        }
      } catch (error) {
        console.error('获取消息阅读成员失败:', error);
      }
    },

    toggleMessageReaders(message: MessageResponse) {
      message.readers_expanded = !message.readers_expanded;
      if (message.readers_expanded && !message.readers) void this.refreshMessageReaders(message);
    },

    markMessageVisible(message: MessageResponse) {
      if (!message.seq_id || message.seq_id === '0') return;
      const currentUserId = useUserStore().loggedInUser.user_id;
      const visible = this.visibleMessageIds[message.conversation_id] || {};
      visible[message.message_id] = true;
      this.visibleMessageIds[message.conversation_id] = visible;
      if (message.sender_info.user_id === currentUserId) return;

      const state = this.readStates[message.conversation_id] || emptyReadState(message.conversation_id);
      const messages = this.messagesForConversation(message.conversation_id)
        .filter(item => item.seq_id !== '0')
        .sort((left, right) => compareSeqId(left.seq_id, right.seq_id));
      let candidate = state.read_seq;
      for (const item of messages) {
        if (compareSeqId(item.seq_id, candidate) <= 0) continue;
        const isOwnMessage = item.sender_info.user_id === currentUserId;
        if (!isOwnMessage && !visible[item.message_id]) break;
        candidate = item.seq_id;
      }
      if (compareSeqId(candidate, state.read_seq) <= 0) return;
      state.read_seq = candidate;
      state.unread_count = 0;
      this.readStates[message.conversation_id] = state;
      websocketService.sendReadReceipt({
        conversation_id: message.conversation_id,
        read_seq: candidate,
      });
    },

    markConversationReadToLatest(conversationId: string) {
      const messages = this.messagesForConversation(conversationId);
      const latest = messages.reduce((max, message) => maxSeqId(max, message.seq_id), '0');
      const state = this.readStates[conversationId] || emptyReadState(conversationId);
      const target = maxSeqId(latest, state.latest_seq);
      if (compareSeqId(target, state.read_seq) <= 0) return;
      state.read_seq = target;
      state.unread_count = 0;
      this.readStates[conversationId] = state;
      websocketService.sendReadReceipt({ conversation_id: conversationId, read_seq: target });
    },

    async syncActiveConversation() {
      if (!this.activeConversationId) return;
      try {
        const userId = useUserStore().loggedInUser.user_id;
        const maxSeqId = await dbService.getLastHistorySeq(userId, this.activeConversationId);
        if (!maxSeqId) {
          await this.refreshReadState(this.activeConversationId);
          return;
        }
        const missed = await MessageService.syncMessages(this.activeConversationId, maxSeqId);
        if (missed.length > 0) {
          await dbService.putHistory(missed);
          await this.sendDeliveredReceipts(missed);
          this.processMessages(missed, this.activeConversationIsGroup);
        }
        await this.refreshReadState(this.activeConversationId);
      } catch (error) {
        console.error('重连补齐失败:', error);
      }
    },

    // 供 websocket.service 调用的内部动作
    schedulePendingRetry(clientId: string) {
      const item = this.pendingMessagesInfo.find(entry => entry.clientId === clientId);
      if (!item) return;
      if (item.timeoutId) clearTimeout(item.timeoutId);
      item.timeoutId = setTimeout(() => {
        void this.handlePendingTimeout(clientId);
      }, SEND_CONFIRM_TIMEOUT_MS);
    },
  },

  getters: {
    hasPendingMessages: (state) => (conversationId: string) => {
      return Boolean(state.pendingMessages[conversationId]?.ranges?.length);
    },
    isMessagePending: (state) => (clientId: string) => {
      return state.pendingMessagesInfo.some(item => item.clientId === clientId);
    },
    isMessageTimeout: (state) => (clientId: string) => {
      return state.pendingMessagesInfo.find(item => item.clientId === clientId)?.state === 'failed';
    },
  },
});

function normalizeDeliveryState(state: DeliveryState | undefined): DeliveryState {
  return state || 'sent';
}

function normalizeMessage(message: MessageResponse): MessageResponse {
  return {
    ...message,
    delivery_state: message.delivery_state || 'sent',
    readers: message.readers,
  };
}

function emptyReadState(conversationId: string): ConversationReadState {
  return {
    conversation_id: conversationId,
    delivered_seq: '0',
    read_seq: '0',
    latest_seq: '0',
    unread_count: 0,
  };
}
