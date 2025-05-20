import api from './api.service';
import type {MessageResponse} from "@/type/message.ts";

export const MessageService = {
    async getMessageHistory(
        conversation_id: string,
        before_message_id?: string,
        after_message_id?: string,
    ): Promise<{messages: MessageResponse[], has_more_before: boolean, has_more_after: boolean}> {
        try {
            const params = {
                before_message_id,
                after_message_id,
            };
            const response = await api.get(
                `/messages/history/${conversation_id}`,
                { params }
            );
            return response.data;
        } catch (error: any) {
            throw error;
        }
    },
    async putMessage(
        groupId: string,
        content_type: number,
        content: string
    ): Promise<MessageResponse> {
        try {
            const response = await api.post<MessageResponse>(
                '/api/v1/messages/group',
                {
                    group_id: groupId,
                    content_type,
                    content
                }
            );
            return response.data;
        } catch (error: any) {
            throw error;
        }
    }
};