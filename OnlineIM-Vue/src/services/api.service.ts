// src/services/api.service.ts
import axios from 'axios';
import { API_BASE_URL } from '../../shared/config.ts';
import { useUserStore } from '@/stores/user';
import { toast } from 'vue-sonner';
// 定义标准API响应格式
export type ApiResponse<T = any> = {
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: Record<string, string[]>; // 用于字段级错误
    [key: string]: any; // 其他可能的错误信息
  };
};

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 为 true 时不弹出全局错误提示，由调用方自己展示 */
    skipErrorToast?: boolean
  }
}

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  },
});
// 请求拦截器
// 请求拦截器添加调试日志
api.interceptors.request.use(
  (config) => {
    const userStore=useUserStore();
    // 从 localStorage 获取 token
    const token = userStore.token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    // 请求配置出错时直接reject
    return Promise.reject({
      code: 'REQUEST_CONFIG_ERROR',
      message: '请求配置错误',
      originalError: error
    });
  }
);

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    // 直接返回数据部分，同时保留完整的响应结构
    return response;
  },
  (error) => {
    useUserStore();
// 如果请求被取消
    if (axios.isCancel(error)) {
      return Promise.reject({
        code: 'REQUEST_CANCELLED',
        message: '请求已被取消'
      });
    }

    const skipErrorToast = Boolean(error.config?.skipErrorToast)

    // 网络错误（无响应）
    if (!error.response) {
      if (!skipErrorToast) {
        toast.error('网络错误', {
          description: '请检查网络连接后重试'
        });
      }
      return Promise.reject({
        code: 'NETWORK_ERROR',
        message: error.message || '网络错误',
        details: {},
        status: 0,
        originalError: error
      });
    }

    const { status, data } = error.response;
    const errorCode = data?.code || `HTTP_${status}`;
    const errorMessage = readErrorMessage(data, error.message);

    // 统一错误格式
    const normalizedError = {
      code: errorCode,
      message: errorMessage,
      details: data?.errors || {},
      status,
      ...data // 保留原始错误数据
    };


    if (!skipErrorToast && (status === 400 || status === 403)) {
      toast.error(errorMessage);
    }

    switch (status) {
      // case 400:
      //   // 已在上方用后端 message 提示
      //   break;
      //
      // case 401:
      //   if (userStore.isAuthenticated()) {
      //     userStore.clearUser(); // 使用store的统一logout方法
      //     toast.error('会话已过期', {
      //       description: '请重新登录',
      //       duration: 5000
      //     });
      //     router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
      //   }
      //   break;
      //
      // case 403:
      //   toast.error('权限不足', {
      //     description: '您没有执行此操作的权限'
      //   });
      //   break;
      //
      // case 404:
      //   toast.error( data.message ||"我们的服务出了点问题，网页错误代码：404");
      //   break;

      // case 429:
      //   toast.error('请求过于频繁', {
      //     description: '请稍后再试'
      //   });
      //   break;
      //
      // case 500:
      //   toast.error('服务器错误', {
      //     description: '我们的服务暂时遇到问题，请稍后再试'
      //   });
      //   break;
      //
      // default:
      //   if (status >= 500) {
      //     toast.error(`服务器错误 (${status})`);
      //   }
      // default:
      //   toast.error(  data.message ||"我们的服务出了点问题");
    }
    return Promise.reject(normalizedError);
  }
);

// 封装常用HTTP方法
export const http = {
  get: <T>(url: string, config?: any) => api.get<T>(url, config),
  post: <T>(url: string, data?: any, config?: any) => api.post<T>(url, data, config),
  put: <T>(url: string, data?: any, config?: any) => api.put<T>(url, data, config),
  patch: <T>(url: string, data?: any, config?: any) => api.patch<T>(url, data, config),
  delete: <T>(url: string, config?: any) => api.delete<T>(url, config),
};

function readErrorMessage(data: { message?: unknown; error?: unknown } | undefined, fallback?: string): string {
  if (typeof data?.message === 'string' && data.message.trim()) {
    return data.message;
  }
  if (typeof data?.error === 'string' && data.error.trim()) {
    return data.error;
  }
  return fallback || '请求处理失败';
}

export default api;
