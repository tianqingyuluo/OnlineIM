export interface MessageResponse {
    message_id: string;
    conversation_id: string;
    sender_info: {
        user_id: string;
        nickname: string;
        avatar_url?: string;
    };
    message_type: string;
    seq_id: "string",
    content: any;
    mentioned_user_ids?: string[];
    status: "delivered" | "read" | "recalled";
    timestamp: string;
    is_recalled: boolean;
}

export interface MessageHistoryResponse {
    messages: MessageResponse[];
    has_more_before: boolean;
    has_more_after: boolean;
}