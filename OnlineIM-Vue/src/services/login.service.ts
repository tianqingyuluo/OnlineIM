// src/services/login.service.ts
import api from './api.service';
import { useUserStore } from '@/stores/user';
import { toast } from 'vue-sonner';
import router from "@/router";
import { websocketService } from '@/services/websocket.service';
import { UAParser } from 'ua-parser-js';
import { buildDeviceLoginFields, rememberDeviceId, type DeviceType } from '@/utils/device-session';

export const LoginService = {
    validationRules: {
        username: {
            min: 4,
            max: 16,
            pattern: /^[a-zA-Z0-9_]+$/,
            messages: {
                min: '用户名长度必须在4-16之间',
                max: '用户名长度必须在4-16之间',
                pattern: '用户名只能包含字母、数字和下划线',
                required: '用户名不能为空'
            }
        },
        password: {
            min: 6,
            max: 20,
            messages: {
                min: '密码长度必须在6-20之间',
                max: '密码长度必须在6-20之间',
                required: '密码不能为空'
            }
        }
    },

    async login(
        credentials: { username: string; password: string }
    ) {
        const userStore = useUserStore();

        try {
            const parser = new UAParser;
            const uaResult = parser.getResult();
            const device = buildDeviceLoginFields({
                uaDeviceType: uaResult.device.type,
                override: localStorage.getItem('onlineim.device_type'),
            });
            const requestBody = {
                ...credentials,
                device_id: device.device_id,
                device_type: device.device_type,
            };
            const response = await api.post('/auth/login', requestBody);

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
                const deviceType = (response.data.device_type || device.device_type) as DeviceType
                const deviceId = response.data.device_id || device.device_id
                userStore.deviceType = deviceType
                userStore.deviceId = deviceId
                rememberDeviceId(deviceType, deviceId)

                console.log("token:\n"+response.data.access_token);

                // 连接 WebSocket
                websocketService.connect();

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
            if (error?.status === 400 || error?.status === 403) {
                return;
            }
            toast.error(error?.message || '登录失败');
        }
    }
};