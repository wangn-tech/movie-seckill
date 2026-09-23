'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import Link from 'next/link';

export default function Home() {
  const [movies, setMovies] = useState<any[]>([]);
  useEffect(() => { api.get('/movies/hot').then((r: any) => setMovies(r.data || [])).catch(() => {}); }, []);

  return (
    <div>
      <h1 className="text-xl font-bold mb-4">正在热映</h1>
      <div className="grid grid-cols-3 gap-4">
        {movies.map((m) => (
          <Link key={m.id} href={`/movie/${m.id}`} className="bg-white rounded p-3 shadow hover:shadow-lg">
            <div className="h-40 bg-gray-200 rounded mb-2 flex items-center justify-center text-gray-400">海报</div>
            <div className="font-medium">{m.name}</div>
            <div className="text-sm text-orange-500">评分 {m.score}</div>
          </Link>
        ))}
      </div>
    </div>
  );
}
