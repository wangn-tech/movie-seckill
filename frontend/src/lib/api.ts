import axios, { AxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/auth';

export const api = axios.create({ baseURL: '/api' });

// 是否正在刷新 token
let refreshing = false;
let pendingQueue: Array<() => void> = [];

// 请求拦截：带 token
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 响应拦截：业务 code 解包；401 自动 refresh 后重放
api.interceptors.response.use(
  (res) => {
    const body = res.data;
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
      const err = new Error(body.message || '请求失败') as Error & { response?: any; config?: any };
      err.response = { data: body };
      err.config = res.config;
      throw err;
    }
    return body;
  },
  async (error) => {
    const { response, config } = error;
    const originalRequest = config as AxiosRequestConfig & { _retry?: boolean };

    // 401 且不是 refresh 接口本身，尝试刷新
    if (response?.status === 401 && !originalRequest._retry && !originalRequest.url?.includes('/auth/refresh')) {
      if (refreshing) {
        // 排队等刷新完成
        return new Promise((resolve) => {
          pendingQueue.push(() => resolve(api(originalRequest)));
        });
      }

      originalRequest._retry = true;
      refreshing = true;

      try {
        const refreshToken = useAuthStore.getState().refreshToken;
        if (!refreshToken) throw new Error('no refresh token');

        const refreshRes = await axios.post('/api/auth/refresh', {}, {
          headers: { Authorization: `Bearer ${refreshToken}` },
        });
        const body = refreshRes.data;
        if (body.code === 0 && body.data?.accessToken) {
          useAuthStore.getState().setTokens(body.data);
          // 重放队列里的请求
          pendingQueue.forEach((cb) => cb());
          pendingQueue = [];
          return api(originalRequest);
        }
        throw new Error('refresh failed');
      } catch (e) {
        pendingQueue = [];
        useAuthStore.getState().logout();
        if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
          window.location.href = '/login';
        }
        return Promise.reject(error);
      } finally {
        refreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
