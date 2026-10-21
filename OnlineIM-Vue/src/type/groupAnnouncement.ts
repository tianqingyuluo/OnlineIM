export interface GroupAnnouncement{
    announcement_id: string;
    group_id: string;
    publisher_info: UserBriefResponse;
    title: string;
    content: string;
    pinned: boolean;
    created_at: string;
    updated_at: string;
    /** 当前用户是否已点“我知道了” */
    read_by_me?: boolean;
    /** 仅群主/管理员 */
    read_count?: number;
    unread_count?: number;
}
export interface AnnouncementReader {
    user_id: string;
    username?: string;
    nickname?: string;
    avatar_url?: string;
    read_at?: string;
}
export interface GroupAnnouncementReads {
    announcement_id: string;
    group_id: string;
    read_count: number;
    unread_count: number;
    readers: AnnouncementReader[];
    unread_users: AnnouncementReader[];
}
export interface UserBriefResponse {
    user_id: string;
    username: string;
    nickname: string;
    avatar_url: string;
}
export interface GroupAnnouncementRequest {
    title: string;
    content: string;
    pinned: boolean;
}