'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import Link from 'next/link';

export default function MovieDetail({ params }: { params: { id: string } }) {
  const id = params.id;
  const [movie, setMovie] = useState<any>({});
  const [cinemas, setCinemas] = useState<any[]>([]);
  const [selectedCinema, setSelectedCinema] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<any[]>([]);

  useEffect(() => {
    api.get(`/movies/${id}`).then((r: any) => setMovie(r.data || {}));
    api.get('/cinemas').then((r: any) => {
      setCinemas(r.data || []);
      if (r.data?.length) {
        setSelectedCinema(r.data[0].id);
      }
    });
  }, [id]);

  useEffect(() => {
    if (selectedCinema) {
      api.get(`/schedules?movieId=${id}&cinemaId=${selectedCinema}`).then((r: any) => setSchedules(r.data || []));
    }
  }, [selectedCinema, id]);

  return (
    <div>
      <h1 className="text-xl font-bold">{movie.name}</h1>
      <p className="text-gray-600 mb-4">{movie.genre} · {movie.duration}分钟 · 评分 {movie.score}</p>
      <p className="text-gray-700 mb-4">{movie.description}</p>

      <h2 className="font-bold mb-2">选择影院</h2>
      <div className="flex gap-2 mb-4 flex-wrap">
        {cinemas.map((c) => (
          <button key={c.id} onClick={() => setSelectedCinema(c.id)}
            className={`px-3 py-1 rounded border ${selectedCinema === c.id ? 'bg-red-600 text-white border-red-600' : 'bg-white'}`}>
            {c.name}
          </button>
        ))}
      </div>

      <h2 className="font-bold mb-2">场次</h2>
      <div className="space-y-2">
        {schedules.map((s) => (
          <Link key={s.id} href={`/seat/${s.id}`} className="block bg-white p-3 rounded shadow hover:shadow-md">
            <div>{s.showDate} {s.showTime} · {s.hallName}</div>
            <div className="text-red-600">¥{s.price} · 余票 {s.availableSeats}</div>
          </Link>
        ))}
        {!schedules.length && <div className="text-gray-400">该影院暂无此电影场次</div>}
      </div>
    </div>
  );
}
