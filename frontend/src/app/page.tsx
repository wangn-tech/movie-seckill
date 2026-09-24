'use client';

import Image from 'next/image';
import Link from 'next/link';
import useSWR from 'swr';
import { getData } from '@/lib/api';
import type { Movie } from '@/types/api';

const fallbackPoster = '/posters/cinema.svg';

export default function Home() {
  const { data: movies, error, isLoading, mutate } = useSWR<Movie[]>('/movies/hot', getData);

  return (
    <div className="space-y-10">
      <section className="overflow-hidden rounded-3xl bg-zinc-950 px-6 py-10 text-white card-shadow sm:px-10">
        <div className="max-w-2xl">
          <span className="rounded-full bg-red-600/20 px-3 py-1 text-xs font-semibold text-red-300">高并发抢座演示</span>
          <h1 className="mt-5 text-3xl font-black leading-tight sm:text-5xl">热门电影，座位先到先得</h1>
          <p className="mt-4 max-w-xl text-sm leading-6 text-zinc-300 sm:text-base">
            Redis Lua 原子锁座，RocketMQ 异步落单，MySQL 条件更新与唯一索引双重兜底。
          </p>
          <div className="mt-7 flex flex-wrap gap-3 text-xs text-zinc-300">
            {['原子预扣库存', '异步削峰', '幂等不重单', '超时自动释放'].map((label) => (
              <span key={label} className="rounded-lg border border-white/10 bg-white/5 px-3 py-2">{label}</span>
            ))}
          </div>
        </div>
      </section>

      <section>
        <div className="mb-5 flex items-end justify-between">
          <div>
            <p className="text-sm font-semibold text-red-600">NOW SHOWING</p>
            <h2 className="mt-1 text-2xl font-black">正在热映</h2>
          </div>
          <span className="text-sm text-zinc-400">{movies?.length ?? 0} 部影片</span>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => <div key={index} className="h-80 animate-pulse rounded-2xl bg-zinc-200" />)}
          </div>
        ) : error ? (
          <div className="rounded-2xl border border-red-100 bg-red-50 p-8 text-center text-red-700">
            电影加载失败。<button onClick={() => mutate()} className="ml-2 font-semibold underline">重新加载</button>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {(movies ?? []).map((movie) => (
              <Link key={movie.id} href={`/movie/${movie.id}`}
                className="group overflow-hidden rounded-2xl border border-zinc-200 bg-white transition hover:-translate-y-1 hover:border-red-200 card-shadow">
                <div className="relative aspect-[3/4] overflow-hidden bg-zinc-900">
                  <Image src={movie.poster || fallbackPoster} alt={`${movie.name}海报`} fill sizes="(max-width: 640px) 50vw, 25vw"
                    className="object-cover transition duration-500 group-hover:scale-105" />
                  <span className="absolute right-3 top-3 rounded-full bg-black/65 px-2 py-1 text-xs font-bold text-amber-300">{movie.score}</span>
                </div>
                <div className="p-4">
                  <h3 className="truncate font-bold">{movie.name}</h3>
                  <p className="mt-1 truncate text-xs text-zinc-500">{movie.genre} · {movie.duration} 分钟</p>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
