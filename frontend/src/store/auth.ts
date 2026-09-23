import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  userId: number | null;
  nickname: string;
  accessToken: string;
  refreshToken: string;
  setTokens: (t: { userId: number; nickname: string; accessToken: string; refreshToken: string }) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      userId: null,
      nickname: '',
      accessToken: '',
      refreshToken: '',
      setTokens: (t) => set(t),
      logout: () => set({ userId: null, nickname: '', accessToken: '', refreshToken: '' }),
    }),
    { name: 'seckill-auth' }
  )
);
