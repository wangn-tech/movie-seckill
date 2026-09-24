'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/auth';

export default function NavBar() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const nickname = useAuthStore((state) => state.nickname);
  const clearAuth = useAuthStore((state) => state.logout);
  const pathname = usePathname();
  const router = useRouter();

  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      clearAuth();
      router.push('/');
    }
  };

  const linkClass = (active: boolean) =>
    `rounded-full px-3 py-2 transition ${active ? 'bg-red-50 font-semibold text-red-600' : 'text-zinc-600 hover:bg-zinc-100'}`;

  return (
    <header className="sticky top-0 z-40 border-b border-zinc-200/80 bg-white/90 backdrop-blur">
      <nav className="mx-auto flex h-16 max-w-6xl items-center gap-3 px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-2 font-black tracking-tight text-zinc-950">
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-red-600 text-white shadow-sm">影</span>
          <span>光影秒杀</span>
        </Link>
        <div className="ml-auto flex items-center gap-1 text-sm">
          <Link href="/" className={linkClass(pathname === '/')}>热映</Link>
          <Link href="/orders" className={linkClass(pathname === '/orders')}>订单</Link>
          {accessToken ? (
            <>
              <span className="hidden px-2 text-zinc-400 sm:inline">{nickname || '用户'}</span>
              <button onClick={logout} className="rounded-full px-3 py-2 text-zinc-600 hover:bg-zinc-100">退出</button>
            </>
          ) : (
            <Link href="/login" className="ml-1 rounded-full bg-red-600 px-4 py-2 font-semibold text-white hover:bg-red-700">
              登录
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
}
