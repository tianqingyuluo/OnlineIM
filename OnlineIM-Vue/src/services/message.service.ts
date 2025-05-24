import api from './api.service';
import type {MessageResponse} from "@/type/message.ts";
import { dbService } from "@/utils/indexedDB.ts";
import {useUserStore} from "@/stores/user.ts";

export const MessageService = {
    async getMessageHistory(
        conversation_id: string,
        seq_id?: string,
    ): Promise<{messages: MessageResponse[], has_more_before: boolean, has_more_after: boolean}> {
        try {
            const params = {
                seq_id:seq_id,
            };
            const response = await api.get(
                `/messages/${conversation_id}`,
                { params }
            );
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },//错误的put方法
    async putMessage(
        groupId: string,
        message_type: string,
        content: string,
        quoteMessageId?: string,
    ): Promise<MessageResponse> {
        try {
            const response = await api.post<MessageResponse>(
                '/api/v1/messages/group',
                {
                    target_id: groupId,
                    message_type: message_type,
                    content: content,
                    quote_message_id: quoteMessageId,

                }
            );
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },
    async getHistoryByIndexDB(conversation_id: string, before_message_id?: string): Promise<{messages: MessageResponse[], has_more_before: boolean}> {
        const userStore = useUserStore();
        const userId = userStore.loggedInUser.user_id;
        const messages = await dbService.getHistory(userId, conversation_id, before_message_id);
        return { messages, has_more_before: messages.length === 50 };
    }
};