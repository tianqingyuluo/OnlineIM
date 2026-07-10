import type { DeliveryState } from '@/utils/message-state'

export interface MessageUserBrief {
    user_id: string;
    username?: string;
    nickname?: string;
    avatar_url?: string;
}

export interface MessageResponse {
    message_id: string;
    conversation_id: string;
    sender_info: MessageUserBrief;
    message_type: string;
    seq_id: string;
    content: string;
    mentioned_user_ids?: string[];
    status: number | string;
    delivery_state?: DeliveryState;
    timestamp: string;
    is_recalled: boolean;
    client_message_id: string;
    // 以下字段由前端在收到群聊已读回执后填充，不会破坏后端历史消息格式。
    readers?: MessageUserBrief[];
    readers_expanded?: boolean;
}

export interface PrivateMessageResponse {
    message_id: string;
    conversation_id: string;
    sender_id: string;
    receiver_id: string;
    message_type: string;
    seq_id: string;
    content: string;
    status: number | string;
    delivery_state?: DeliveryState;
    client_message_id: string;
    timestamp: string;
    created_at: string;
    updated_at: string;
}

/** 兼容历史代码中的旧命名。 */
export type privateMessageResponse = PrivateMessageResponse;

export interface MessageHistoryResponse {
    messages: MessageResponse[];
    has_more_before: boolean;
    has_more_after?: boolean;
}

export interface MessageAckPayload {
    client_message_id: string;
    message_id: string;
    conversation_id: string;
    seq_id: string;
    delivery_state: DeliveryState;
    server_time: number;
}

export interface ReceiptPayload {
    receipt_type: 'delivered';
    message_id: string;
    conversation_id: string;
    seq_id: string;
    receiver_id?: string;
}

export interface ReadReceiptPayload {
    conversation_id: string;
    reader_id?: string;
    read_seq: string;
}

export interface ServerErrorPayload {
    client_message_id?: string;
    code?: string;
    message?: string;
    details?: Record<string, unknown>;
}
