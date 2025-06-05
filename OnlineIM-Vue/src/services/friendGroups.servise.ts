import api from "@/services/api.service.ts";
import type {UserGroupInfo} from "@/type/userGroup.ts";
import { toast } from "vue-sonner";

export const friendGroupsService={
    async getFriendGroups(): Promise<UserGroupInfo[]>{
        try{
            const response= await api.get<UserGroupInfo[]>("/friend-groups")
            return response.data;
        }
        catch(e){
            throw new Error("获取好友分组列表失败"); // 处理错误
        }
    },
    
    async deleteFriendGroup(groupId: string): Promise<void> {
        try {
            await api.delete(`/friend-groups/${groupId}`);
        }
        catch(e) {
            const errorMessage = e.response?.data?.message || "删除好友分组失败";
            toast.error(errorMessage);
            throw new Error(errorMessage);
        }
    },
    
    async createFriendGroup(name: string): Promise<UserGroupInfo> {
        try {
            const response = await api.post<UserGroupInfo>("/friend-groups", { name });
            return response.data;
        }
        catch(e) {
            const errorMessage = e.response?.data?.message || "创建好友分组失败";
            toast.error(errorMessage);
            throw new Error(errorMessage);
        }
    },
    
    async updateFriendGroup(groupId: string, name: string): Promise<UserGroupInfo> {
        try {
            const response = await api.put<UserGroupInfo>(`/friend-groups/${groupId}`, { name });
            return response.data;
        }
        catch(e) {
            const errorMessage = e.response?.data?.message || "更新好友分组失败";
            toast.error(errorMessage);
            throw new Error(errorMessage);
        }
    }
}