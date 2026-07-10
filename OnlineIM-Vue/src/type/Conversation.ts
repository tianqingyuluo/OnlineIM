export interface TargetInfo {
    id: string
    name: string
    avatar_url?: string
}

export interface MessagePreviewResponse {
    message_id: string
    sender_nickname?: string
    content_preview: string
    message_type?: string
    timestamp: string
    is_recalled: boolean
}

export interface ReadStateResponse {
    conversation_id: string
    delivered_seq: string
    read_seq: string
    latest_seq: string
    unread_count: number
}

export interface MessageReader {
    user_id: string
    username?: string
    nickname?: string
    avatar_url?: string
}

export interface MessageReadersResponse {
    conversation_id: string
    message_id: string
    seq_id: string
    readers: MessageReader[]
    delivered_readers?: MessageReader[]
}

export interface Conversation {
    conversation_id: string
    type: 'private' | 'group'
    target_info: TargetInfo
    last_message: MessagePreviewResponse
    unread_count: number
    delivered_seq?: string
    read_seq?: string
    latest_seq?: string
    is_pinned?: boolean
    is_muted?: boolean
    last_activity_time: string
    is_at?: boolean
    notreadednumber?: number
}
