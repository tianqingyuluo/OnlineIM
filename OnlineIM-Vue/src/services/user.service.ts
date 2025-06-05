import api from './api.service';
import type { FormContext } from 'vee-validate';
import {type User,type UserSearchResponse } from "@/type/User.ts";



export const searchService = {
  async searchUsers(
    query: string,
    page: number,
    formContext?: FormContext
  ): Promise<UserSearchResponse> {
    try {

      const response = await api.get<UserSearchResponse>(`/users/search/${encodeURIComponent(query)}/${page}`);
      return response.data;
    } catch (error: any) {
      if (formContext && error.response?.data?.errors) {
        formContext.setErrors(error.response.data.errors);
      }
      throw error;
    }
  }
};

export const userService = {
  async getUserById(userId: string) {
    try {
      const response = await api.get<User>(`/users/${userId}`);
      return response.data;
    } catch (error) {
      console.error('获取用户信息失败:', error);
      throw error;
    }
  }
};