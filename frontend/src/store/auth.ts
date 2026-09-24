import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthPayload } from '@/types/api';

interface AuthState {
  userId: number | null;
  nickname: string;
  accessToken: string;
  refreshToken: string;
  hydrated: boolean;
  setTokens: (tokens: AuthPayload) => void;
  setHydrated: () => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      userId: null,
      nickname: '',
      accessToken: '',
      refreshToken: '',
      hydrated: false,
      setTokens: (tokens) => set(tokens),
      setHydrated: () => set({ hydrated: true }),
      logout: () => set({ userId: null, nickname: '', accessToken: '', refreshToken: '' }),
    }),
    {
      name: 'seckill-auth',
      version: 1,
      partialize: ({ hydrated: _hydrated, ...state }) => state,
      onRehydrateStorage: () => (state) => state?.setHydrated(),
    },
  ),
);
