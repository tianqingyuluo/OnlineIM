export interface GroupAnnouncement{
    announcement_id: string;
    group_id: string;
    publisher_info: UserBriefResponse;
    title: string;
    content: string;
    pinned: boolean;
    created_at: string;
    updated_at: string;
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