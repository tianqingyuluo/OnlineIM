import api from './api.service';
import { toast } from 'vue-sonner';

export const groupMembersService = {
  // 移除群成员
  async removeMember(groupId: string, memberId: string): Promise<void> {
    try {
      await api.delete(`/groups/${groupId}/members/${memberId}`);
      toast.success('成员移除成功');
    } catch (error) {
      console.error('移除成员失败:', error);
      toast.error('移除成员失败');
      throw error;
    }
  },

  // 设置管理员
  async setAdmin(groupId: string, memberId: string): Promise<void> {
    try {
      await api.put(`/groups/${groupId}/admins/${memberId}`);
      toast.success('设置管理员成功');
    } catch (error) {
      console.error('设置管理员失败:', error);
      toast.error('设置管理员失败');
      throw error;
    }
  },

  // 取消管理员
  async removeAdmin(groupId: string, memberId: string): Promise<void> {
    try {
      await api.delete(`/groups/${groupId}/admins/${memberId}`);
      toast.success('取消管理员成功');
    } catch (error) {
      console.error('取消管理员失败:', error);
      toast.error('取消管理员失败');
      throw error;
    }
  },

  // 更新成员昵称
  async updateNickname(groupId: string, memberId: string, nickname: string): Promise<void> {
    try {
      await api.put(`/groups/${groupId}/members/${memberId}/nickname`, { nickname });
      toast.success('昵称更新成功');
    } catch (error) {
      console.error('更新昵称失败:', error);
      toast.error('更新昵称失败');
      throw error;
    }
  },

  // 禁言成员
  async muteMember(groupId: string, memberId: string, duration: number): Promise<void> {
    try {
      await api.put(`/groups/${groupId}/members/${memberId}/mute`, { duration });
      toast.success('禁言设置成功');
    } catch (error) {
      console.error('禁言设置失败:', error);
      toast.error('禁言设置失败');
      throw error;
    }
  },

  // 取消禁言
  async unmuteMember(groupId: string, memberId: string): Promise<void> {
    try {
      await api.delete(`/groups/${groupId}/members/${memberId}/mute`);
      toast.success('取消禁言成功');
    } catch (error) {
      console.error('取消禁言失败:', error);
      toast.error('取消禁言失败');
      throw error;
    }
  },

  // 转让群主
  async transferOwnership(groupId: string, newOwnerId: string): Promise<void> {
    try {
      await api.put(`/groups/${groupId}/owner`, { new_owner_id: newOwnerId });
      toast.success('群主转让成功');
    } catch (error) {
      console.error('群主转让失败:', error);
      toast.error('群主转让失败');
      throw error;
    }
  }
};