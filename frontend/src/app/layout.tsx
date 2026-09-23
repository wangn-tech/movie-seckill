import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = { title: '电影票秒杀', description: 'Java高并发秒杀演示' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen">
        <nav className="bg-red-600 text-white px-6 py-3 flex gap-4">
          <a href="/" className="font-bold">电影票秒杀</a>
          <a href="/orders" className="ml-auto">我的订单</a>
        </nav>
        <main className="max-w-4xl mx-auto p-4">{children}</main>
      </body>
    </html>
  );
}
