export interface TargetInfo {
    id: string
    name: string
    avatar_url?: string
}

export interface MessagePreviewResponse {
    message_id: string
    /** 发送者 ID（旧会话记录可能没有） */
    sender_id?: string | null
    /** 发送者显示名：私聊对方为备注/昵称，其他为昵称 */
    sender_nickname?: string | null
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
    /** 有未读的 @我 消息（服务端按已读游标计算） */
    at_me?: boolean
    at_me_count?: number
    /** 最早一条未读 @我 消息 */
    at_me_message_id?: string
    /** 有未读的 @所有人。为 true 时列表显示“[@所有人]”；at_me 同时为 true */
    at_all?: boolean
    at_all_count?: number
    at_all_message_id?: string
    /** 未读里有直接 @ 到自己（可以和 @所有人在同一条消息）。为 true 时再显示 [有人@我] */
    at_personal?: boolean
    /** 未发送草稿的摘要；会话列表显示“[草稿] ”加这段文字 */
    draft_preview?: string | null
    notreadednumber?: number
}
