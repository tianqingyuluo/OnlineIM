import api from './api.service';

export const authService = {
  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } catch (error) {
      console.error('登出失败:', error);
      throw error;
    }
  }
};