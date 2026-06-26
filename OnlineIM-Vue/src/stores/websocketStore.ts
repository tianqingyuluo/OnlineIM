import { defineStore } from 'pinia';
import { websocketService } from '@/services/websocket.service';

export const useWebSocketStore = defineStore('websocket', {
  state: () => ({
    connectionState: websocketService.connectionState,
  }),
  getters: {
    isConnected: () => websocketService.isConnected,
    isOnline: (state) => state.connectionState === 'online',
    isReconnecting: (state) => state.connectionState === 'reconnecting',
    isOffline: (state) => state.connectionState === 'offline',
  },
  actions: {
    connect() {
      websocketService.connect();
    },

    disconnect() {
      websocketService.disconnect();
    },

    sendMessage(message: { type: string, message: any }) {
      websocketService.sendMessage(message);
    },

    manualRetry() {
      websocketService.manualRetry();
    },
  },
});
