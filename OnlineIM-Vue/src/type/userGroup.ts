import type { FriendInFriendGroup} from "@/type/Friends.ts";

export interface UserGroupInfo {
    group_id: string,
    name: string,
    sort: number,
    createdAt: string
    friends: FriendInFriendGroup[]
}