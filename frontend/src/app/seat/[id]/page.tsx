'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';

interface Seat { row: number; col: number; status: string; }

export default function SeatPage({ params }: { params: { id: string } }) {
  const scheduleId = Number(params.id);
  const [selected, setSelected] = useState<Seat[]>([]);
  const [locked, setLocked] = useState<Seat[]>([]);
  const [meta, setMeta] = useState<any>({});
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  useEffect(() => {
    api.get(`/schedules/${scheduleId}/seats`).then((r: any) => {
      setMeta({ rows: r.data.rows, cols: r.data.cols, availableSeats: r.data.availableSeats });
      setLocked(r.data.lockedSeats || []);
    });
  }, [scheduleId]);

  const isLocked = (row: number, col: number) =>
    locked.some((s) => s.row === row && s.col === col);
  const isSelected = (row: number, col: number) =>
    selected.some((s) => s.row === row && s.col === col);

  const toggle = (row: number, col: number) => {
    if (isLocked(row, col)) return;
    if (isSelected(row, col)) setSelected(selected.filter((s) => !(s.row === row && s.col === col)));
    else if (selected.length < 4) setSelected([...selected, { row, col, status: 'selected' }]);
  };

  const seize = async () => {
    if (!selected.length) return alert('请选座');
    setLoading(true);
    try {
      const requestId = `req_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
      const res: any = await api.post('/seckill/seize', {
        scheduleId,
        seats: selected.map((s) => ({ row: s.row, col: s.col })),
        requestId,
      });
      alert('抢座成功，订单排队中，正在查询...');
      // 轮询订单状态
      pollOrder(requestId);
    } catch (e: any) {
      alert(e.response?.data?.message || '抢座失败');
      setLoading(false);
    }
  };

  const pollOrder = async (requestId: string) => {
    for (let i = 0; i < 10; i++) {
      await new Promise((r) => setTimeout(r, 1000));
      try {
        const res: any = await api.get(`/orders/by-request/${requestId}`);
        if (res.data) {
          router.push('/orders');
          return;
        }
      } catch {}
    }
    router.push('/orders');
  };

  return (
    <div>
      <h1 className="text-xl font-bold mb-2">选座</h1>
      <div className="text-sm text-gray-500 mb-4">余票 {meta.availableSeats} · 已选 {selected.length} 座</div>
      <div className="inline-block bg-white p-4 rounded shadow">
        <div className="text-center text-gray-400 mb-3 text-sm">—— 银幕 ——</div>
        {Array.from({ length: meta.rows || 8 }).map((_, r) => (
          <div key={r} className="flex gap-1 mb-1">
            {Array.from({ length: meta.cols || 10 }).map((_, c) => {
              const lock = locked.find((s) => s.row === r && s.col === c);
              let cls = 'bg-gray-200 hover:bg-gray-300';
              if (lock?.status === 'sold') cls = 'bg-gray-500 text-white cursor-not-allowed';
              else if (lock) cls = 'bg-red-300 text-white cursor-not-allowed';
              else if (isSelected(r, c)) cls = 'bg-red-600 text-white';
              return (
                <button key={c} onClick={() => toggle(r, c)} disabled={!!lock}
                  className={`w-8 h-8 rounded text-xs ${cls}`}>
                  {c + 1}
                </button>
              );
            })}
          </div>
        ))}
      </div>
      <div className="mt-4 flex items-center gap-4 text-sm">
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-gray-200 inline-block" />可选</span>
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-red-600 inline-block" />已选</span>
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-red-300 inline-block" />锁定中</span>
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-gray-500 inline-block" />已售</span>
      </div>
      <button disabled={loading} onClick={seize}
        className="mt-4 bg-red-600 text-white px-8 py-2 rounded disabled:opacity-50">
        {loading ? '提交中...' : `提交订单（${selected.length}座）`}
      </button>
    </div>
  );
}
