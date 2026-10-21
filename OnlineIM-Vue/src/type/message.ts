import type { DeliveryState } from '@/utils/message-state'

export type ReplyState = 'active' | 'edited' | 'recalled' | 'unavailable';

export interface ReplyReference {
    message_id: string;
    seq_id: string;
    sender_id: string;
    sender_display_name: string;
    message_type: string;
    preview_text?: string | null;
    state: ReplyState;
}

export interface MessageContextResponse {
    target_message_id: string;
    messages: MessageResponse[];
    has_more_before: boolean;
    has_more_after: boolean;
}

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
    /** 群聊 @提及 的成员 ID（服务端已清洗，仅文本消息） */
    at_users?: string[] | null;
    status: number | string;
    delivery_state?: DeliveryState;
    timestamp: string;
    is_recalled: boolean;
    client_message_id: string;
    reply_to?: ReplyReference;
    /** 逐条转发的消息：原作者（转发链上最初的发送者） */
    forwarded_from?: ForwardedFrom | null;
    /** 文本消息被编辑过 */
    edited?: boolean;
    /** 最近一次编辑时间（格式同 timestamp） */
    edited_at?: string | null;
    /** 内容版本：从 1 开始，每次编辑 +1 */
    content_revision?: number;
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
    reply_to?: ReplyReference;
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

export interface MessageRecalledPayload {
    message_id: string;
    conversation_id: string;
    recall_user_id?: string;
    seq_id?: string;
    timestamp?: number;
}

export interface ForwardedFrom {
    user_id: string;
    display_name: string;
}

/** POST /messages/forward */
export interface MessageForwardRequest {
    source_conversation_id: string;
    message_ids: string[];
    /** 好友 user_id 或群 group_id */
    targets: string[];
    mode: 'separate' | 'merged';
    client_request_id: string;
}

export interface MessageForwardTargetResult {
    target_id: string;
    conversation_id: string;
    messages: MessageResponse[];
}

export interface MessageForwardResponse {
    mode: 'separate' | 'merged';
    results: MessageForwardTargetResult[];
}

/** PUT /messages/{message_id} 请求体 */
export interface MessageEditRequest {
    content: string;
    /** 群聊：编辑后的 @ 列表；省略表示保持原列表 */
    at_users?: string[];
}

/** WebSocket MESSAGE_EDITED 帧（message 与历史接口格式相同） */
export interface MessageEditedFrame {
    message: MessageResponse;
    at_me?: boolean;
}

/** 消息搜索：片段内命中的位置（UTF-16 下标） */
export interface SearchHighlight {
    start: number;
    length: number;
}

export interface MessageSearchConversation {
    conversation_id: string;
    type: 'private' | 'group';
    /** 私聊为对方的备注或昵称，群聊为群名 */
    name: string;
    avatar_url?: string;
}

export interface MessageSearchItem {
    message: MessageResponse;
    conversation: MessageSearchConversation;
    /** 命中片段（换行替换为空格，过长时首尾带“…”） */
    snippet: string;
    highlights: SearchHighlight[];
}

/** GET /api/v1/messages/search */
export interface MessageSearchResponse {
    keyword: string;
    conversation_id: string | null;
    results: MessageSearchItem[];
    next_cursor: string | null;
    has_more: boolean;
}

export interface MessageSearchParams {
    q: string;
    conversation_id?: string;
    cursor?: string;
    size?: number;
}
