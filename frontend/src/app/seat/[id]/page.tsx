'use client';
import { useState } from 'react';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';

const ROWS = 8, COLS = 10;

export default function SeatPage({ params }: { params: { id: string } }) {
  const scheduleId = Number(params.id);
  const [selected, setSelected] = useState<{ row: number; col: number }[]>([]);
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const toggle = (row: number, col: number) => {
    const idx = selected.findIndex((s) => s.row === row && s.col === col);
    if (idx >= 0) setSelected(selected.filter((_, i) => i !== idx));
    else if (selected.length < 4) setSelected([...selected, { row, col }]);
  };

  const seize = async () => {
    if (!selected.length) return alert('请选座');
    setLoading(true);
    try {
      const requestId = `req_${Date.now()}`;
      const res: any = await api.post('/seckill/seize', {
        scheduleId,
        seats: selected,
        requestId,
      });
      alert('抢座成功，订单排队中');
      router.push('/orders');
    } catch (e: any) {
      alert(e.response?.data?.message || '抢座失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h1 className="text-xl font-bold mb-4">选座（场次 {scheduleId}）</h1>
      <div className="inline-block bg-white p-4 rounded shadow">
        <div className="text-center text-gray-400 mb-3">—— 银幕 ——</div>
        {Array.from({ length: ROWS }).map((_, r) => (
          <div key={r} className="flex gap-1 mb-1">
            {Array.from({ length: COLS }).map((_, c) => {
              const isSel = selected.some((s) => s.row === r && s.col === c);
              return (
                <button
                  key={c}
                  onClick={() => toggle(r, c)}
                  className={`w-7 h-7 rounded text-xs ${isSel ? 'bg-red-500 text-white' : 'bg-gray-200 hover:bg-gray-300'}`}
                >
                  {c + 1}
                </button>
              );
            })}
          </div>
        ))}
      </div>
      <div className="mt-4">
        <div>已选 {selected.length} 座</div>
        <button disabled={loading} onClick={seize} className="mt-2 bg-red-600 text-white px-6 py-2 rounded disabled:opacity-50">
          {loading ? '提交中...' : '提交订单'}
        </button>
      </div>
    </div>
  );
}
