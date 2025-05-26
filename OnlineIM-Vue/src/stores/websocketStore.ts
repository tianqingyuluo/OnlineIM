import { defineStore } from 'pinia';
import { websocketService } from '@/services/websocket.service';

export const useWebSocketStore = defineStore('websocket', {
  state: () => ({
    // 可以直接使用 service 中的响应式状态
    isConnected: websocketService.isConnected,
  }),
  actions: {
    /**
     * 连接 WebSocket
     */
    connect() {
      websocketService.connect();
    },

    /**
     * 断开 WebSocket 连接
     */
    disconnect() {
      websocketService.disconnect();
    },

    /**
     * 发送消息
     * @param message 要发送的消息字符串
     */
    sendMessage(message: string) {
      websocketService.sendMessage(message);
    },

    // 可以选择在这里添加消息处理逻辑，或者让组件直接使用 service 的 onMessage 方法
    // 例如：
    // onMessage(handler: (event: MessageEvent) => void) {
    //   return websocketService.onMessage(handler);
    // }
  },
});