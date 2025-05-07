// src/services/api.service.ts
import axios from 'axios';
import { API_BASE_URL } from '@/config';
import { useUserStore } from '@/stores/user';
import { toast } from 'vue-sonner';
import router from '@/router';

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

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  },
});

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
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
    return {
      data: response.data,
      status: response.status,
      headers: response.headers
    };
  },
  (error) => {
    const userStore = useUserStore();
    
    // 如果请求被取消
    if (axios.isCancel(error)) {
      return Promise.reject({
        code: 'REQUEST_CANCELLED',
        message: '请求已被取消'
      });
    }

    // 网络错误（无响应）
    if (!error.response) {
      toast.error('网络错误', {
        description: '请检查网络连接后重试'
      });
      return Promise.reject({
        code: 'NETWORK_ERROR',
        message: '网络连接异常',
        isNetworkError: true
      });
    }

    const { status, data } = error.response;
    const errorCode = data?.code || `HTTP_${status}`;
    const errorMessage = data?.message || error.message || '请求处理失败';

    // 统一错误格式
    const normalizedError = {
      code: errorCode,
      message: errorMessage,
      details: data?.errors || {},
      status,
      ...data // 保留原始错误数据
    };

    switch (status) {
      case 400:
        // 不需要toast，由调用方处理字段级错误
        break;
        
      case 401:
        if (userStore.isLoggedIn) {
          userStore.logout(); // 使用store的统一logout方法
          toast.error('会话已过期', {
            description: '请重新登录',
            duration: 5000
          });
          router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
        }
        break;

      case 403:
        toast.error('权限不足', {
          description: '您没有执行此操作的权限'
        });
        break;

      case 404:
        // 由调用方决定如何处理404
        break;

      case 429:
        toast.error('请求过于频繁', {
          description: '请稍后再试'
        });
        break;

      case 500:
        toast.error('服务器错误', {
          description: '我们的服务暂时遇到问题，请稍后再试'
        });
        break;

      default:
        if (status >= 500) {
          toast.error(`服务器错误 (${status})`);
        }
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

export default api;