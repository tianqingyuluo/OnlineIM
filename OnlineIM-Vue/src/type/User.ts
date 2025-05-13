// 用户类型定义
export interface User {
    user_id: string;    // 用户唯一ID
    username: string;   // 登录用户名
    nickname: string;   // 显示昵称
    avatar_url: string ; // 头像URL
    region?: string;    // 可选-地区
    gender?: 0|1|2; // 可选-性别
    email?: string;     // 可选-邮箱
    phone?: string;     // 可选-手机
    signature?: string; // 可选-个性签名
    created_at?: string; // 可选-创建时间
}
export type UserSearchResult = {
    user_id: `usr_${string}`;
    username?: string;
    nickname: string;
    avatar_url?: string;
};

export type UserSearchResponse = {
    users: UserSearchResult[];
    total: number;
};
export interface TokenResponse {
    access_token: string;
    expires_in: number;
}