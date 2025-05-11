// src/services/login.service.ts
import api from './api.service';
import { useUserStore } from '@/stores/user';
import { toast } from 'vue-sonner';

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

    /**
     * 登录方法（集成完整错误处理）
     * @param credentials 登录凭证
     * @param router
     */
    async login(
        credentials: { username: string; password: string },
        router
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
            const errorMsg = error.message;
            toast.error('登录失败', {
                description: errorMsg
            });
            // // 处理字段级验证错误（400）
            // if (error.code === 'VALIDATION_ERROR' && formContext) {
            //     Object.entries(error.details || {}).forEach(([field, message]) => {
            //         formContext.setFieldError(field, message as string);
            //     });
            //     return;
            // }
            //
            // // 处理业务特定错误
            // switch (error.code) {
            //     case 'AUTH_FAILED':
            //         toast.error('登录失败', {
            //             description: '用户名或密码错误'
            //         });
            //         break;
            //
            //     case 'ACCOUNT_LOCKED':
            //         toast.error('账户已锁定', {
            //             description: '请联系管理员解锁账户'
            //         });
            //         break;
            //
            //     case 'CONFLICT':
            //         if (error.conflictField === 'username' && formContext) {
            //             formContext.setFieldError('username', '该用户名已被注册');
            //         }
            //         break;
            //
            //     default:
            //         // 其他错误已在api拦截器统一处理
            //         throw error;
            //}
        }
    }
};