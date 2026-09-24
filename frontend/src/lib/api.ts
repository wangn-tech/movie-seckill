import axios, { AxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/auth';
import type { ApiEnvelope, AuthPayload } from '@/types/api';

export const api = axios.create({ baseURL: '/api', timeout: 10_000 });

interface RetryConfig extends AxiosRequestConfig {
  _retry?: boolean;
}

interface QueueItem {
  resolve: () => void;
  reject: (reason: unknown) => void;
}

let refreshing = false;
let pendingQueue: QueueItem[] = [];

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => {
    const body = response.data as ApiEnvelope<unknown>;
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
      return Promise.reject(Object.assign(new Error(body.message || '请求失败'), {
        response: { status: response.status, data: body },
        config: response.config,
      }));
    }
    return response;
  },
  async (error) => {
    const response = error.response;
    const originalRequest = error.config as RetryConfig | undefined;
    if (!originalRequest || response?.status !== 401 || originalRequest._retry
      || originalRequest.url?.includes('/auth/refresh')) {
      return Promise.reject(error);
    }

    if (refreshing) {
      await new Promise<void>((resolve, reject) => pendingQueue.push({ resolve, reject }));
      return api(originalRequest);
    }

    originalRequest._retry = true;
    refreshing = true;
    try {
      const refreshToken = useAuthStore.getState().refreshToken;
      if (!refreshToken) throw new Error('缺少刷新凭证');
      const response = await axios.post<ApiEnvelope<AuthPayload>>('/api/auth/refresh', {}, {
        headers: { Authorization: `Bearer ${refreshToken}` },
      });
      if (response.data.code !== 0) throw new Error(response.data.message);
      useAuthStore.getState().setTokens(response.data.data);
      pendingQueue.forEach((item) => item.resolve());
      pendingQueue = [];
      return api(originalRequest);
    } catch (refreshError) {
      pendingQueue.forEach((item) => item.reject(refreshError));
      pendingQueue = [];
      useAuthStore.getState().logout();
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
      return Promise.reject(refreshError);
    } finally {
      refreshing = false;
    }
  },
);

export async function getData<T>(url: string): Promise<T> {
  const response = await api.get<ApiEnvelope<T>>(url);
  return response.data.data;
}

export async function postData<T>(url: string, body?: unknown): Promise<T> {
  const response = await api.post<ApiEnvelope<T>>(url, body);
  return response.data.data;
}

export function errorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axios.isAxiosError<ApiEnvelope<unknown>>(error)) {
    return error.response?.data?.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
