import api from './api.service';
import {type TokenResponse} from "@/type/User.ts"

export const authService = {
  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } catch (error) {
      console.error('登出失败:', error);
      throw error;
    }
  },
  async updateToken(
      refreshToken:string
      ): Promise<TokenResponse> {
      try {
        const response = await api.post<TokenResponse>('/auth/refresh',{refresh_token: refreshToken});
        return response.data;
      } catch (error: any) {
        console.error('登出失败:', error);
      throw error;
    }
  },
};