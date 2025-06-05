
import api from './api.service';
import { toast } from 'vue-sonner';
import type { GroupSetting } from '@/type/groupsetting';

export const GroupSettingService = {
    async getGroupSetting(groupId: string): Promise<GroupSetting> {
        try {
            const response = await api.get(`/groups/${groupId}/settings`);
            return response.data;
        } catch (error) {
            toast.error('获取群组设置失败');
            throw error;
        }
    },
    
    async updateGroupSetting(groupId: string, settings: object): Promise<GroupSetting> {
        try {
            const response = await api.put(`/groups/${groupId}/settings`, settings);
            toast.success('群组设置更新成功');
            return response.data;
        } catch (error) {
            toast.error('更新群组设置失败');
            throw error;
        }
    }
};