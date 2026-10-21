// src/services/register.service.ts
import api from './api.service';
import { toast } from 'vue-sonner';

type FieldErrors = {
    setFieldError: (field: string, message: string) => void
}

export const RegisterService = {
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
        formContext?: FieldErrors
    ): Promise<boolean> {
        try {
            // 客户端确认密码校验
            if (formData.password !== formData.confirmPassword) {
                formContext?.setFieldError(
                    'confirmPassword',
                    this.validationRules.confirmPassword.messages.mismatch
                );
                return false;
            }

            await api.post('/auth/register', {
                username: formData.username,
                password: formData.password
            });
            toast.success('注册成功', {
                description: '账号已创建，请登录',
            });
            return true;
        } catch (error: any) {
            const message = typeof error?.message === 'string' && error.message
                ? error.message
                : '注册失败';
            if (error?.status === 409) {
                formContext?.setFieldError('username', message);
                return false;
            }
            if (error?.status === 400 && applyFieldErrors(message, formContext)) {
                return false;
            }
            if (error?.status !== 400 && error?.status !== 403) {
                toast.error(message);
            }
            return false;
        }
    }
};

function applyFieldErrors(message: string, formContext?: FieldErrors): boolean {
    if (!formContext || !message) return false;
    let applied = false;
    for (const part of message.split(';')) {
        const separator = part.indexOf(':');
        if (separator <= 0) continue;
        const field = part.slice(0, separator).trim();
        const text = part.slice(separator + 1).trim();
        if (!text) continue;
        if (field === 'username' || field === 'password' || field === 'nickname' || field === 'email' || field === 'phone') {
            formContext.setFieldError(field, text);
            applied = true;
        }
    }
    return applied;
}