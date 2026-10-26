import { defineStore } from 'pinia';
import type {
  MessageAckPayload,
  MessageRecalledPayload,
  MessageResponse,
  MessageUserBrief,
  OfflineSyncPayload,
  ReadReceiptPayload,
  ReplyReference,
  ReceiptPayload,
  ServerErrorPayload,
} from '@/type/message';
import type { Conversation } from '@/type/Conversation';
import { advanceDeliveryState, type DeliveryState } from '@/utils/message-state';
import { dbService, type SyncCursor } from '@/utils/indexedDB';
import { MessageService } from '@/services/message.service';
import { useUserStore } from '@/stores/user';
import { useListStore } from '@/stores/list';
import { websocketService } from '@/services/websocket.service';
import { conversationService } from '@/services/conversation.service';
import { compareSeqId, maxSeqId } from '@/utils/seq-id';
import { buildReplyReference } from '@/utils/reply-preview';
import { toServerTimestamp } from '@/utils/conversation-time';
import { isNewerRevision } from '@/utils/message-edit';
import { hydrateReplyPreviews, LOCKED_TEXT, looksLikeEnvelope, needsDecrypt } from '@/utils/e2ee-message';
import {
  applyOfflineConversations,
  hasMentionMark,
  noteCountedMessages,
  onConversationAck,
  onConversationEdit,
  onConversationMessage,
  onConversationRead,
  onConversationRecall,
  onOwnDeviceRead,
  scheduleConversationRefresh,
} from '@/stores/conversation-live';

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
  replyTo?: ReplyReference;
  /** 加密发送时 content 是线上密文，displayContent 是本机明文（失败重发草稿用） */
  displayContent?: string;
  encrypted?: boolean;
  attempts: number;
  state: DeliveryState;
  timeoutId?: ReturnType<typeof setTimeout>;
}

export interface ReplyComposerTarget {
  conversationId: string;
  reference: ReplyReference;
}

export interface FailedReplyDraft {
  clientId: string;
  conversationId: string;
  receiverId: string;
  content: string;
  messageType: string;
  isGroup: boolean;
  atUsers?: string[];
  replyTarget: ReplyComposerTarget;
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

export interface RecalledOriginal {
  conversationId: string;
  content: string;
  atUsers: string[] | null;
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
    pendingMessagesInfo: [] as PendingMessageInfoItem[],
    activeConversationId: null as string | null,
    activeConversationIsGroup: false,
    readStates: {} as Record<string, ConversationReadState>,
    groupReadCursors: {} as Record<string, Record<string, string>>,
    deliveredCursors: {} as Record<string, Record<string, string>>,
    readersByMessage: {} as Record<string, MessageUserBrief[]>,
    visibleMessageIds: {} as Record<string, Record<string, boolean>>,
    replyTarget: null as ReplyComposerTarget | null,
    failedReplyDraft: null as FailedReplyDraft | null,
    /** 本页面会话期间自己撤回的文本原文（“重新编辑”用；只在内存里，刷新后不再提供） */
    recalledOriginals: {} as Record<string, RecalledOriginal>,
  }),

  actions: {
    generateClientId(): string {
      return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 11)}`;
    },

    selectReplyTarget(message: MessageResponse): boolean {
      const reference = buildReplyReference(message);
      if (!reference) return false;
      this.replyTarget = {
        conversationId: message.conversation_id,
        reference,
      };
      return true;
    },

    cancelReplyTarget(): void {
      this.replyTarget = null;
    },

    consumeFailedReplyDraft(): FailedReplyDraft | null {
      const draft = this.failedReplyDraft;
      this.failedReplyDraft = null;
      return draft;
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
      this.replyTarget = null;
      this.failedReplyDraft = null;
    },

    async loadInitialHistory(userId: string, conversationId: string, isGroup = false) {
      if (this.activeConversationId && this.activeConversationId !== conversationId) {
        this.cancelReplyTarget();
      }
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
        const userId = useUserStore().loggedInUser?.user_id;
        let messages: MessageResponse[] = [];
        let hasMore = false;

        if (beforeSeqId) {
          if (this.shouldFetchOlderFromServer(conversationId, beforeSeqId)) {
            const page = await this.ingestServerHistory(conversationId, isGroup, beforeSeqId);
            messages = page.messages;
            hasMore = page.hasMore;
          } else {
            const response = await MessageService.getHistoryByIndexDB(
              conversationId,
              this.lastHistorySeqId || '0',
              beforeSeqId,
            );
            messages = response.messages;
            hasMore = response.has_more_before || this.hasOlderGap(conversationId, this.earliestSeq(messages) || beforeSeqId);
          }
        } else if (await this.needsInitialHistory(userId, conversationId)) {
          // 还没锚定过：第一次打开（或本地只有几条实时消息）从服务端拉最近一页，并登记更早的缺口
          const page = await this.ingestServerHistory(conversationId, isGroup);
          messages = page.messages;
          hasMore = page.hasMore;
        } else {
          const localLast = userId ? await this.localHistorySeq(userId, conversationId) : null;
          if (localLast) {
            const response = await MessageService.getHistoryByIndexDB(
              conversationId,
              this.lastHistorySeqId || '0',
            );
            messages = response.messages;
            const newer = await this.pullNewerThanLocal(conversationId, localLast);
            if (newer.length > 0) {
              await dbService.putHistory(newer);
              await this.sendDeliveredReceipts(newer);
              if (userId) await this.noteHistoryAnchor(userId, conversationId, newer);
              messages = [...messages, ...newer];
            }
            hasMore = response.has_more_before || this.hasOlderGap(conversationId, this.earliestSeq(messages));
          } else {
            // 上次打开确认过是空会话。列表或游标显示有新消息时才再向服务端要
            const known = userId ? await this.knownLatestSeq(userId, conversationId) : '0';
            if (compareSeqId(known, '0') > 0) {
              const page = await this.ingestServerHistory(conversationId, isGroup);
              messages = page.messages;
              hasMore = page.hasMore;
            }
          }
        }

        const revealed = await this.revealForDisplay(messages);
        this.processMessages(revealed, isGroup, beforeSeqId);
        await this.refreshReadState(conversationId);
        this.updateLoadState(hasMore);
      } catch (error) {
        console.error('获取消息历史失败:', error);
      } finally {
        this.isLoading = false;
      }
    },

    /**
     * 往上翻时，只有当前边界落在已登记的缺口里才找服务端。
     * 第一页不在这里决定：没有锚定过才整页拉取，锚定之后走本地，再按游标补更新的消息。
     */
    shouldFetchOlderFromServer(conversationId: string, beforeSeqId?: string): boolean {
      if (!beforeSeqId) return false;
      const pendingInfo = this.pendingMessages[conversationId];
      if (!pendingInfo) return false;
      return pendingInfo.ranges.some(range =>
        compareSeqId(beforeSeqId, range.minSeqId) >= 0
        && compareSeqId(beforeSeqId, range.maxSeqId) <= 0,
      );
    },

    async ingestServerHistory(conversationId: string, isGroup: boolean, beforeSeqId?: string) {
      const response = isGroup
        ? await MessageService.getMessageHistory(conversationId, beforeSeqId)
        : await MessageService.getPrivateHistory(conversationId, beforeSeqId);
      const messages = response.messages || [];
      const hasMore = response.has_more_before;
      if (messages.length > 0) {
        await this.updatePendingRanges(conversationId, messages, { hasMoreBefore: hasMore });
        await dbService.putHistory(messages);
        await this.sendDeliveredReceipts(messages);
      }
      const userId = useUserStore().loggedInUser?.user_id;
      if (userId) await this.noteHistoryAnchor(userId, conversationId, messages);
      return { messages, hasMore };
    },

    /** 还没做过一次成功的历史分页。本地有没有几条实时消息都不算，否则每次打开都会从服务端重拉整页 */
    async needsInitialHistory(userId: string | undefined, conversationId: string): Promise<boolean> {
      if (!userId) return true;
      const cursor = await this.readSyncCursor(userId, conversationId);
      return !cursor?.anchored;
    },

    earliestSeq(messages: MessageResponse[]): string | undefined {
      return messages.reduce<string | undefined>((min, message) => {
        if (!message?.seq_id) return min;
        if (!min || compareSeqId(message.seq_id, min) < 0) return message.seq_id;
        return min;
      }, undefined);
    },

    hasOlderGap(conversationId: string, earliestSeq?: string): boolean {
      const ranges = this.pendingMessages[conversationId]?.ranges || [];
      if (!earliestSeq) return ranges.length > 0;
      return ranges.some(range => compareSeqId(range.minSeqId, earliestSeq) < 0);
    },

    async localHistorySeq(userId: string, conversationId: string): Promise<string | null> {
      try {
        return await dbService.getLastHistorySeq(userId, conversationId);
      } catch (error) {
        console.warn('读取本地消息游标失败:', error);
        return null;
      }
    },

    async updatePendingRanges(
      conversationId: string,
      newMessages: MessageResponse[],
      options: { hasMoreBefore?: boolean } = {},
    ) {
      if (!newMessages.length) return;
      const userId = this.pendingMessages[conversationId]?.userId || useUserStore().loggedInUser.user_id;
      const lastSeq = await dbService.getLastHistorySeq(userId, conversationId);
      const sortedMessages = [...newMessages].sort((left, right) => compareSeqId(left.seq_id, right.seq_id));
      const newEarliest = sortedMessages[0]?.seq_id;
      if (newEarliest && lastSeq && compareSeqId(newEarliest, lastSeq) > 0) {
        await this.addPendingRange(userId, conversationId, lastSeq, newEarliest);
      } else if (newEarliest && options.hasMoreBefore
          && !(this.pendingMessages[conversationId]?.ranges || []).some(range => compareSeqId(range.minSeqId, newEarliest) < 0)
          && !(await this.hasLocalHistoryBefore(userId, conversationId, newEarliest))) {
        // 服务端说更早还有，本地却一条比这页更早的都没有（第一次打开、或本地只存了实时收到的几条）：
        // 登记“0 ~ 这页最早一条”待补，往上翻时才会继续找服务端，而不是读空的本地缓存后当成到顶了
        await this.addPendingRange(userId, conversationId, '0', newEarliest);
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

    async hasLocalHistoryBefore(userId: string, conversationId: string, seqId: string): Promise<boolean> {
      try {
        const older = await dbService.getHistory(userId, conversationId, undefined, seqId);
        return Array.isArray(older) && older.length > 0;
      } catch (error) {
        console.warn('读取本地历史失败:', error);
        return true;
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
        const previous = merged.get(key);
        if (previous?.e2ee_status === 'decrypted' && needsDecrypt(normalized)) {
          merged.set(key, {
            ...normalized,
            content: previous.content,
            is_encrypted: true,
            e2ee_status: 'decrypted',
            e2ee_envelope: normalized.e2ee_envelope
              || (looksLikeEnvelope(normalized.content) ? normalized.content : previous.e2ee_envelope),
          });
        } else {
          merged.set(key, normalized);
        }
      }
      const sorted = hydrateReplyPreviews(
        [...merged.values()].sort((left, right) => compareSeqId(left.seq_id, right.seq_id)),
      );
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
      const now = toServerTimestamp();
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
      item.timestamp = toServerTimestamp();
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
      transport?: { encrypted?: boolean, displayContent?: string },
    ): string {
      const clientId = this.generateClientId();
      const timestamp = toServerTimestamp();
      const currentUser = useUserStore().loggedInUser;
      const encrypted = !isGroup && transport?.encrypted === true;
      const visible = encrypted ? (transport?.displayContent ?? content) : content;
      const replyTo = this.replyTarget?.conversationId === conversationId
        ? { ...this.replyTarget.reference }
        : undefined;
      const tempMessage: MessageResponse = {
        message_id: `local_${clientId}`,
        conversation_id: conversationId,
        sender_info: {
          user_id: currentUser.user_id,
          username: currentUser.username,
          nickname: currentUser.nickname?.trim() || currentUser.username,
          avatar_url: currentUser.avatar_url,
        },
        message_type: messageType,
        content: visible,
        timestamp,
        seq_id: '0',
        status: 0,
        delivery_state: 'sending',
        is_recalled: false,
        client_message_id: clientId,
        ...(encrypted ? {
          is_encrypted: true,
          e2ee_status: 'decrypted' as const,
          e2ee_envelope: content,
        } : {}),
        ...(isGroup && atUsers?.length ? { at_users: atUsers } : {}),
        reply_to: replyTo,
      };
      if (isGroup) this.groupMessages.push(tempMessage);
      else this.chatMessages.push(tempMessage);
      onConversationMessage(tempMessage, this.activeConversationId);

      const item: PendingMessageInfoItem = {
        clientId,
        content,
        displayContent: encrypted ? visible : undefined,
        encrypted,
        messageType,
        timestamp,
        createAt: timestamp,
        conversationId,
        receiverId,
        isGroup,
        atUsers,
        replyTo,
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
            ...(replyTo ? { reply_to_message_id: replyTo.message_id } : {}),
            ...(atUsers?.length ? { at_users: atUsers } : {}),
          }
        : {
            conversation_id: conversationId,
            receiver_id: receiverId,
            message_type: messageType,
            content,
            client_message_id: clientId,
            ...(encrypted ? { is_encrypted: true } : {}),
            ...(replyTo ? { reply_to_message_id: replyTo.message_id } : {}),
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
        existing.timestamp = toServerTimestamp();
        message.delivery_state = 'sending';
        this.sendPendingMessage(existing);
        this.schedulePendingRetry(existing.clientId);
        return;
      }

      const encrypted = !isGroup && (message.is_encrypted === true || !!message.e2ee_envelope);
      const item: PendingMessageInfoItem = {
        clientId: message.client_message_id,
        content: encrypted && message.e2ee_envelope ? message.e2ee_envelope : message.content,
        displayContent: message.e2ee_status === 'decrypted' ? message.content : undefined,
        encrypted,
        messageType: message.message_type,
        timestamp: toServerTimestamp(),
        createAt: message.timestamp,
        conversationId: message.conversation_id,
        receiverId,
        isGroup,
        atUsers: message.at_users ?? undefined,
        replyTo: message.reply_to,
        attempts: 1,
        state: 'sending',
      };
      this.pendingMessagesInfo.push(item);
      message.delivery_state = 'sending';
      this.sendPendingMessage(item);
      this.schedulePendingRetry(item.clientId);
    },

    getMessageDeliveryState(message: MessageResponse): DeliveryState {
      return message.delivery_state || 'sent';
    },

    handleIncomingMessage(message: MessageResponse, isGroup: boolean, atMe?: boolean, atAll?: boolean) {
      const normalized = normalizeMessage(message);
      const existing = this.findMessage(normalized.client_message_id, normalized.message_id);
      if (existing?.e2ee_status === 'decrypted' && needsDecrypt(normalized)) {
        this.keepLocalPlaintext(existing, normalized, isGroup, atMe, atAll);
        return;
      }
      if (!needsDecrypt(normalized)) {
        this.commitIncoming(normalized, isGroup, atMe, atAll);
        return;
      }
      void this.revealForDisplay([normalized]).then(([revealed]) => {
        const current = this.findMessage(revealed.client_message_id, revealed.message_id);
        if (current?.e2ee_status === 'decrypted' && revealed.e2ee_status !== 'decrypted') {
          this.keepLocalPlaintext(current, revealed, isGroup, atMe, atAll);
          return;
        }
        this.commitIncoming(revealed, isGroup, atMe, atAll);
      }).catch(error => console.error('解密实时消息失败:', error));
    },

    keepLocalPlaintext(
      existing: MessageResponse,
      incoming: MessageResponse,
      isGroup: boolean,
      atMe?: boolean,
      atAll?: boolean,
    ) {
      const envelope = incoming.e2ee_envelope
        || (looksLikeEnvelope(incoming.content) ? incoming.content : existing.e2ee_envelope);
      Object.assign(existing, {
        ...incoming,
        content: existing.content,
        is_encrypted: true,
        e2ee_status: 'decrypted',
        e2ee_envelope: envelope,
      });
      this.commitIncoming(existing, isGroup, atMe, atAll);
    },

    commitIncoming(normalized: MessageResponse, isGroup: boolean, atMe?: boolean, atAll?: boolean) {
      const existing = this.findMessage(normalized.client_message_id, normalized.message_id);
      if (existing && existing !== normalized) {
        Object.assign(existing, normalized);
      } else if (!existing && normalized.conversation_id === this.activeConversationId) {
        // 只有当前打开的会话才放进消息列表；其他会话的消息只更新侧栏并写入本地缓存，
        // 否则会混进正在查看的聊天窗口
        this.processMessages([normalized], isGroup);
      }
      if (normalized.conversation_id === this.activeConversationId) {
        hydrateReplyPreviews(isGroup ? this.groupMessages : this.chatMessages);
      }
      const shown = existing && existing !== normalized ? existing : normalized;
      onConversationMessage(shown, this.activeConversationId, atMe, atAll);
      void dbService.putHistory([wireCopy(shown)])
        .then(() => this.sendDeliveredReceipts([shown]))
        .catch(error => console.error('保存实时消息失败:', error));
    },

    /** 明文消息原样返回；只有带密文信封的才动态加载密钥库，避免普通消息测试拉起加密模块。 */
    async revealForDisplay(messages: MessageResponse[]): Promise<MessageResponse[]> {
      if (!messages.some(needsDecrypt)) return hydrateReplyPreviews(messages);
      const { useE2eeStore } = await import('@/stores/e2ee.store');
      return useE2eeStore().revealMessages(messages);
    },

    async redecryptVisible(): Promise<void> {
      const visible = [...this.chatMessages, ...this.groupMessages].map(message =>
        message.e2ee_envelope
          ? { ...message, content: message.e2ee_envelope, is_encrypted: true, e2ee_status: undefined }
          : message,
      );
      if (!visible.some(needsDecrypt)) {
        hydrateReplyPreviews(this.chatMessages);
        hydrateReplyPreviews(this.groupMessages);
        return;
      }
      const revealed = await this.revealForDisplay(visible);
      for (const message of revealed) {
        const existing = this.findMessage(message.client_message_id, message.message_id);
        if (existing) Object.assign(existing, message);
        if (message.e2ee_status) onConversationEdit(message.conversation_id, existing || message);
      }
      hydrateReplyPreviews(this.chatMessages);
      hydrateReplyPreviews(this.groupMessages);
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
            ...(item.replyTo ? { reply_to_message_id: item.replyTo.message_id } : {}),
            ...(item.atUsers ? { at_users: item.atUsers } : {}),
          }
        : {
            conversation_id: item.conversationId,
            receiver_id: item.receiverId,
            message_type: item.messageType,
            content: item.content,
            client_message_id: item.clientId,
            ...(item.encrypted ? { is_encrypted: true } : {}),
            ...(item.replyTo ? { reply_to_message_id: item.replyTo.message_id } : {}),
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
        if (
          pending.replyTo
          && this.replyTarget?.conversationId === pending.conversationId
          && this.replyTarget.reference.message_id === pending.replyTo.message_id
        ) {
          this.replyTarget = null;
        }
        if (this.failedReplyDraft?.clientId === pending.clientId) {
          this.failedReplyDraft = null;
        }
        this.pendingMessagesInfo.splice(pendingIndex, 1);
      }
      onConversationAck(payload.conversation_id, payload.client_message_id, payload.message_id);
      this.updateReadStateLatest(payload.conversation_id, payload.seq_id);
    },

    handleWebSocketMessage(message: MessageResponse) {
      this.handleIncomingMessage(message, false);
    },

    /** atMe / atAll：GROUP_MESSAGE_RESPONSE 帧顶层字段 */
    handleGroupWebSocketMessage(message: MessageResponse, atMe?: boolean, atAll?: boolean) {
      this.handleIncomingMessage(message, true, atMe, atAll);
    },

    async jumpToMessage(conversationId: string, messageId: string): Promise<MessageResponse | null> {
      const inMemory = this.messagesForConversation(conversationId)
        .find(message => message.message_id === messageId);
      if (inMemory) return inMemory;

      const userId = useUserStore().loggedInUser.user_id;
      const isGroup = conversationId.startsWith('grp_')
        || (this.activeConversationId === conversationId && this.activeConversationIsGroup);
      const cached = await dbService.getHistoryMessage(userId, conversationId, messageId);
      if (cached) {
        const [revealed] = await this.revealForDisplay([cached]);
        this.processMessages([revealed], isGroup);
        return this.messagesForConversation(conversationId)
          .find(message => message.message_id === messageId) || null;
      }

      const context = await MessageService.getMessageContext(conversationId, messageId);
      if (context.messages.length > 0) {
        await dbService.putHistory(context.messages);
        this.processMessages(await this.revealForDisplay(context.messages), isGroup);
      }
      return this.messagesForConversation(conversationId)
        .find(message => message.message_id === messageId) || null;
    },

    /**
     * 消息被编辑（MESSAGE_EDITED 帧，或自己编辑成功后的 REST 返回）：
     * 更新已加载的消息、引用了它的回复（“原消息已编辑”）、正在回复它的输入框引用、会话预览与本地缓存。
     * 乱序到达的旧版本（content_revision 更小）被忽略。
     */
    async handleMessageEdited(edited: MessageResponse, atMe?: boolean, atAll?: boolean): Promise<void> {
      if (!edited?.message_id || !edited.conversation_id) return;
      const incoming = edited;
      if (needsDecrypt(incoming)) {
        const [revealed] = await this.revealForDisplay([normalizeMessage(incoming)]);
        edited = revealed;
      }
      const me = useUserStore().loggedInUser.user_id;
      const mentioned = (ids?: string[] | null) => !!me && !!ids && (ids.includes(me) || ids.includes('all'));
      const personally = (ids?: string[] | null) => !!me && !!ids && ids.includes(me);
      let mentionChanged = false;
      let applied = false;
      for (const message of this.messagesForConversation(edited.conversation_id)) {
        if (message.message_id === edited.message_id) {
          if (!isNewerRevision(edited.content_revision, message.content_revision)) continue;
          const before = mentioned(message.at_users);
          const beforePersonal = personally(message.at_users);
          message.content = edited.content;
          message.is_encrypted = edited.is_encrypted;
          message.e2ee_status = edited.e2ee_status;
          message.e2ee_envelope = edited.e2ee_envelope;
          message.edited = true;
          message.edited_at = edited.edited_at ?? null;
          message.content_revision = edited.content_revision;
          if (edited.at_users !== undefined) message.at_users = edited.at_users;
          mentionChanged = before !== mentioned(message.at_users) || beforePersonal !== personally(message.at_users);
          applied = true;
        }
        if (message.reply_to?.message_id === edited.message_id && message.reply_to.state === 'active') {
          message.reply_to = { ...message.reply_to, state: 'edited' };
        }
      }
      // 本地没有这条消息：被新 @ 了，或列表里正带着标记（编辑可能去掉了 @），都以服务端为准刷新
      if (!applied && (atMe || atAll || hasMentionMark(edited.conversation_id))) mentionChanged = true;
      if (this.replyTarget?.reference.message_id === edited.message_id
        && this.replyTarget.reference.state === 'active') {
        this.replyTarget.reference = { ...this.replyTarget.reference, state: 'edited' };
      }
      hydrateReplyPreviews(this.messagesForConversation(edited.conversation_id));
      onConversationEdit(edited.conversation_id, edited);
      // @我 变化（被新 @ 或不再被 @）以服务端会话记录为准
      if (mentionChanged && edited.sender_info?.user_id !== me) {
        scheduleConversationRefresh(edited.conversation_id, 600);
      }
      try {
        const stored = edited.e2ee_envelope
          ? { ...edited, content: edited.e2ee_envelope, e2ee_status: undefined, is_encrypted: true as const }
          : incoming;
        await dbService.applyMessageEdit(me, stored);
      } catch (error) {
        console.warn('更新本地缓存的编辑失败:', error);
      }
    },

    /** 自己撤回的文本原文（“重新编辑”）；没有返回 null */
    recalledOriginal(messageId: string): RecalledOriginal | null {
      return this.recalledOriginals[messageId] || null;
    },

    async handleMessageRecalled(
      payload: MessageRecalledPayload,
      options: { adjustUnread?: boolean } = {},
    ): Promise<void> {
      if (!payload?.message_id || !payload.conversation_id) return;
      const me = useUserStore().loggedInUser.user_id;
      onConversationRecall(payload.conversation_id, payload.message_id, {
        recallUserId: payload.recall_user_id,
        senderId: payload.sender_id,
        operatorRole: payload.recall_operator_role,
        seqId: payload.seq_id,
        activeConversationId: this.activeConversationId,
        adjustUnread: options.adjustUnread,
      });
      for (const message of this.messagesForConversation(payload.conversation_id)) {
        if (message.message_id === payload.message_id) {
          if (payload.recall_user_id) message.recall_user_id = payload.recall_user_id
          if (payload.recall_operator_role) message.recall_operator_role = payload.recall_operator_role
          // 自己撤回的文本才提供“重新编辑”；管理员撤回我的消息不算
          const recalledByMe = payload.recall_user_id ? payload.recall_user_id === me : message.sender_info?.user_id === me
          const lockedPlaceholder = message.e2ee_status === 'locked' || message.content === LOCKED_TEXT;
          if (recalledByMe && message.sender_info?.user_id === me && (message.message_type || 'text').toLowerCase() === 'text'
            && typeof message.content === 'string' && message.content && !lockedPlaceholder
            && !this.recalledOriginals[message.message_id]) {
            this.recalledOriginals[message.message_id] = {
              conversationId: payload.conversation_id,
              content: message.content,
              atUsers: message.at_users ? [...message.at_users] : null,
            };
          }
          message.status = 3;
          message.is_recalled = true;
          message.content = '';
          message.at_users = null;
          message.reply_to = undefined;
          message.forwarded_from = null;
        }
        if (message.reply_to?.message_id === payload.message_id) {
          message.reply_to = {
            ...message.reply_to,
            state: 'recalled',
            preview_text: null,
          };
        }
      }
      if (this.replyTarget?.reference.message_id === payload.message_id) {
        this.replyTarget.reference = {
          ...this.replyTarget.reference,
          state: 'recalled',
          preview_text: null,
        };
      }
      await dbService.markMessageRecalled(
        useUserStore().loggedInUser.user_id,
        payload.conversation_id,
        payload.message_id,
      );
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
      // 自己另一台设备读过了：同步未读，不要把“我发出的消息”标成对方已读
      if (payload.reader_id && payload.reader_id === currentUserId) {
        const state = this.readStates[payload.conversation_id] || emptyReadState(payload.conversation_id);
        state.read_seq = maxSeqId(state.read_seq, payload.read_seq);
        this.readStates[payload.conversation_id] = state;
        onOwnDeviceRead(payload.conversation_id, payload.read_seq);
        return;
      }
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

    handleServerError(payload: ServerErrorPayload) {
      if (!payload?.client_message_id) return;
      const message = this.findMessage(payload.client_message_id);
      if (message) this.markMessageFailed(message.client_message_id);
      if (payload.code !== 'REPLY_TARGET_UNAVAILABLE') return;
      const pending = this.pendingMessagesInfo.find(
        item => item.clientId === payload.client_message_id,
      );
      if (!pending?.replyTo) return;
      const replyTarget: ReplyComposerTarget = {
        conversationId: pending.conversationId,
        reference: { ...pending.replyTo },
      };
      this.replyTarget = replyTarget;
      this.failedReplyDraft = {
        clientId: pending.clientId,
        conversationId: pending.conversationId,
        receiverId: pending.receiverId,
        content: pending.displayContent ?? pending.content,
        messageType: pending.messageType,
        isGroup: pending.isGroup,
        atUsers: pending.atUsers,
        replyTarget,
      };
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
      const remaining = messages.filter(item => compareSeqId(item.seq_id, candidate) > 0
        && item.sender_info.user_id !== currentUserId).length;
      onConversationRead(message.conversation_id, remaining);
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
      onConversationRead(conversationId, 0);
    },

    /** 聊天窗口卸载：不再视为“正在查看”，并用服务端值校正该会话的未读与预览 */
    leaveConversation(conversationId: string) {
      if (this.activeConversationId === conversationId) {
        this.activeConversationId = null;
        this.activeConversationIsGroup = false;
      }
      scheduleConversationRefresh(conversationId, 300);
    },

    /**
     * 每次连接或重连成功后上报本地游标。优先用历史缓存、内存和已经记下的同步游标里最大的 seq；
     * 三者都没有时才退回会话列表的 latest_seq。登录后、WebSocket 尚未连上时到达的消息，
     * 会落在这个游标之后，由服务端补推。补齐成功后即使消息正文不写入历史缓存，也会推进同步游标，
     * 避免没有本地缓存的会话每次连接都从 0 把同一段历史再拉一遍。
     */
    async requestOfflineSync(): Promise<void> {
      try {
        const userId = useUserStore().loggedInUser?.user_id;
        if (!userId) return;
        const cursors = await this.collectSyncCursors(userId);
        const watch = this.collectWatch();
        websocketService.sendMessage({
          type: 'SYNC_REQUEST',
          message: {
            conversations: cursors,
            ...(watch ? { watch: [watch] } : {}),
          },
        }, { persist: false });
      } catch (error) {
        console.error('发送上线补齐请求失败:', error);
      }
    },

    async applyOfflineSync(payload: OfflineSyncPayload): Promise<void> {
      const revisions = payload?.revisions ?? [];
      const messages = payload?.messages ?? [];
      noteCountedMessages([...revisions, ...messages].map(message => message?.message_id));
      for (const revision of revisions) {
        await this.applySyncedRevision(revision);
      }
      const cacheable = await this.cacheableSyncConversations(messages, Boolean(payload?.truncated));
      for (const message of messages) {
        await this.absorbSyncedMessage(message, { cache: cacheable.has(message?.conversation_id) });
      }
      // 正文可以不落历史缓存（无本地历史或被截断时会留下空洞），但最大 seq 必须记下
      await this.rememberOfflineWatermarks(messages);
      applyOfflineConversations(payload?.conversations ?? [], this.activeConversationId);
      if (payload?.truncated) {
        await this.syncActiveConversation();
      }
    },

    async collectSyncCursors(userId: string): Promise<{ conversation_id: string, last_seq: string }[]> {
      const list = useListStore().conversations || [];
      const picked: Conversation[] = list.slice(0, 100);
      if (this.activeConversationId && !picked.some(item => item.conversation_id === this.activeConversationId)) {
        const active = list.find(item => item.conversation_id === this.activeConversationId);
        if (active) picked.push(active);
      }
      const cursors: { conversation_id: string, last_seq: string }[] = [];
      for (const conversation of picked) {
        if (!conversation?.conversation_id) continue;
        cursors.push({
          conversation_id: conversation.conversation_id,
          last_seq: await this.cursorFor(userId, conversation.conversation_id, conversation.latest_seq),
        });
      }
      if (this.activeConversationId && !cursors.some(item => item.conversation_id === this.activeConversationId)) {
        cursors.push({
          conversation_id: this.activeConversationId,
          last_seq: await this.cursorFor(userId, this.activeConversationId, '0'),
        });
      }
      return cursors;
    },

    async cursorFor(userId: string, conversationId: string, latestSeq?: string): Promise<string> {
      let stored = '0';
      try {
        stored = await dbService.getLastHistorySeq(userId, conversationId) || '0';
      } catch (error) {
        console.warn('读取本地消息游标失败:', error);
      }
      const watermark = (await this.readSyncCursor(userId, conversationId))?.seq || '0';
      const memory = this.messagesForConversation(conversationId)
        .reduce((max, message) => maxSeqId(max, message.seq_id), '0');
      const local = maxSeqId(maxSeqId(stored, memory), watermark);
      return local !== '0' ? local : (latestSeq || '0');
    },

    async readSyncCursor(userId: string, conversationId: string): Promise<SyncCursor | null> {
      if (typeof dbService.getSyncCursor !== 'function') return null;
      try {
        return await dbService.getSyncCursor(userId, conversationId);
      } catch (error) {
        console.warn('读取同步游标失败:', error);
        return null;
      }
    },

    /** 只向前推进。anchored 一旦为真就保持，表示这个会话已经做过历史分页，下次打开不再整页重拉 */
    async advanceSyncCursor(
      userId: string,
      conversationId: string,
      seq: string | undefined,
      options: { anchored?: boolean } = {},
    ) {
      if (!userId || !conversationId || typeof dbService.putSyncCursor !== 'function') return;
      const current = await this.readSyncCursor(userId, conversationId);
      const nextSeq = maxSeqId(current?.seq, seq);
      const anchored = Boolean(options.anchored || current?.anchored);
      if ((current?.seq || '0') === nextSeq && Boolean(current?.anchored) === anchored) return;
      if (nextSeq === '0' && !anchored) return;
      try {
        await dbService.putSyncCursor(userId, conversationId, { seq: nextSeq, anchored });
      } catch (error) {
        console.warn('保存同步游标失败:', error);
      }
    },

    async rememberOfflineWatermarks(messages: MessageResponse[]) {
      const userId = useUserStore().loggedInUser?.user_id;
      if (!userId) return;
      const seqs = new Map<string, string>();
      for (const message of messages) {
        if (!message?.conversation_id || !message.seq_id) continue;
        seqs.set(message.conversation_id, maxSeqId(seqs.get(message.conversation_id), message.seq_id));
      }
      for (const [conversationId, seq] of seqs) {
        await this.advanceSyncCursor(userId, conversationId, seq);
      }
    },

    async noteHistoryAnchor(userId: string, conversationId: string, messages: MessageResponse[]) {
      const seq = messages.reduce((max, message) => maxSeqId(max, message?.seq_id), '0');
      await this.advanceSyncCursor(userId, conversationId, seq, { anchored: true });
    },

    async knownLatestSeq(userId: string, conversationId: string): Promise<string> {
      const cursor = await this.readSyncCursor(userId, conversationId);
      const listed = (useListStore().conversations || [])
        .find(item => item?.conversation_id === conversationId)?.latest_seq;
      return maxSeqId(cursor?.seq, listed);
    },

    /** 本地已经有连续历史时，只补游标之后的新消息，不再把最近一页历史重新下载一遍 */
    async pullNewerThanLocal(conversationId: string, localLast: string): Promise<MessageResponse[]> {
      const userId = useUserStore().loggedInUser?.user_id;
      if (!userId || !localLast || localLast === '0' || typeof MessageService.syncMessages !== 'function') return [];
      const known = await this.knownLatestSeq(userId, conversationId);
      if (compareSeqId(known, localLast) <= 0) return [];
      try {
        const missed = await MessageService.syncMessages(conversationId, localLast);
        return Array.isArray(missed) ? missed : [];
      } catch (error) {
        console.error('增量补齐新消息失败:', error);
        return [];
      }
    },

    collectWatch(): { conversation_id: string, message_ids: string[] } | null {
      if (!this.activeConversationId) return null;
      const messageIds = this.messagesForConversation(this.activeConversationId)
        .map(message => message.message_id)
        .filter(messageId => Boolean(messageId) && !messageId.startsWith('local_'))
        .slice(-100);
      if (messageIds.length === 0) return null;
      return { conversation_id: this.activeConversationId, message_ids: messageIds };
    },

    async applySyncedRevision(message: MessageResponse): Promise<void> {
      if (!message?.message_id || !message.conversation_id) return;
      if (message.is_recalled || String(message.status) === '3') {
        await this.handleMessageRecalled({
          message_id: message.message_id,
          conversation_id: message.conversation_id,
          recall_user_id: message.sender_info?.user_id,
          seq_id: message.seq_id,
        }, { adjustUnread: false });
        return;
      }
      await this.handleMessageEdited(message);
    },

    /**
     * 哪些会话的补齐消息可以写进 IndexedDB。本地历史按“连续 + 待补区间”分页读取：
     * - 截断时（某会话只给了最新一段）写进去会在本地留下没有登记的空洞，往上翻会直接跳过被截掉的消息；
     *   正在看的会话还会让 HTTP 增量从最新一条开始而补不回空洞。所以截断时一条都不缓存，
     *   正在看的会话由 syncActiveConversation 按本地旧游标补全，其他会话打开时照旧从服务端分页。
     * - 本地还没有这个会话的历史时也不缓存（否则打开后只能翻到这几条），打开时照旧从服务端拉。
     *   这些消息的最大 seq 仍写入同步游标，下次连接从这里增量，而不是再从 0 全量拉取。
     */
    async cacheableSyncConversations(messages: MessageResponse[], truncated: boolean): Promise<Set<string>> {
      const result = new Set<string>();
      if (truncated) return result;
      const userId = useUserStore().loggedInUser?.user_id;
      if (!userId) return result;
      for (const conversationId of new Set(messages.map(message => message?.conversation_id).filter(Boolean))) {
        try {
          if (await dbService.getLastHistorySeq(userId, conversationId)) result.add(conversationId);
        } catch (error) {
          console.warn('读取本地消息游标失败:', error);
        }
      }
      return result;
    },

    async absorbSyncedMessage(message: MessageResponse, options: { cache?: boolean } = {}): Promise<void> {
      if (!message?.message_id || !message.conversation_id) return;
      const normalized = normalizeMessage(message);
      const isGroup = normalized.conversation_id.startsWith('grp_')
        || (normalized.conversation_id === this.activeConversationId && this.activeConversationIsGroup);
      try {
        if (options.cache !== false) await dbService.putHistory([wireCopy(normalized)]);
      } catch (error) {
        console.error('保存补齐消息失败:', error);
      }
      const [revealed] = await this.revealForDisplay([normalized]);
      const existing = this.findMessage(revealed.client_message_id, revealed.message_id);
      if (existing?.e2ee_status === 'decrypted' && needsDecrypt(normalized)) {
        this.keepLocalPlaintext(existing, revealed, isGroup);
      } else if (existing) {
        if (revealed.is_recalled || String(revealed.status) === '3') {
          await this.handleMessageRecalled({
            message_id: revealed.message_id,
            conversation_id: revealed.conversation_id,
            recall_user_id: revealed.sender_info?.user_id,
            seq_id: revealed.seq_id,
          }, { adjustUnread: false });
        } else if (revealed.edited) {
          await this.handleMessageEdited(revealed);
        } else {
          Object.assign(existing, revealed);
          onConversationMessage(existing, this.activeConversationId);
        }
      } else if (revealed.conversation_id === this.activeConversationId) {
        this.processMessages([revealed], isGroup);
        onConversationMessage(revealed, this.activeConversationId);
      } else {
        onConversationMessage(revealed, this.activeConversationId);
      }
      try {
        await this.sendDeliveredReceipts([revealed]);
      } catch (error) {
        console.error('补齐消息回执失败:', error);
      }
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
          await dbService.putHistory(missed.map(wireCopy));
          await this.sendDeliveredReceipts(missed);
          this.processMessages(await this.revealForDisplay(missed), this.activeConversationIsGroup);
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

/** 本地缓存只留密文。已经解开的气泡把信封写回 content，避免明文进 IndexedDB。 */
function wireCopy(message: MessageResponse): MessageResponse {
  if (!message.e2ee_envelope) return message;
  if (message.e2ee_status !== 'decrypted' && message.e2ee_status !== 'locked') return message;
  return {
    ...message,
    content: message.e2ee_envelope,
    is_encrypted: true,
    e2ee_status: undefined,
  };
}

function normalizeDeliveryState(state: DeliveryState | undefined): DeliveryState {
  return state || 'sent';
}

function normalizeMessage(message: MessageResponse): MessageResponse {
  const replyTo = message.reply_to
    && (message.reply_to.state === 'recalled' || message.reply_to.state === 'unavailable')
    ? { ...message.reply_to, preview_text: null }
    : message.reply_to;
  return {
    ...message,
    delivery_state: message.delivery_state || 'sent',
    readers: message.readers,
    reply_to: replyTo,
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
