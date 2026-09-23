'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';

const STATUS: Record<number, string> = { 0: '待支付', 1: '已支付', 2: '已取消' };

export default function OrdersPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const load = () => api.get('/orders').then((r: any) => setOrders(r.data || []));
  useEffect(() => { load(); }, []);

  const pay = async (orderNo: string) => {
    try {
      await api.post(`/payments/${orderNo}`);
      alert('支付成功');
      load();
    } catch (e: any) {
      alert(e.response?.data?.message || '支付失败');
    }
  };

  return (
    <div>
      <h1 className="text-xl font-bold mb-4">我的订单</h1>
      <div className="space-y-2">
        {orders.map((o) => (
          <div key={o.id} className="bg-white p-4 rounded shadow flex justify-between">
            <div>
              <div className="font-medium">{o.movieName || '订单 ' + o.orderNo}</div>
              <div className="text-sm text-gray-500">{o.seatsInfo} · ¥{o.totalPrice}</div>
            </div>
            <div className="text-right">
              <div className={o.status === 0 ? 'text-orange-500' : o.status === 1 ? 'text-green-600' : 'text-gray-400'}>
                {STATUS[o.status]}
              </div>
              {o.status === 0 && (
                <button onClick={() => pay(o.orderNo)} className="mt-1 bg-red-600 text-white px-3 py-1 rounded text-sm">
                  模拟支付
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
