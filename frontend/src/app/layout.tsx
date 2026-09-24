import type { Metadata } from 'next';
import './globals.css';
import NavBar from './nav-bar';

export const metadata: Metadata = {
  title: { default: '光影秒杀', template: '%s · 光影秒杀' },
  description: 'Redis Lua + RocketMQ 高并发电影抢座演示',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen bg-zinc-50 text-zinc-900 antialiased">
        <NavBar />
        <main className="mx-auto min-h-[calc(100vh-144px)] max-w-6xl px-4 py-8 sm:px-6">{children}</main>
        <footer className="border-t border-zinc-200 bg-white py-6 text-center text-xs text-zinc-400">
          Java 高并发面试演示 · Redis Lua · Caffeine · Redisson · RocketMQ · Outbox
        </footer>
      </body>
    </html>
  );
}
