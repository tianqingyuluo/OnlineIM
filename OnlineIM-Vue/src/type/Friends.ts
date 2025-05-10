export type FriendInfo = {
    user_id: `usr_${string}`;
    username: string;
    nickname: string;
    avatar_url?: string;
    remark?: string;
    friend_group_id: `fgrp_${string}`;
    online_status: 'online' | 'offline';
};

export type Friend = {
    friendship_id: `rel_${string}`;
    friend_info: FriendInfo;
    created_at: string;
};

export type FriendsResponse = {
    friends: Friend[];
    total: number;
};