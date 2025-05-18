// src/services/groupsetting.service.ts
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
};