'use client';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/auth';

export default function NavBar() {
  const { accessToken, nickname, logout } = useAuthStore();
  const router = useRouter();
  const pathname = usePathname();

  const handleLogout = () => {
    logout();
    router.push('/');
  };

  return (
    <nav className="bg-red-600 text-white px-6 py-3 flex items-center gap-4">
      <Link href="/" className="font-bold">电影票秒杀</Link>
      <div className="ml-auto flex items-center gap-4 text-sm">
        <Link href="/" className={pathname === '/' ? 'font-bold' : ''}>首页</Link>
        <Link href="/orders" className={pathname === '/orders' ? 'font-bold' : ''}>我的订单</Link>
        {accessToken ? (
          <>
            <span className="text-red-100">{nickname || '用户'}</span>
            <button onClick={handleLogout} className="underline">退出</button>
          </>
        ) : (
          <Link href="/login" className="underline">登录</Link>
        )}
      </div>
    </nav>
  );
}
