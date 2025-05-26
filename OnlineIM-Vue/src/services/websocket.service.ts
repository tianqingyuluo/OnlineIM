import { ref } from 'vue';
import { WS_API_URL } from '../../shared/config.ts';
import { useUserStore } from '@/stores/user';
import { useHistoryStore } from '@/stores/history'; // 导入 historyStore


class WebSocketService {
  private ws: WebSocket | null = null;
  public isConnected = ref(false);

  private messageHandlers: ((event: MessageEvent) => void)[] = [];
  private errorHandlers: ((event: Event) => void)[] = [];
  private openHandlers: ((event: Event) => void)[] = [];
  private closeHandlers: ((event: CloseEvent) => void)[] = [];

  connect(): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      console.log('WebSocket is already connected.');
      return;
    }

    const userStore = useUserStore();
    const token = userStore.token;

    // Assuming token is passed as a query parameter. Adjust if backend uses headers.
    const url = `${WS_API_URL}?token=${token}`;

    this.ws = new WebSocket(url);

    this.ws.onopen = (event) => {
      console.log('WebSocket connection opened:', event);
      this.isConnected.value = true;
      this.openHandlers.forEach(handler => handler(event));
    };

    this.ws.onmessage = (event) => {
      console.log('WebSocket message received:', event.data);
      try {
        const message = JSON.parse(event.data) ;
        // 检查消息类型，只将 PRIVATE_MESSAGE_RESPONSE 转发给 historyStore 处理
        if (message.type === 'PRIVATE_MESSAGE_RESPONSE') {
          const historyStore = useHistoryStore(); // 在这里获取 store 实例
          historyStore.handleWebSocketMessage(message);
        } else {
          console.log('Received unhandled message type:', message.type, message);
        }
      } catch (error) {
        console.error('处理 WebSocket 消息失败:', error);
      }
      // 仍然触发通用的 messageHandlers，如果需要的话
      this.messageHandlers.forEach(handler => handler(event));
    };

    this.ws.onerror = (event) => {
      console.log('WebSocket error:', event);
      this.isConnected.value = false;
      this.errorHandlers.forEach(handler => handler(event));
    };

    this.ws.onclose = (event) => {
      console.log('WebSocket connection closed:', event);
      this.isConnected.value = false;
      this.closeHandlers.forEach(handler => handler(event));
      // Implement auto-reconnect logic here if needed
    };
  }

  disconnect(): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.close();
    }
  }

  /**
   * 发送消息到 WebSocket 服务器
   * @param payload 包含 type 和 message 字段的对象
   */
  sendMessage(   payload: { type: string, message: any } ): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      try {
        const jsonMessage = JSON.stringify(payload);
        this.ws.send(jsonMessage);
        console.log('WebSocket message sent:', jsonMessage);
      } catch (error) {
        console.error('Failed to stringify message payload:', error);
      }
    } else {
      console.error('WebSocket is not connected. Cannot send message.');
    }
  }

  onMessage(handler: (event: MessageEvent) => void): () => void {
    this.messageHandlers.push(handler);
    return () => {
      this.messageHandlers = this.messageHandlers.filter(h => h !== handler);
    };
  }

  onError(handler: (event: Event) => void): () => void {
    this.errorHandlers.push(handler);
    return () => {
      this.errorHandlers = this.errorHandlers.filter(h => h !== handler);
    };
  }

  onOpen(handler: (event: Event) => void): () => void {
    this.openHandlers.push(handler);
    return () => {
      this.openHandlers = this.openHandlers.filter(h => h !== handler);
    };
  }

  onClose(handler: (event: CloseEvent) => void): () => void {
    this.closeHandlers.push(handler);
    return () => {
      this.closeHandlers = this.closeHandlers.filter(h => h !== handler);
    };
  }
}

export const websocketService = new WebSocketService();