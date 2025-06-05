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

export interface Conversation {
    conversation_id: string
    type: 'private' | 'group'
    target_info: TargetInfo
    last_message: MessagePreviewResponse
    unread_count: number
    is_pinned?: boolean//知道
    is_muted?: boolean//免打扰
    last_activity_time: string
    is_at?: boolean // 是否有@消息
    notreadednumber?: number // 未读消息数量
}