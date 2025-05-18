import api from './api.service';
import type {MessageResponse, MessageWithTotal} from "@/type/message.ts";

export const MessageService = {
    async getMessageHistory(
        groupId: string,
        timestamp?: number,  // 新增可选的时间戳参数
    ): Promise<MessageWithTotal> {
        try {
            // 构造查询参数
            const params = {
                timestamp: timestamp?.toString(),
            };

            const response = await api.get<MessageWithTotal>(
                `/messages/group/${groupId}`,
                { params }  // 将参数附加到请求
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