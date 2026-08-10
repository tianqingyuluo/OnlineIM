import api from './api.service';
import type { FormContext } from 'vee-validate';
import { useUserStore } from '@/stores/user.ts';
import {type User}from '@/type/User.ts'
import axios from 'axios';
import { API_BASE_URL } from '@/config';



export const meService = {
    async me(formContext?: FormContext): Promise<User> {
        try {
            const response = await api.get<User>('/users/me');
            const userStore = useUserStore();
            const id =userStore.loggedInUser.user_id
            userStore.setLoggedInUser(response.data); // 更新 store 状态
            userStore.loggedInUser.user_id=id
            return response.data;
        } catch (error: any) {
            if (formContext && error.response?.data?.errors) {
                formContext.setErrors(error.response.data.errors);
            }
            throw error;
        }
    },

    /**
     * 更新当前用户信息
     * @param userData 要更新的用户数据
     * @param formContext 可选的表单上下文
     * @returns 更新后的用户信息
     */
    async updateMe(
        userData: Partial<Omit<User, 'user_id' | 'created_at' | 'token'>>,
        formContext?: FormContext
    ): Promise<User> {
        try {
            // 确保avatar_url是字符串而不是嵌套对象
            if (userData.avatar_url && typeof userData.avatar_url === 'object') {
                userData.avatar_url = userData.avatar_url.avatar_url;
            }
            const response = await api.put<User>('/users/me', userData);
            return response.data;
        } catch (error: any) {
            if (formContext && error.response?.data?.errors) {
                formContext.setErrors(error.response.data.errors);
            }
            throw error;
        }
    },

    async uploadAvatar(file: File) {
        try {
            const formData = new FormData();
            formData.append('file', file);
            
            const userStore = useUserStore();
            const instance = axios.create({
                baseURL: API_BASE_URL,
                headers: {
                    'Authorization': `Bearer ${userStore.token}`,
                }
            });
            
            const response = await instance.post<string>('/users/me/avatar', formData);
            return response.data;
        } catch (error: any) {
            throw error;
        }
    }

};