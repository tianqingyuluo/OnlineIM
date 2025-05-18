export type FriendInfo = {
    user_id: `usr_${string}`;
    username: string;
    nickname: string;
    avatar_url?: string;
    remark?: string;
    friend_group_id?: string;
    online_status?: 'online' | 'offline';
};

export type Friend = {
    friendship_id: string;
    friend_info: FriendInfo;
    created_at: string;
};

export type FriendsResponse = {
    friends: Friend[];
    total: number;
};

export interface FriendRequest {
  request_id: string;
  sender_info: {
    user_id: string;
    nickname: string;
    avatar_url?: string;
  };
  message?: string;
  status: 'pending' | 'accepted' | 'rejected';
  created_at: string;
}

export interface FriendRequestsResponse {
  requests: FriendRequest[];
  total: number;
}