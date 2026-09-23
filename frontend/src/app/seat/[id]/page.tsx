'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/auth';

interface Seat { row: number; col: number; status: string; }

export default function SeatPage({ params }: { params: { id: string } }) {
  const scheduleId = Number(params.id);
  const [selected, setSelected] = useState<Seat[]>([]);
  const [locked, setLocked] = useState<Seat[]>([]);
  const [meta, setMeta] = useState<any>({ rows: 8, cols: 10, availableSeats: 0 });
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState('');
  const router = useRouter();
  const accessToken = useAuthStore((s) => s.accessToken);

  // 登录守卫
  useEffect(() => {
    if (!accessToken) {
      alert('请先登录');
      router.push('/login');
    }
  }, [accessToken, router]);

  // 加载座位图
  useEffect(() => {
    if (!accessToken) return;
    api.get(`/schedules/${scheduleId}/seats`).then((r: any) => {
      setMeta({ rows: r.data.rows, cols: r.data.cols, availableSeats: r.data.availableSeats });
      setLocked(r.data.lockedSeats || []);
    }).catch(() => {});
  }, [scheduleId, accessToken]);

  const isLocked = (row: number, col: number) =>
    locked.some((s) => s.row === row && s.col === col);
  const isSelected = (row: number, col: number) =>
    selected.some((s) => s.row === row && s.col === col);

  const toggle = (row: number, col: number) => {
    if (isLocked(row, col)) return;
    if (isSelected(row, col)) {
      setSelected(selected.filter((s) => !(s.row === row && s.col === col)));
    } else if (selected.length >= 4) {
      alert('最多选 4 个座位');
    } else {
      setSelected([...selected, { row, col, status: 'selected' }]);
    }
  };

  const seize = async () => {
    if (!selected.length) { alert('请先选择座位'); return; }
    setLoading(true);
    setStatus('提交订单中...');
    try {
      const requestId = `req_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
      setStatus('扣减库存，创建订单...');
      const res: any = await api.post('/seckill/seize', {
        scheduleId,
        seats: selected.map((s) => ({ row: s.row, col: s.col })),
        requestId,
      });
      setStatus('订单创建成功，跳转订单页...');
      await pollOrder(requestId);
    } catch (e: any) {
      setLoading(false);
      setStatus('');
      alert(e.response?.data?.message || '抢座失败，请重试');
    }
  };

  const pollOrder = async (requestId: string) => {
    for (let i = 0; i < 8; i++) {
      await new Promise((r) => setTimeout(r, 800));
      try {
        const res: any = await api.get(`/orders/by-request/${requestId}`);
        if (res.data) {
          router.push('/orders');
          return;
        }
      } catch {}
      setStatus(`等待订单落库... ${i + 1}/8`);
    }
    // 超时也跳订单页
    router.push('/orders');
  };

  return (
    <div className="max-w-2xl">
      <h1 className="text-xl font-bold mb-2">选座</h1>
      <div className="text-sm text-gray-500 mb-4">
        余票 {meta.availableSeats} · 已选 {selected.length} 座
        {status && <span className="ml-3 text-red-600">{status}</span>}
      </div>
      <div className="inline-block bg-white p-6 rounded shadow">
        <div className="text-center text-gray-400 mb-4 text-sm tracking-widest">—— 银 幕 ——</div>
        <div className="space-y-1">
          {Array.from({ length: meta.rows || 8 }).map((_, r) => (
            <div key={r} className="flex gap-1 justify-center">
              <span className="w-5 text-xs text-gray-400 leading-8">{r + 1}</span>
              {Array.from({ length: meta.cols || 10 }).map((_, c) => {
                const lock = locked.find((s) => s.row === r && s.col === c);
                let cls = 'bg-gray-200 hover:bg-gray-400 text-gray-600';
                if (lock?.status === 'sold') cls = 'bg-gray-600 text-white cursor-not-allowed';
                else if (lock) cls = 'bg-orange-300 text-white cursor-not-allowed';
                else if (isSelected(r, c)) cls = 'bg-red-600 text-white ring-2 ring-red-300';
                return (
                  <button key={c} onClick={() => toggle(r, c)} disabled={!!lock}
                    className={`w-8 h-8 rounded text-xs transition-all ${cls}`}>
                    {c + 1}
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </div>
      <div className="mt-4 flex items-center gap-4 text-sm">
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-gray-200 inline-block rounded" />可选</span>
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-red-600 inline-block rounded" />已选</span>
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-orange-300 inline-block rounded" />锁定中</span>
        <span className="flex items-center gap-1"><i className="w-3 h-3 bg-gray-600 inline-block rounded" />已售</span>
      </div>
      <button disabled={loading} onClick={seize}
        className="mt-4 bg-red-600 text-white px-8 py-3 rounded font-medium disabled:opacity-50">
        {loading ? status || '提交中...' : `提交订单（${selected.length}座）`}
      </button>
    </div>
  );
}
