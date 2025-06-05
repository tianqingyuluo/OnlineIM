export type FriendInfo = {
    user_id: string;
    username: string;
    nickname: string;
    avatar_url?: string;
    remark?: string;
    friend_group_id?: string;
    online_status?: string;
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
  status: string;
  created_at: string;
}

export interface FriendRequestsResponse {
  requests: FriendRequest[];
  total: number;
}
export type FriendInFriendGroup = {
  friendship_id: string;
  user_id: string; 
  friend_group_id: string;
  created_at: string;
  username: string;
  nickname: string;
  avatar_url?: string;
  remark?: string;
  online_status?:string;
}