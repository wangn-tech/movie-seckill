import axios from 'axios';
import { useAuthStore } from '@/store/auth';

export const api = axios.create({ baseURL: '/api' });

// 请求拦截：带 token
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 响应拦截：业务 code != 0 抛错；401 清 token 跳登录
api.interceptors.response.use(
  (res) => {
    const body = res.data;
    // 后端统一返回 { code, message, data }，code=0 成功
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
      const err = new Error(body.message || '请求失败') as Error & { response?: any };
      err.response = { data: body };
      throw err;
    }
    return body;
  },
  (error) => {
    const { response } = error;
    if (response?.data?.code === 401 || response?.status === 401) {
      useAuthStore.getState().logout();
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
