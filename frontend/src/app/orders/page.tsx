'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import useSWR from 'swr';
import { errorMessage, getData, postData } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import type { Order } from '@/types/api';

const STATUS = {
  0: { label: '待支付', style: 'bg-amber-50 text-amber-700' },
  1: { label: '已支付', style: 'bg-emerald-50 text-emerald-700' },
  2: { label: '已取消', style: 'bg-zinc-100 text-zinc-500' },
} as const;

export default function OrdersPage() {
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const hydrated = useAuthStore((state) => state.hydrated);
  const [paying, setPaying] = useState('');
  const [notice, setNotice] = useState('');
  const { data: orders, error, isLoading, mutate } = useSWR<Order[]>(accessToken ? '/orders' : null, getData);

  useEffect(() => {
    if (hydrated && !accessToken) router.replace('/login');
  }, [accessToken, hydrated, router]);

  const pay = async (orderNo: string) => {
    setPaying(orderNo);
    setNotice('');
    try {
      await postData<void>(`/payments/${orderNo}`);
      setNotice('支付成功，Redis 座位状态将通过 Outbox 异步确认。');
      await mutate();
    } catch (payError) {
      setNotice(errorMessage(payError, '支付失败'));
    } finally {
      setPaying('');
    }
  };

  if (!hydrated || isLoading) return <div className="h-72 animate-pulse rounded-3xl bg-zinc-200" />;

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs font-semibold text-red-600">MY ORDERS</p>
        <h1 className="mt-1 text-3xl font-black">我的订单</h1>
        <p className="mt-2 text-sm text-zinc-500">订单由 RocketMQ 异步创建，支付与超时释放通过 Outbox 保证最终一致。</p>
      </header>
      {notice ? <div className="rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">{notice}</div> : null}
      {error ? <div className="rounded-xl bg-red-50 p-5 text-red-700">订单加载失败。<button onClick={() => mutate()} className="ml-2 underline">重试</button></div> : null}
      {!error && orders?.length === 0 ? (
        <div className="rounded-3xl border border-dashed border-zinc-300 bg-white p-14 text-center">
          <p className="text-lg font-bold">还没有订单</p>
          <p className="mt-2 text-sm text-zinc-400">选择一场喜欢的电影开始抢座吧。</p>
          <Link href="/" className="mt-5 inline-block rounded-xl bg-red-600 px-5 py-3 text-sm font-semibold text-white">浏览电影</Link>
        </div>
      ) : null}
      <div className="grid gap-4">
        {(orders ?? []).map((order) => {
          const status = STATUS[order.status];
          return (
            <article key={order.id} className="rounded-2xl border border-zinc-200 bg-white p-5 card-shadow sm:flex sm:items-center sm:justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-3">
                  <h2 className="text-lg font-black">{order.movieName || `订单 ${order.orderNo}`}</h2>
                  <span className={`rounded-full px-3 py-1 text-xs font-semibold ${status.style}`}>{status.label}</span>
                </div>
                <p className="mt-2 text-sm text-zinc-500">{order.cinemaName} · {order.showTime}</p>
                <p className="mt-1 text-sm text-zinc-500">{order.seatsInfo} · 共 {order.seatCount} 座</p>
                <p className="mt-3 text-xs text-zinc-400">订单号 {order.orderNo}</p>
              </div>
              <div className="mt-5 flex items-center justify-between gap-5 border-t border-zinc-100 pt-4 sm:mt-0 sm:block sm:border-0 sm:pt-0 sm:text-right">
                <p className="text-2xl font-black text-red-600">¥{order.totalPrice}</p>
                {order.status === 0 ? (
                  <button onClick={() => pay(order.orderNo)} disabled={paying === order.orderNo}
                    className="mt-2 rounded-xl bg-red-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50">
                    {paying === order.orderNo ? '支付中…' : '模拟支付'}
                  </button>
                ) : null}
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
