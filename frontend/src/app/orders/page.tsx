'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/auth';

const STATUS: Record<number, string> = { 0: '待支付', 1: '已支付', 2: '已取消' };

export default function OrdersPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const [paying, setPaying] = useState<string>('');
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    if (!accessToken) {
      alert('请先登录');
      router.push('/login');
      return;
    }
    load();
  }, [accessToken]);

  const load = () => api.get('/orders').then((r: any) => setOrders(r.data || []));

  const pay = async (orderNo: string) => {
    setPaying(orderNo);
    try {
      await api.post(`/payments/${orderNo}`);
      alert('支付成功');
      load();
    } catch (e: any) {
      alert(e.response?.data?.message || '支付失败');
    } finally {
      setPaying('');
    }
  };

  return (
    <div>
      <h1 className="text-xl font-bold mb-4">我的订单</h1>
      {orders.length === 0 && <div className="text-gray-400">暂无订单</div>}
      <div className="space-y-2">
        {orders.map((o) => (
          <div key={o.id} className="bg-white p-4 rounded shadow flex justify-between items-center">
            <div>
              <div className="font-medium">{o.movieName || '订单 ' + o.orderNo}</div>
              <div className="text-sm text-gray-500">{o.showTime} · {o.seatsInfo} · ¥{o.totalPrice}</div>
            </div>
            <div className="text-right">
              <span className={`text-sm ${o.status === 0 ? 'text-orange-500' : o.status === 1 ? 'text-green-600' : 'text-gray-400'}`}>
                {STATUS[o.status]}
              </span>
              {o.status === 0 && (
                <button onClick={() => pay(o.orderNo)} disabled={paying === o.orderNo}
                  className="block mt-1 bg-red-600 text-white px-3 py-1 rounded text-sm disabled:opacity-50">
                  {paying === o.orderNo ? '支付中...' : '模拟支付'}
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
