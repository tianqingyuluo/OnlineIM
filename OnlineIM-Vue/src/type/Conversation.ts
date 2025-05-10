export interface TargetInfo {
    id: string
    name: string
    avatar_url?: string
}

export interface LastMessage {
    message_id: string
    sender_nickname?: string
    content_preview: string
    timestamp: string
    is_recalled: boolean
}

export interface Conversation {
    conversation_id: string
    type: 'private' | 'group'
    target_info: TargetInfo
    last_message: LastMessage
    unread_count: number
    is_muted?: boolean
    is_pinned?: boolean
    last_activity_time: string
}

export type ConversationsResponse = {
    conversations: Conversation[];
    total: number;
};
