// 基础类型定义
type UUID = string;  // 格式如: usr_xxx 或 grp_xxx
type ISO8601DateTime = string;
type GroupRole = string;

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
    create_at: string;
}


export interface GroupMemberAll {
    "user_info": {
        "user_id": string
        "username": string
        "nickname": string
        "avatar_url": string
    }
    group_nickname: string
    "role": string;
    "is_muted" : boolean
    "mute_end_time": string
    "joined_at": string
}
export interface GroupMembersResponse {
    members: GroupMemberAll[];
    total: number;
}
export interface GroupJoinRequestResponse {
    
    requestID:string
    groupID:string
    groupName:string
    userInfo:
        {
            username:string
            nickname:string
            avatarUrl:string
            userID:string
        }
    inviterInfo:
        {
        username:string
        nickname:string
        avatarUrl:string
        userID:string
        }
    message:string
    status:string
    createdAt:string
    
} 