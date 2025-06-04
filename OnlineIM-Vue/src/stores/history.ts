import { defineStore } from 'pinia';
import type { MessageResponse } from '@/type/message';
import { dbService } from '@/utils/indexedDB';
import { MessageService } from '@/services/message.service';
import {useUserStore} from "@/stores/user.ts";
import { websocketService } from '@/services/websocket.service';
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

// 新增接口定义
interface PendingMessageInfoItem {
  clientId: string;    // 唯一标识
  content: string;
  timestamp: string;
  createAt: string;
  conversationId: string;
  timeoutId?: NodeJS.Timeout;
  isTimeout?: boolean;
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
    pendingMessagesInfo: [] as PendingMessageInfoItem[],

  }),

  actions: {
    generateClientId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substr(2, 9);
    },
    async init() {

      this.pendingMessagesInfo.forEach(item => {
        if (item.timeoutId) clearTimeout(item.timeoutId);
      });
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
      console.log("开始初始化历史记录");
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
        console.log("before_message_id", before_message_id);
        let shouldFetch = this.shouldFetchFromServer(conversationId, before_message_id);
        console.log("是否从服务器拉数据",shouldFetch);
        let messages: any[] = []; // 使用 any 暂时兼容不同类型
        let has_more = false;

        if (shouldFetch) {
          let response;
          if (isGroup) {
            response = await MessageService.getMessageHistory(
                conversationId,
                before_message_id
            );
          } else {
            response = await MessageService.getPrivateHistory(
                conversationId,
                before_message_id
            );
          }
          console.log("服务器返回数据", response);
          messages = response;
          if (before_message_id!==undefined)
          {has_more = before_message_id.localeCompare(messages[messages.length - 1].seq_id) > 0;}
          else{
            has_more = true;
          }
          if (messages.length > 0) {
            // 注意：updatePendingRanges 和 putHistory 可能需要根据消息类型调整
            await this.updatePendingRanges(conversationId, messages as any[]);
            console.log("开始推送历史记录");
            await dbService.putHistory(messages as any[]);
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
            if (this.pendingMessages[conversationId]) {
              has_more = true;
              this.lastHistorySeqId = this.pendingMessages[conversationId].ranges[-1].minSeqId;
            }

          }
        }

        this.processMessages(messages as any[], isGroup, before_message_id);
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
      if (!newMessages || newMessages.length === 0) return;
      
      // 1. 获取当前最新记录 - 添加空值检查
      const lastSeq = await dbService.getLastHistorySeq(
        this.pendingMessages[conversationId]?.userId || '',
        conversationId
      );
      
      // 2. 对新消息排序（升序）- 添加防御性检查
      const sortedMessages = [...newMessages].sort((a, b) => {
        // 确保 seq_id 存在
        const aSeq = a?.seq_id || '0';
        const bSeq = b?.seq_id || '0';
        return aSeq.localeCompare(bSeq);
      });
      
      // 检查排序后数组是否为空
      if (sortedMessages.length === 0) return;
      
      // 3. 判断是否需要创建顶部空洞 - 添加防御性检查
      const newEarliest = sortedMessages[0]?.seq_id;
      if (newEarliest && lastSeq && newEarliest.localeCompare(lastSeq) > 0) {
        await this.addPendingRange(
          this.pendingMessages[conversationId]?.userId || '',
          conversationId,
          lastSeq,
          newEarliest
        );
      }
      
      // 4. 原有区间填补逻辑 - 添加空值检查
      const pendingInfo = this.pendingMessages[conversationId];
      if (!pendingInfo || !pendingInfo.ranges) return;
      
      const newMin = sortedMessages[0]?.seq_id || '0';
      const newMax = sortedMessages[sortedMessages.length - 1]?.seq_id || '0';
      
      // 使用数组副本来避免修改原数组时的迭代问题
      const rangesCopy = [...pendingInfo.ranges];
      for (const range of rangesCopy) {
        if (!range?.minSeqId || !range?.maxSeqId) continue;
        
        if (
          newMin.localeCompare(range.minSeqId) <= 0 &&
          newMax.localeCompare(range.maxSeqId) >= 0
        ) {
          // 完全填补，删除区间
          if (range.id) await dbService.deletePendingRange(range.id);
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
      // 1. 空值检查
      if (!messages || messages.length === 0) return;
    
      // 2. 安全排序 - 添加防御性检查
      const sorted = [...messages].sort((a, b) => {
        // 确保 seq_id 存在，不存在则放到最后
        const aSeq = a?.seq_id || '999999999999'; // 使用一个很大的值作为默认
        const bSeq = b?.seq_id || '999999999999';
        return aSeq.localeCompare(bSeq);
      }).filter(msg => msg?.seq_id); // 过滤掉无效消息
    
      // 3. 检查排序后是否还有有效消息
      if (sorted.length === 0) {
        console.warn('processMessages: 排序后没有有效消息');
        return;
      }
    
      try {
        // 4. 更新消息列表 - 添加类型安全
        if (isGroup) {
          this.groupMessages = beforeSeqId 
            ? [...sorted, ...(this.groupMessages || [])] 
            : sorted;
        } else {
          this.chatMessages = beforeSeqId 
            ? [...sorted, ...(this.chatMessages || [])] 
            : sorted;
        }
    
        console.log("当前历史记录", this.groupMessages);
    
        // 5. 缓存头像 - 改进异步处理
        const cachePromises = messages.map(msg => {
          if (!msg?.sender_info?.avatar_url) return Promise.resolve();
          
          const avatarUrl = msg.sender_info.avatar_url;
          return dbService.addImageFromUrl(avatarUrl).catch(error => {
            console.error('缓存头像失败:', error, 'URL:', avatarUrl);
          });
        });
    
        // 可以等待所有缓存完成（可选）
        // await Promise.all(cachePromises);
        
      } catch (error) {
        console.error('处理消息时发生错误:', error);
      }
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

















    async updateMessageTimestamp(clientId: string) {
      const index = this.pendingMessagesInfo.findIndex(
          item => item.clientId === clientId
      );

      if (index === -1) {
        console.log('更新消息时间戳 - 未找到匹配消息');
        return;
      }

      const message = this.pendingMessagesInfo[index];

      // 清除旧定时器
      if (message.timeoutId) clearTimeout(message.timeoutId);

      // 更新字段
      message.timestamp = new Date().toISOString();
      message.isTimeout = false;

      // 设置新定时器
      message.timeoutId = setTimeout(() => {
        message.isTimeout = true;
        this.pendingMessagesInfo = [...this.pendingMessagesInfo];
      }, 3000);

      // 触发响应式更新
      this.pendingMessagesInfo.splice(index, 1, message);
    },

    // 新增：添加待发送消息内容（使用 clientId）
    addPendingMessageContent(content: string, conversationId: string) {
      const clientId = this.generateClientId();
      const createAt = new Date().toISOString();

      const messageInfo: PendingMessageInfoItem = {
        clientId,
        content,
        timestamp: createAt,
        createAt,
        conversationId,
        isTimeout: false,
        timeoutId: setTimeout(() => {
          const index = this.pendingMessagesInfo.findIndex(
              item => item.clientId === clientId
          );
          if (index !== -1) {
            this.pendingMessagesInfo[index].isTimeout = true;
            this.pendingMessagesInfo = [...this.pendingMessagesInfo];
          }
        }, 3000)
      };

      this.pendingMessagesInfo.push(messageInfo);
      return clientId;
    },


    handleWebSocketMessage(message: MessageResponse) {

      console.log('处理WebSocket消息 - 收到消息:', {
        clientId: message.client_message_id,
        content: message.content
      });

      const index = this.pendingMessagesInfo.findIndex(
          item => item.clientId === message.client_message_id
      );
      console.log('找到的索引:', index);
      console.log('找到的消息:', this.pendingMessagesInfo);
      if (index !== -1) {
        if (this.pendingMessagesInfo[index].timeoutId) {
          clearTimeout(this.pendingMessagesInfo[index].timeoutId);
        }
        this.pendingMessagesInfo.splice(index, 1);

        // 更新正式消息
        const tempIndex = this.chatMessages.findIndex(
            msg => msg.client_message_id === message.client_message_id
        );
        if (tempIndex !== -1) {
          this.chatMessages[tempIndex] = message;
        }
      } else {
        this.chatMessages.push(message);
      }
    },
    handleGroupWebSocketMessage(message: MessageResponse) {

      console.log('处理WebSocket消息 - 收到消息:', {
        clientId: message.client_message_id,
        content: message.content
      });

      const index = this.pendingMessagesInfo.findIndex(
          item => item.clientId === message.client_message_id
      );
      console.log('找到的索引:', index);
      console.log('找到的消息:', this.pendingMessagesInfo);
      if (index !== -1) {
        if (this.pendingMessagesInfo[index].timeoutId) {
          clearTimeout(this.pendingMessagesInfo[index].timeoutId);
        }
        this.pendingMessagesInfo.splice(index, 1);

        // 更新正式消息
        const tempIndex = this.groupMessages.findIndex(
            msg => msg.client_message_id === message.client_message_id
        );
        if (tempIndex !== -1) {
          this.groupMessages[tempIndex] = message;
        }
      } else {
        this.groupMessages.push(message);
      }
    },
    sendMessage(conversationId: string, receiverId: string,
       messageType: string, content: string,isGroup:boolean,atUsers?:string[]) {
      
      let clientId=  this.addPendingMessageContent(content, conversationId);
      const tempMessage: MessageResponse = {
        message_id: Date.now().toString(),
        conversation_id: conversationId,
        sender_info: useUserStore().loggedInUser || { user_id: '', username: '未知用户', avatar_url: '' },
        message_type: messageType,
        content: content,
        timestamp: new Date().toISOString(),
        seq_id: '',
        status: 0,
        is_recalled: false,
        client_message_id: clientId
      };

      if (isGroup) {
        this.groupMessages.push(tempMessage);
      } else {
        this.chatMessages.push(tempMessage);
      }


      const websocketMessage: any = {
        conversation_id: tempMessage.conversation_id,
        sender_id: useUserStore().loggedInUser?.user_id || '',
        receiver_id: receiverId,
        message_type: messageType,
        content: content,
        client_message_id: clientId
      };
      if (isGroup){
        websocketMessage.group_id=conversationId
      }
      console.log(JSON.stringify(websocketMessage));
      if (atUsers) {
        websocketMessage.at_users = atUsers;
      }

      console.log('发送消息 - 临时消息时间戳:', tempMessage.timestamp);
      if (!isGroup) {
        websocketService.sendMessage({type: 'PRIVATE_MESSAGE_REQUEST', message: websocketMessage})
      }
      else {
        websocketService.sendMessage({type: 'GROUP_MESSAGE_REQUEST', message: websocketMessage})
      }

    }
  },

  getters: {
    hasPendingMessages: (state) => (conversationId: string) => {
      return !!state.pendingMessages[conversationId]?.ranges?.length;
    },

    isMessagePending: (state) => (clientId: string) => {
      return state.pendingMessagesInfo.some(item => item.clientId === clientId);
    },

    isMessageTimeout: (state) => (clientId: string) => {
      const message = state.pendingMessagesInfo.find(
          item => item.clientId === clientId
      );
      return message?.isTimeout || false;
    }


  }
});