// src/services/login.service.ts
import api from './api.service';
import { useUserStore } from '@/stores/user';
import { toast } from 'vue-sonner';
import router from "@/router";

export const LoginService = {
    validationRules: {
        username: {
            min: 2,
            max: 20,
            messages: {
                min: '用户名至少2个字符',
                max: '用户名不能超过20个字符'
            }
        },
        password: {
            min: 6,
            max: 20,
            messages: {
                min: '密码至少需要6位',
                max: '密码不能超过20位'
            }
        }
    },

    async login(
        credentials: { username: string; password: string }
    ) {
        const userStore = useUserStore();

        try {
            console.log('准备发送请求...');
             const response = await api.post('/auth/login', credentials);
            if (response.data.access_token) {
                // 更新用户状态

                userStore.setLoggedInUser({
                    user_id: response.data.user_info.user_id, // 使用 user_id
                    username: response.data.user_info.username,
                    nickname: response.data.user_info.nickname,
                    avatar_url: response.data.user_info.avatar_url || null, // 使用 avatar_url
                });
                userStore.loggedInUser.user_id=response.data.user_info.user_id
                userStore.token = response.data.access_token
                await userStore.updateToken()
                console.log("token:\n"+response.data.access_token);

                // 显示欢迎消息
                toast.success('登录成功', {
                    description: `欢迎回来, ${response.data.user_info.nickname || response.data.user_info.username}`,
                });

                // 延迟1秒后跳转
                await new Promise(resolve => setTimeout(resolve, 1000));
                await router.push('/main/chat');
            }
            return response;
        } catch (error: any) {
        }
    }
};