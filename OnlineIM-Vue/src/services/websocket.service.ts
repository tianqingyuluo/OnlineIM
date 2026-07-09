import { ref, computed, type Ref, type ComputedRef } from 'vue';
import { WS_API_URL } from '../../shared/config.ts';
import { useUserStore } from '@/stores/user';
import { useHistoryStore } from '@/stores/history';
import { dbService } from '@/utils/indexedDB.ts';
import { toast } from 'vue-sonner';
import {
  type ConnectionState,
  HEARTBEAT_INTERVAL_MS,
  ACK_MISS_THRESHOLD,
  OFFLINE_THRESHOLD,
  OUTBOUND_QUEUE_LIMIT,
  computeBackoff,
} from '@/utils/reconnect-utils';

export type { ConnectionState };

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

  private setConnectionState(state: ConnectionState): void {
    const prev = this.connectionState.value;
    if (prev === state) return;
    this.connectionState.value = state;

    if (prev === 'online' && state === 'reconnecting') {
      toast.warning('连接已断开，正在重连');
    } else if (prev === 'reconnecting' && state === 'online') {
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

    const userStore = useUserStore();
    const token = userStore.token;
    const url = `${WS_API_URL}?token=Bearer ${token}`;

    const socket = new WebSocket(url);
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

      if (this.hasConnectedBefore) {
        this.flushOutboundQueue();
        this.triggerReconnectSync();
        this.reconnectHandlers.forEach(handler => handler());
      }
      this.hasConnectedBefore = true;

      this.openHandlers.forEach(handler => handler(event));
    };

    socket.onmessage = (event) => {
      if (this.ws !== socket) return;
      try {
        const message = JSON.parse(event.data);

        if (message.type === 'HEARTBEAT_ACK') {
          this.missedAckCount = 0;
          return;
        }

        if (message.type === 'PRIVATE_MESSAGE_RESPONSE') {
          const historyStore = useHistoryStore();
          historyStore.handleWebSocketMessage(message.message);
          this.markOutboundSent(message.message);
        }
        if (message.type === 'GROUP_MESSAGE_RESPONSE') {
          const historyStore = useHistoryStore();
          historyStore.handleGroupWebSocketMessage(message.message);
          this.markOutboundSent(message.message);
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

      if (!this.isManualDisconnect) {
        this.startReconnect();
      }
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

  sendMessage(payload: { type: string, message: any }): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      try {
        const jsonMessage = JSON.stringify(payload);
        this.ws.send(jsonMessage);
      } catch (error) {
        console.error('发送WebSocket消息失败:', error);
      }
    } else {
      this.enqueueOutbound(payload);
    }
  }

  manualRetry(): void {
    this.stopReconnectTimer();
    this.reconnectAttempts = 0;
    this.connect();
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.missedAckCount = 0;
    this.sendHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.missedAckCount >= ACK_MISS_THRESHOLD) {
        console.warn('连续未收到HEARTBEAT_ACK，判定连接已死');
        this.stopHeartbeat();
        if (this.ws) {
          this.ws.close();
        }
        return;
      }
      this.sendHeartbeat();
    }, HEARTBEAT_INTERVAL_MS);
  }

  private sendHeartbeat(): void {
    this.missedAckCount++;
    this.sendMessage({ type: 'HEARTBEAT', message: {} });
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

    if (this.reconnectAttempts >= OFFLINE_THRESHOLD) {
      this.setConnectionState('offline');
    } else {
      this.setConnectionState('reconnecting');
    }

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

  private async enqueueOutbound(payload: { type: string, message: any }): Promise<void> {
    try {
      const userStore = useUserStore();
      const userId = userStore.loggedInUser.user_id;
      const message = payload.message;
      const conversationId = message.conversation_id || message.target_id;
      if (!conversationId) {
        console.error('无法入队：缺少会话ID');
        return;
      }

      const existing = await dbService.getOutboundQueue(userId, conversationId);
      if (existing.length >= OUTBOUND_QUEUE_LIMIT) {
        toast.error('消息过多，请稍后');
        return;
      }

      const clientLocalId = message.client_message_id || crypto.randomUUID();
      const queuedPayload = {
        ...message,
        client_message_id: clientLocalId,
      };

      await dbService.addOutboundMessage({
        user_id: userId,
        conversation_id: conversationId,
        type: payload.type,
        payload: queuedPayload,
        client_local_id: clientLocalId,
        status: 'pending',
        created_at: Date.now(),
      });
    } catch (error) {
      console.error('入站队列出错:', error);
    }
  }

  private async flushOutboundQueue(): Promise<void> {
    if (this.isFlushing) return;
    this.isFlushing = true;
    try {
      const userStore = useUserStore();
      const userId = userStore.loggedInUser.user_id;
      const allQueued = await dbService.getAllOutboundQueue(userId);
      const byConversation = new Map<string, typeof allQueued>();
      for (const item of allQueued) {
        const arr = byConversation.get(item.conversation_id) || [];
        arr.push(item);
        byConversation.set(item.conversation_id, arr);
      }
      for (const [, items] of byConversation) {
        items.sort((a, b) => a.created_at - b.created_at);
        for (const item of items) {
          if (item.status === 'pending') {
            if (this.ws?.readyState !== WebSocket.OPEN) return;
            this.ws.send(JSON.stringify({ type: item.type, message: item.payload }));
          }
        }
      }
    } catch (error) {
      console.error('flush出站队列失败:', error);
    } finally {
      this.isFlushing = false;
    }
  }

  private async markOutboundSent(serverMessage: any): Promise<void> {
    try {
      const userStore = useUserStore();
      const userId = userStore.loggedInUser.user_id;
      const clientLocalId = serverMessage.client_message_id;
      if (!clientLocalId) return;
      await dbService.markOutboundSent(userId, clientLocalId);
    } catch (error) {
      console.error('标记出站消息已发送失败:', error);
    }
  }

  private triggerReconnectSync(): void {
    try {
      const historyStore = useHistoryStore();
      historyStore.syncActiveConversation();
    } catch (error) {
      console.error('重连补齐触发失败:', error);
    }
  }

  onMessage(handler: (event: MessageEvent) => void): () => void {
    this.messageHandlers.push(handler);
    return () => { this.messageHandlers = this.messageHandlers.filter(h => h !== handler); };
  }

  onError(handler: (event: Event) => void): () => void {
    this.errorHandlers.push(handler);
    return () => { this.errorHandlers = this.errorHandlers.filter(h => h !== handler); };
  }

  onOpen(handler: (event: Event) => void): () => void {
    this.openHandlers.push(handler);
    return () => { this.openHandlers = this.openHandlers.filter(h => h !== handler); };
  }

  onClose(handler: (event: CloseEvent) => void): () => void {
    this.closeHandlers.push(handler);
    return () => { this.closeHandlers = this.closeHandlers.filter(h => h !== handler); };
  }

  onReconnect(handler: () => void): () => void {
    this.reconnectHandlers.push(handler);
    return () => { this.reconnectHandlers = this.reconnectHandlers.filter(h => h !== handler); };
  }
}

export const websocketService = new WebSocketService();
