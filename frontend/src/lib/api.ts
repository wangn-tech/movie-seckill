import axios from 'axios';
import { useAuthStore } from '@/store/auth';

export const api = axios.create({ baseURL: '/api' });

// 请求拦截：带 token
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 响应拦截：401 时尝试 refresh
api.interceptors.response.use(
  (res) => res.data,
  async (error) => {
    const { response } = error;
    if (response?.data?.code === 401) {
      // 简单处理：跳登录
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
