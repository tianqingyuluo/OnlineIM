// src/services/register.service.ts
import api from './api.service';
import { toast } from 'vue-sonner';
import type { FormContext } from 'vee-validate';

export const RegisterService = {
    validationRules: {
        username: {
            min: 2,
            max: 20,
            messages: {
                min: '用户名至少2个字符',
                max: '用户名不能超过20个字符',
                required: '请输入用户名'
            }
        },
        password: {
            min: 6,
            max: 32,
            messages: {
                min: '密码至少需要6位',
                max: '密码不能超过32位',
                required: '请输入密码'
            }
        },
        confirmPassword: {
            messages: {
                mismatch: '两次输入密码不一致',
                required: '请确认密码'
            }
        }
    },

    /**
     * 用户注册
     * @param formData 注册表单数据
     * @param formContext 表单上下文（用于设置字段错误）
     */
    async register(
        formData: {
            username: string;
            password: string;
            confirmPassword: string;
        },
        formContext?: FormContext
    ) {
        try {
            // 客户端确认密码校验
            if (formData.password !== formData.confirmPassword) {
                if (formContext) {
                    formContext.setFieldError('confirmPassword', this.validationRules.confirmPassword.messages.required);
                }
                return;
            }

            const response = await api.post('/auth/register', {
                username: formData.username,
                password: formData.password
            });
            if (response.data.access_token){
                toast.success('注册成功', {
                    description: '账号已创建，请登录',
                });
            }
            return ;
        } catch (error: any) {
            // 处理字段级错误
            if (error.code === 'VALIDATION_ERROR' && formContext) {
                Object.entries(error.details || {}).forEach(([field, message]) => {
                    formContext.setFieldError(field, message as string);
                });
            }
            // 处理用户名冲突
            else if (error.code === 'CONFLICT' && error.conflictField === 'username') {
                formContext?.setFieldError('username', '该用户名已被注册');
            }
            // 其他错误已在api拦截器处理
            throw error;
        }
    }
};