// 基础类型定义
type UUID = string;  // 格式如: usr_xxx 或 grp_xxx
type ISO8601DateTime = string;
type GroupRole = 'owner' | 'admin' | 'member';

export interface GroupMember {
  user_id: UUID;
  username: string;
  avatar_url?: string;
  role: GroupRole;
  joined_at: ISO8601DateTime;
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
    // 其他群组设置，如加群验证方式等
}

export interface JoinedGroupsResponse {
    groups: GroupResponse[];
    total: number;
}