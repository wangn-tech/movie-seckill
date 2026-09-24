'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import useSWR from 'swr';
import { errorMessage, getData, postData } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import type { Schedule, SeatCoordinate, SeatLayout, SeckillStatus } from '@/types/api';

const keyOf = (row: number, col: number) => `${row}-${col}`;
const wait = (milliseconds: number) => new Promise((resolve) => setTimeout(resolve, milliseconds));

export default function SeatPage({ params }: { params: { id: string } }) {
  const scheduleId = Number(params.id);
  const router = useRouter();
  const accessToken = useAuthStore((state) => state.accessToken);
  const hydrated = useAuthStore((state) => state.hydrated);
  const [selected, setSelected] = useState<SeatCoordinate[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [progress, setProgress] = useState('');
  const [notice, setNotice] = useState('');
  const { data: layout, error, isLoading, mutate } = useSWR<SeatLayout>(
    accessToken ? `/schedules/${scheduleId}/seats` : null,
    getData,
    { refreshInterval: submitting ? 0 : 5_000 },
  );
  const { data: schedule } = useSWR<Schedule>(`/schedules/${scheduleId}`, getData);

  useEffect(() => {
    if (hydrated && !accessToken) router.replace(`/login?next=/seat/${scheduleId}`);
  }, [accessToken, hydrated, router, scheduleId]);

  const unavailable = useMemo(() => new Set((layout?.unavailableSeats ?? []).map((seat) => keyOf(seat.row, seat.col))), [layout?.unavailableSeats]);
  const locked = useMemo(() => new Map((layout?.lockedSeats ?? []).map((seat) => [keyOf(seat.row, seat.col), seat.status])), [layout?.lockedSeats]);
  const selectedSet = useMemo(() => new Set(selected.map((seat) => keyOf(seat.row, seat.col))), [selected]);
  const totalPrice = ((layout?.price ?? schedule?.price ?? 0) * selected.length).toFixed(2);

  useEffect(() => {
    if (!layout) return;
    setSelected((current) => current.filter((seat) => {
      const key = keyOf(seat.row, seat.col);
      return !unavailable.has(key) && !locked.has(key);
    }));
  }, [layout, unavailable, locked]);

  const toggle = (row: number, col: number) => {
    const key = keyOf(row, col);
    if (unavailable.has(key) || locked.has(key) || submitting) return;
    setNotice('');
    setSelected((current) => current.some((seat) => seat.row === row && seat.col === col)
      ? current.filter((seat) => seat.row !== row || seat.col !== col)
      : [...current, { row, col }]);
  };

  const pollOrder = async (requestId: string) => {
    for (let attempt = 1; attempt <= 20; attempt += 1) {
      await wait(500);
      let status: SeckillStatus;
      try {
        status = await getData<SeckillStatus>(`/seckill/requests/${requestId}`);
      } catch (pollError) {
        const statusCode = (pollError as { response?: { status?: number } }).response?.status;
        if (statusCode === 404 && attempt < 20) {
          setProgress(`订单入口处理中 ${attempt}/20`);
          continue;
        }
        throw pollError;
      }
      if (status.status === 'CREATED') {
        router.push('/orders');
        return;
      }
      if (status.status === 'FAILED') throw new Error(status.message);
      setProgress(`订单异步落库中 ${attempt}/20`);
    }
    setSubmitting(false);
    setProgress('订单仍在处理中，可前往订单页稍后查看');
  };

  const seize = async () => {
    if (!selected.length) {
      setNotice('请至少选择一个有效座位');
      return;
    }
    setSubmitting(true);
    setNotice('');
    setProgress('正在原子预扣库存…');
    const requestId = `req_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
    try {
      const status = await postData<SeckillStatus>('/seckill/seize', { scheduleId, seats: selected, requestId });
      setProgress(status.message);
      await pollOrder(requestId);
    } catch (requestError) {
      setSubmitting(false);
      setProgress('');
      setNotice(errorMessage(requestError, '抢座失败，请重新选择'));
      setSelected([]);
      await mutate();
    }
  };

  if (!hydrated || isLoading) return <div className="h-[520px] animate-pulse rounded-3xl bg-zinc-200" />;
  if (error || !layout) return <div className="rounded-2xl bg-red-50 p-8 text-red-700">座位图加载失败，请返回场次页重试。</div>;

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
      <section className="min-w-0 rounded-3xl border border-zinc-200 bg-white p-4 card-shadow sm:p-7">
        <div className="mb-7 flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold text-red-600">SELECT SEATS</p>
            <h1 className="mt-1 text-2xl font-black">{schedule?.hallName || '影厅选座'}</h1>
            <p className="mt-2 text-sm text-zinc-500">{schedule?.showDate} {schedule?.showTime} · 剩余 {layout.availableSeats} 个座位</p>
          </div>
          <button onClick={() => mutate()} className="rounded-full border border-zinc-200 px-3 py-2 text-xs text-zinc-500 hover:bg-zinc-50">刷新座位</button>
        </div>

        <div className="overflow-x-auto pb-4">
          <div className="mx-auto w-max min-w-[520px]">
            <div className="mx-auto mb-8 h-2 w-3/4 rounded-b-full bg-gradient-to-r from-zinc-200 via-red-200 to-zinc-200 shadow-[0_12px_30px_rgba(239,68,68,0.2)]" />
            <p className="mb-6 text-center text-[10px] font-semibold tracking-[0.5em] text-zinc-400">银幕方向</p>
            <div className="space-y-2">
              {Array.from({ length: layout.rows }, (_, rowIndex) => rowIndex + 1).map((row) => (
                <div key={row} className="flex items-center justify-center gap-2">
                  <span className="w-5 text-right text-[10px] text-zinc-400">{row}</span>
                  {Array.from({ length: layout.cols }, (_, colIndex) => colIndex + 1).map((col) => {
                    const key = keyOf(row, col);
                    const unavailableSeat = unavailable.has(key);
                    const lockedState = locked.get(key);
                    const chosen = selectedSet.has(key);
                    if (unavailableSeat) return <span key={key} className="h-8 w-8" aria-label={`${row}排${col}列过道`} />;
                    const style = lockedState === 'SOLD'
                      ? 'bg-zinc-700 text-white cursor-not-allowed'
                      : lockedState === 'LOCKED'
                        ? 'bg-amber-300 text-amber-900 cursor-not-allowed'
                        : chosen
                          ? 'bg-red-600 text-white ring-2 ring-red-200'
                          : 'bg-zinc-100 text-zinc-500 hover:bg-red-100 hover:text-red-700';
                    return (
                      <button key={key} type="button" onClick={() => toggle(row, col)} disabled={Boolean(lockedState) || submitting}
                        aria-label={`${row}排${col}座${chosen ? '已选' : lockedState === 'SOLD' ? '已售' : lockedState === 'LOCKED' ? '锁定中' : '可选'}`}
                        className={`h-8 w-8 rounded-t-lg rounded-b-sm text-[10px] font-semibold transition ${style}`}>
                        {col}
                      </button>
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-5 flex flex-wrap justify-center gap-4 border-t border-zinc-100 pt-5 text-xs text-zinc-500">
          {[['bg-zinc-100', '可选'], ['bg-red-600', '已选'], ['bg-amber-300', '锁定中'], ['bg-zinc-700', '已售']].map(([color, label]) => (
            <span key={label} className="flex items-center gap-2"><i className={`h-3 w-3 rounded ${color}`} />{label}</span>
          ))}
          <span className="flex items-center gap-2"><i className="h-3 w-3 border-x border-zinc-300" />过道/空位</span>
        </div>
      </section>

      <aside className="h-fit rounded-3xl bg-zinc-950 p-6 text-white card-shadow lg:sticky lg:top-24">
        <p className="text-xs font-semibold text-red-400">BOOKING SUMMARY</p>
        <h2 className="mt-2 text-xl font-black">订单确认</h2>
        <dl className="mt-6 space-y-3 text-sm">
          <div className="flex justify-between"><dt className="text-zinc-400">已选座位</dt><dd>{selected.length} 个</dd></div>
          <div className="flex justify-between"><dt className="text-zinc-400">单价</dt><dd>¥{layout.price}</dd></div>
        </dl>
        <div className="mt-4 flex max-h-28 flex-wrap gap-2 overflow-y-auto">
          {selected.map((seat) => <span key={keyOf(seat.row, seat.col)} className="rounded-lg bg-white/10 px-2 py-1 text-xs">{seat.row}排{seat.col}座</span>)}
          {!selected.length ? <span className="text-sm text-zinc-500">暂未选择座位</span> : null}
        </div>
        <div className="mt-6 border-t border-white/10 pt-5">
          <div className="flex items-end justify-between"><span className="text-sm text-zinc-400">合计</span><strong className="text-3xl text-red-400">¥{totalPrice}</strong></div>
          <button onClick={seize} disabled={submitting || !selected.length}
            className="mt-5 w-full rounded-xl bg-red-600 py-3 font-bold transition hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-40">
            {submitting ? '订单处理中…' : '提交抢座'}
          </button>
          {progress ? <p className="mt-3 text-center text-xs text-red-300">{progress}</p> : null}
          {notice ? <p className="mt-3 rounded-lg bg-red-500/15 p-3 text-xs text-red-200">{notice}</p> : null}
        </div>
      </aside>
    </div>
  );
}
