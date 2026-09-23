import type { Metadata } from 'next';
import './globals.css';
import NavBar from './nav-bar';

export const metadata: Metadata = { title: '电影票秒杀', description: 'Java高并发秒杀演示' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen bg-gray-50">
        <NavBar />
        <main className="max-w-4xl mx-auto p-4">{children}</main>
      </body>
    </html>
  );
}
