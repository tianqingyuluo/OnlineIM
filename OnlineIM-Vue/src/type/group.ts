// 基础类型定义
type UUID = string;  // 格式如: usr_xxx 或 grp_xxx
type ISO8601DateTime = string;
type GroupRole = 'owner' | 'admin' | 'member';

// 群组成员简略信息
export interface GroupMemberBrief {
    user_id: UUID;
    username: string;
    avatar_url?: string;
    role: GroupRole;
    join_time: ISO8601DateTime;
}

export interface CreateGroupResponse {
    group_id: UUID;
    name: string;
    owner_id: UUID;
    avatar_url?: string;
    description?: string;
    created_at: ISO8601DateTime;
    member_count: number;
}

// 6.2 搜索群组
export interface GroupSearchResult {
    group_id: UUID;
    name: string;
    avatar_url?: string;
    description?: string;
    member_count: number;
}

export interface GroupSearchResponse {
    groups: GroupSearchResult[];
    total: number;
}

// 6.3 获取群组详细信息
export interface GroupDetail {
    group_id: UUID;
    name: string;
    owner_id: UUID;
    avatar_url?: string;
    description?: string;
    announcement?: string; // 群公告
    created_at: ISO8601DateTime;
    member_count: number;
    my_role: GroupRole;
    members?: GroupMemberBrief[]; // 可选，完整成员列表
    settings?: {
        join_approval_required: boolean; // 是否需要加群审批
        invite_only: boolean; // 是否仅限邀请加入
        message_history_visible: boolean; // 新成员是否可见历史消息
    };
}

// 群组列表项 (用于列表展示)
export interface GroupListItem {
    group_id: UUID;
    name: string;
    avatar_url?: string;
    last_message?: string;
    unread_count?: number;
    updated_at?: ISO8601DateTime;
}

export interface GroupResponse {
    group_id: UUID;
    name: string;
    owner_id: UUID;
    avatar_url?: string;
    description?: string;
    announcement?: string;
    member_count: number;
    my_role: GroupRole;
    created_at: ISO8601DateTime;
}

export interface JoinedGroupsResponse {
    groups: GroupResponse[];
    total: number;
}