import api from './api.service';
import type { FormContext } from 'vee-validate';
import { useUserStore } from '@/stores/user.ts';
import {type User}from '@/type/User.ts'



export const meService = {
    async me(formContext?: FormContext): Promise<User> {
        try {
            const response = await api.get<User>('/users/me');
            const userStore = useUserStore();
            userStore.setLoggedInUser(response.data); // 更新 store 状态
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
            const response = await api.put<User>('/users/me', userData);
            return response.data;
        } catch (error: any) {
            if (formContext && error.response?.data?.errors) {
                formContext.setErrors(error.response.data.errors);
            }
            throw error;
        }
    },

};