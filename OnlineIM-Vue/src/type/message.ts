export interface MessageResponse {
    message_id: string;
    conversation_id: string;
    sender_info: {
        user_id: string;
        nickname: string;
        avatar_url?: string;
    };
    message_type: string;
    seq_id: string,
    content: any;
    mentioned_user_ids?:any;
    status: number;
    timestamp: string;
    is_recalled: boolean;
    client_message_id: string;
}
export interface privateMessageResponse {
    message_id: string;
    conversation_id: string;
    sender_id: string;
    receiver_id: string;
    message_type: string;
    seq_id: string,
    content: any;
    status: number;
    client_message_id: string;
    timestamp: string;
    creat_at:string;
    updated_at: string;
}
export interface MessageHistoryResponse {
    messages: MessageResponse[];
    has_more_before: boolean;
    has_more_after: boolean;
}