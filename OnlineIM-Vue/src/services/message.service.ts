import api from './api.service';
import type {
    MessageContextResponse,
    MessageHistoryResponse,
    MessageResponse,
} from '@/type/message.ts';
import { dbService } from "@/utils/indexedDB.ts";
import {useUserStore} from "@/stores/user.ts";

export const MessageService = {
    async getMessageHistory(
        conversation_id: string,
        seq_id?: string,
    ): Promise<MessageHistoryResponse> {
        const params = { seq_id };
        const response = await api.get(
            `/messages/${conversation_id}`,
            { params }
        );
        return normalizeHistoryResponse(response.data);
    },async getPrivateHistory(
        conversation_id: string,
        seq_id?: string,
    ): Promise<MessageHistoryResponse> {
        const params = { seq_id };
        const response = await api.get(
            `/messages/${conversation_id}`,
            { params }
        );
        return normalizeHistoryResponse(response.data);
    },
    async getMessageContext(
        conversationId: string,
        messageId: string,
        before = 20,
        after = 20,
    ): Promise<MessageContextResponse> {
        const response = await api.get<MessageContextResponse>(
            `/messages/${conversationId}/${messageId}/context`,
            { params: { before, after } },
        );
        return response.data;
    },
    //错误的put方法
    async putMessage(
        groupId: string,
        message_type: string,
        content: string,
        replyToMessageId?: string,
        clientMessageId: string = crypto.randomUUID(),
    ): Promise<MessageResponse> {
        const response = await api.post<MessageResponse>(
            '/messages/send',
            {
                target_id: groupId,
                message_type,
                content,
                reply_to_message_id: replyToMessageId,
                client_message_id: clientMessageId,
            }
        );
        return response.data;
    },
    async getHistoryByIndexDB(conversation_id: string, last_message_id?: string,before_message_id?: string): Promise<MessageHistoryResponse> {
        if (last_message_id==='0'){
            last_message_id=undefined;
        }
        const userStore = useUserStore();
        const userId = userStore.loggedInUser.user_id;
        const messages = await dbService.getHistory(userId, conversation_id,last_message_id, before_message_id);
        return { messages, has_more_before: messages.length === 50 };
    },

    async syncMessages(conversation_id: string, seq_id: string): Promise<MessageResponse[]> {
        const response = await api.get(
            `/messages/sync/${conversation_id}`,
            { params: { seq_id } }
        );
        return Array.isArray(response.data) ? response.data : response.data.messages || [];
    }
};
function normalizeHistoryResponse(data: MessageHistoryResponse | MessageResponse[]): MessageHistoryResponse {
    if (Array.isArray(data)) {
        return {
            messages: data,
            has_more_before: data.length >= 20,
            has_more_after: false,
        };
    }
    return data;
}
