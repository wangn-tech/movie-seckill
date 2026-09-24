'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import useSWR from 'swr';
import { getData } from '@/lib/api';
import type { Cinema, Movie, Schedule } from '@/types/api';

export default function MovieDetail({ params }: { params: { id: string } }) {
  const movieId = Number(params.id);
  const { data: movie, error: movieError } = useSWR<Movie>(`/movies/${movieId}`, getData);
  const { data: cinemas } = useSWR<Cinema[]>('/cinemas', getData);
  const [selectedCinema, setSelectedCinema] = useState<number | null>(null);

  useEffect(() => {
    if (!selectedCinema && cinemas?.length) setSelectedCinema(cinemas[0].id);
  }, [cinemas, selectedCinema]);

  const scheduleUrl = selectedCinema ? `/schedules?movieId=${movieId}&cinemaId=${selectedCinema}` : null;
  const { data: schedules, isLoading: schedulesLoading } = useSWR<Schedule[]>(scheduleUrl, getData);

  if (movieError) return <div className="rounded-2xl bg-red-50 p-8 text-red-700">电影信息加载失败，请返回重试。</div>;
  if (!movie) return <div className="h-96 animate-pulse rounded-3xl bg-zinc-200" />;

  return (
    <div className="space-y-8">
      <section className="grid overflow-hidden rounded-3xl bg-zinc-950 text-white card-shadow md:grid-cols-[280px_1fr]">
        <div className="relative min-h-80 bg-zinc-800">
          <Image src={movie.poster || '/posters/cinema.svg'} alt={`${movie.name}海报`} fill sizes="280px" className="object-cover" />
        </div>
        <div className="flex flex-col justify-center p-7 sm:p-10">
          <p className="text-sm font-semibold text-red-400">正在热映 · {movie.score} 分</p>
          <h1 className="mt-2 text-3xl font-black sm:text-4xl">{movie.name}</h1>
          <p className="mt-3 text-sm text-zinc-300">{movie.genre} · {movie.duration} 分钟</p>
          {movie.actors ? <p className="mt-2 text-sm text-zinc-400">主演：{movie.actors}</p> : null}
          <p className="mt-6 max-w-2xl leading-7 text-zinc-300">{movie.description}</p>
        </div>
      </section>

      <section className="rounded-3xl border border-zinc-200 bg-white p-5 card-shadow sm:p-7">
        <div className="mb-5">
          <p className="text-xs font-semibold text-red-600">SELECT CINEMA</p>
          <h2 className="mt-1 text-xl font-black">选择影院与场次</h2>
        </div>
        <div className="flex gap-2 overflow-x-auto pb-2">
          {(cinemas ?? []).map((cinema) => (
            <button key={cinema.id} onClick={() => setSelectedCinema(cinema.id)}
              className={`shrink-0 rounded-xl border px-4 py-3 text-left transition ${selectedCinema === cinema.id ? 'border-red-600 bg-red-600 text-white' : 'border-zinc-200 hover:border-red-300'}`}>
              <span className="block font-semibold">{cinema.name}</span>
              <span className={`mt-1 block text-xs ${selectedCinema === cinema.id ? 'text-red-100' : 'text-zinc-400'}`}>{cinema.address}</span>
            </button>
          ))}
        </div>
        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          {schedulesLoading ? <div className="h-24 animate-pulse rounded-xl bg-zinc-100" /> : null}
          {(schedules ?? []).map((schedule) => (
            <Link key={schedule.id} href={`/seat/${schedule.id}`}
              className="flex items-center justify-between rounded-2xl border border-zinc-200 p-4 transition hover:border-red-300 hover:bg-red-50/40">
              <div>
                <p className="text-lg font-black">{schedule.showTime}</p>
                <p className="mt-1 text-xs text-zinc-500">{schedule.showDate} · {schedule.hallName}</p>
              </div>
              <div className="text-right">
                <p className="font-black text-red-600">¥{schedule.price}</p>
                <p className="mt-1 text-xs text-zinc-400">余 {schedule.availableSeats}</p>
              </div>
            </Link>
          ))}
        </div>
        {!schedulesLoading && schedules?.length === 0 ? <p className="mt-6 rounded-xl bg-zinc-50 p-5 text-center text-sm text-zinc-400">该影院暂无场次</p> : null}
      </section>
    </div>
  );
}
