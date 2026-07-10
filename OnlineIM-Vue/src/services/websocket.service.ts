import { computed, ref, type ComputedRef, type Ref } from 'vue';
import { WS_API_URL } from '../../shared/config.ts';
import { useUserStore } from '@/stores/user';
import { useHistoryStore } from '@/stores/history';
import { dbService } from '@/utils/indexedDB.ts';
import { toast } from 'vue-sonner';
import type {
  MessageAckPayload,
  MessageRecalledPayload,
  MessageResponse,
  ReadReceiptPayload,
  ReceiptPayload,
  ServerErrorPayload,
} from '@/type/message';
import {
  type ConnectionState,
  HEARTBEAT_INTERVAL_MS,
  ACK_MISS_THRESHOLD,
  OFFLINE_THRESHOLD,
  OUTBOUND_QUEUE_LIMIT,
  computeBackoff,
} from '@/utils/reconnect-utils';

export type { ConnectionState };

type JsonPayload = Record<string, unknown>;

export interface WebSocketPayload {
  type: string;
  message: JsonPayload;
}

interface ReceiptQueueRecord {
  id?: number;
  user_id: string;
  conversation_id: string;
  type: 'RECEIPT' | 'READ_RECEIPT';
  payload: JsonPayload;
  dedupe_key: string;
  created_at: number;
}

export class WebSocketService {
  private ws: WebSocket | null = null;

  public connectionState: Ref<ConnectionState> = ref('connecting');
  public isConnected: ComputedRef<boolean> = computed(() => this.connectionState.value === 'online');

  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private missedAckCount = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private isManualDisconnect = false;
  private hasConnectedBefore = false;

  private messageHandlers: ((event: MessageEvent) => void)[] = [];
  private errorHandlers: ((event: Event) => void)[] = [];
  private openHandlers: ((event: Event) => void)[] = [];
  private closeHandlers: ((event: CloseEvent) => void)[] = [];
  private reconnectHandlers: (() => void)[] = [];
  private isFlushing = false;
  private isFlushingReceipts = false;

  private setConnectionState(state: ConnectionState): void {
    const previous = this.connectionState.value;
    if (previous === state) return;
    this.connectionState.value = state;

    if (previous === 'online' && state === 'reconnecting') {
      toast.warning('连接已断开，正在重连');
    } else if (previous === 'reconnecting' && state === 'online') {
      toast.success('已重新连接');
    } else if (state === 'offline') {
      toast.warning('网络离线，将自动重连');
    }
  }

  connect(preserveOfflineState = false): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.isManualDisconnect = false;
    if (!preserveOfflineState) {
      this.setConnectionState(this.hasConnectedBefore ? 'reconnecting' : 'connecting');
    }

    const token = useUserStore().token;
    const socket = new WebSocket(`${WS_API_URL}?token=Bearer ${token}`);
    this.ws = socket;
    this.bindWsEvents(socket);
  }

  private bindWsEvents(socket: WebSocket): void {
    socket.onopen = (event) => {
      if (this.ws !== socket) return;
      this.setConnectionState('online');
      this.reconnectAttempts = 0;
      this.missedAckCount = 0;
      this.startHeartbeat();

      // 首次连接也要恢复上一次页面会话留下的消息/回执队列。
      void this.flushOutboundQueue();
      void this.flushReceiptQueue();
      if (this.hasConnectedBefore) {
        this.triggerReconnectSync();
        this.reconnectHandlers.forEach(handler => handler());
      }
      this.hasConnectedBefore = true;
      this.openHandlers.forEach(handler => handler(event));
    };

    socket.onmessage = (event) => {
      if (this.ws !== socket) return;
      try {
        const frame = JSON.parse(String(event.data)) as { type?: unknown; message?: unknown };
        const type = typeof frame.type === 'string' ? frame.type : '';
        const payload = isJsonPayload(frame.message)
          ? frame.message
          : isJsonPayload(frame) ? frame : {};

        if (type === 'HEARTBEAT_ACK') {
          this.missedAckCount = 0;
          return;
        }

        const historyStore = useHistoryStore();
        switch (type) {
          case 'MESSAGE_ACK':
            historyStore.handleMessageAck(payload as unknown as MessageAckPayload);
            void this.markOutboundSent(payload);
            break;
          case 'RECEIPT':
            historyStore.handleReceipt(payload as unknown as ReceiptPayload);
            break;
          case 'READ_RECEIPT':
            historyStore.handleReadReceipt(payload as unknown as ReadReceiptPayload);
            break;
          case 'PRIVATE_MESSAGE_RESPONSE':
            historyStore.handleWebSocketMessage(payload as unknown as MessageResponse);
            void this.markOutboundSent(payload);
            break;
          case 'GROUP_MESSAGE_RESPONSE':
            historyStore.handleGroupWebSocketMessage(payload as unknown as MessageResponse);
            void this.markOutboundSent(payload);
            break;
          case 'ERROR':
            historyStore.handleServerError(payload as ServerErrorPayload);
            toast.error((payload as ServerErrorPayload).message || '消息处理失败');
            break;
          case 'MESSAGE_RECALLED':
          case 'RECALL_MESSAGE_RESPONSE':
            void historyStore.handleMessageRecalled(payload as unknown as MessageRecalledPayload);
            break;
          default:
            break;
        }
      } catch (error) {
        console.error('处理 WebSocket 消息失败:', error);
      }

      this.messageHandlers.forEach(handler => handler(event));
    };

    socket.onerror = (event) => {
      if (this.ws !== socket) return;
      console.error('WebSocket error:', event);
      this.errorHandlers.forEach(handler => handler(event));
    };

    socket.onclose = (event) => {
      if (this.ws !== socket) return;
      this.ws = null;
      this.stopHeartbeat();
      this.closeHandlers.forEach(handler => handler(event));
      if (!this.isManualDisconnect) this.startReconnect();
    };
  }

  disconnect(): void {
    this.isManualDisconnect = true;
    this.stopHeartbeat();
    this.stopReconnectTimer();
    const socket = this.ws;
    this.ws = null;
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      socket.close();
    }
    this.setConnectionState('offline');
  }

  /**
   * 业务消息先写入 IndexedDB 出站队列，再尝试发送，避免“已发出但 ACK 前断线”丢失。
   * 心跳等控制帧通过 persist=false 直接发送，不进入业务队列。
   */
  sendMessage(payload: WebSocketPayload, options: { persist?: boolean } = {}): void {
    const persist = options.persist ?? payload.type !== 'HEARTBEAT';
    if (!persist) {
      this.transmit(payload);
      return;
    }

    void this.enqueueOutbound(payload).then(normalizedPayload => {
      if (normalizedPayload) this.transmit(normalizedPayload);
    });
  }

  sendReceipt(payload: ReceiptPayload): void {
    this.enqueueAndSendReceipt('RECEIPT', payload, `RECEIPT:${payload.message_id}`);
  }

  sendReadReceipt(payload: ReadReceiptPayload): void {
    this.enqueueAndSendReceipt('READ_RECEIPT', payload, `READ_RECEIPT:${payload.conversation_id}`);
  }

  manualRetry(): void {
    this.stopReconnectTimer();
    this.reconnectAttempts = 0;
    this.connect();
  }

  private transmit(payload: WebSocketPayload): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false;
    try {
      this.ws.send(JSON.stringify(payload));
      return true;
    } catch (error) {
      console.error('发送 WebSocket 消息失败:', error);
      return false;
    }
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.missedAckCount = 0;
    this.sendHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.missedAckCount >= ACK_MISS_THRESHOLD) {
        console.warn('连续未收到 HEARTBEAT_ACK，判定连接已死');
        this.stopHeartbeat();
        this.ws?.close();
        return;
      }
      this.sendHeartbeat();
    }, HEARTBEAT_INTERVAL_MS);
  }

  private sendHeartbeat(): void {
    this.missedAckCount++;
    this.sendMessage({ type: 'HEARTBEAT', message: {} }, { persist: false });
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private startReconnect(): void {
    if (this.isManualDisconnect) return;
    const delay = computeBackoff(this.reconnectAttempts);
    this.reconnectAttempts++;
    this.setConnectionState(this.reconnectAttempts >= OFFLINE_THRESHOLD ? 'offline' : 'reconnecting');
    this.stopReconnectTimer();
    this.reconnectTimer = setTimeout(() => {
      this.connect(this.connectionState.value === 'offline');
    }, delay);
  }

  private stopReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private async enqueueOutbound(payload: WebSocketPayload): Promise<WebSocketPayload | null> {
    try {
      const userId = useUserStore().loggedInUser.user_id;
      const message = payload.message;
      const conversationId = asString(message.conversation_id)
        || asString(message.group_id)
        || asString(message.target_id);
      if (!conversationId) {
        console.error('无法入队：缺少会话 ID');
        return null;
      }

      const clientLocalId = asString(message.client_message_id) || crypto.randomUUID();
      const existing = await dbService.getOutboundQueue(userId, conversationId);
      const duplicate = existing.some(item => item.client_local_id === clientLocalId);
      const pendingCount = existing.filter(item => item.status === 'pending').length;
      if (pendingCount >= OUTBOUND_QUEUE_LIMIT && !duplicate) {
        toast.error('消息过多，请稍后');
        return null;
      }

      const queuedPayload: JsonPayload = {
        ...message,
        client_message_id: clientLocalId,
      };
      const normalizedPayload: WebSocketPayload = { ...payload, message: queuedPayload };
      await dbService.addOutboundMessage({
        user_id: userId,
        conversation_id: conversationId,
        type: payload.type,
        payload: queuedPayload,
        client_local_id: clientLocalId,
        status: 'pending',
        created_at: Date.now(),
      });
      return normalizedPayload;
    } catch (error) {
      console.error('写入出站队列失败:', error);
      return null;
    }
  }

  private async flushOutboundQueue(): Promise<void> {
    if (this.isFlushing) return;
    this.isFlushing = true;
    try {
      const userId = useUserStore().loggedInUser.user_id;
      const allQueued = await dbService.getAllOutboundQueue(userId);
      const byConversation = new Map<string, typeof allQueued>();
      for (const item of allQueued) {
        const arr = byConversation.get(item.conversation_id) || [];
        arr.push(item);
        byConversation.set(item.conversation_id, arr);
      }
      for (const items of byConversation.values()) {
        items.sort((left, right) => left.created_at - right.created_at);
        for (const item of items) {
          if (item.status !== 'pending' || this.ws?.readyState !== WebSocket.OPEN) continue;
          this.transmit({ type: item.type, message: item.payload });
        }
      }
    } catch (error) {
      console.error('恢复出站队列失败:', error);
    } finally {
      this.isFlushing = false;
    }
  }

  private async enqueueAndSendReceipt(
    type: 'RECEIPT' | 'READ_RECEIPT',
    payload: ReceiptPayload | ReadReceiptPayload,
    dedupeKey: string,
  ): Promise<void> {
    const userId = useUserStore().loggedInUser.user_id;
    const conversationId = payload.conversation_id;
    const item: Omit<ReceiptQueueRecord, 'id'> = {
      user_id: userId,
      conversation_id: conversationId,
      type,
      payload: payload as unknown as JsonPayload,
      dedupe_key: dedupeKey,
      created_at: Date.now(),
    };

    try {
      const id = await dbService.enqueueReceipt(item);
      if (this.transmit({ type, message: item.payload })) {
        await dbService.deleteReceiptIfCurrent(userId, {
          id,
          dedupe_key: item.dedupe_key,
          payload: item.payload,
        });
      }
    } catch (error) {
      console.error('写入回执队列失败:', error);
    }
  }

  private async flushReceiptQueue(): Promise<void> {
    if (this.isFlushingReceipts || typeof dbService.getPendingReceipts !== 'function') return;
    this.isFlushingReceipts = true;
    try {
      const userId = useUserStore().loggedInUser.user_id;
      const receipts = await dbService.getPendingReceipts(userId) as ReceiptQueueRecord[];
      for (const receipt of receipts) {
        if (this.ws?.readyState !== WebSocket.OPEN) break;
        if (this.transmit({ type: receipt.type, message: receipt.payload })) {
          await dbService.deleteReceiptIfCurrent(userId, receipt);
        }
      }
    } catch (error) {
      console.error('恢复回执队列失败:', error);
    } finally {
      this.isFlushingReceipts = false;
    }
  }

  private async markOutboundSent(serverMessage: JsonPayload): Promise<void> {
    const clientLocalId = asString(serverMessage.client_message_id);
    if (!clientLocalId) return;
    try {
      await dbService.markOutboundSent(useUserStore().loggedInUser.user_id, clientLocalId);
    } catch (error) {
      console.error('标记出站消息已确认失败:', error);
    }
  }

  private triggerReconnectSync(): void {
    try {
      useHistoryStore().syncActiveConversation();
    } catch (error) {
      console.error('重连补齐触发失败:', error);
    }
  }

  onMessage(handler: (event: MessageEvent) => void): () => void {
    this.messageHandlers.push(handler);
    return () => { this.messageHandlers = this.messageHandlers.filter(item => item !== handler); };
  }

  onError(handler: (event: Event) => void): () => void {
    this.errorHandlers.push(handler);
    return () => { this.errorHandlers = this.errorHandlers.filter(item => item !== handler); };
  }

  onOpen(handler: (event: Event) => void): () => void {
    this.openHandlers.push(handler);
    return () => { this.openHandlers = this.openHandlers.filter(item => item !== handler); };
  }

  onClose(handler: (event: CloseEvent) => void): () => void {
    this.closeHandlers.push(handler);
    return () => { this.closeHandlers = this.closeHandlers.filter(item => item !== handler); };
  }

  onReconnect(handler: () => void): () => void {
    this.reconnectHandlers.push(handler);
    return () => { this.reconnectHandlers = this.reconnectHandlers.filter(item => item !== handler); };
  }
}

function isJsonPayload(value: unknown): value is JsonPayload {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

export const websocketService = new WebSocketService();
