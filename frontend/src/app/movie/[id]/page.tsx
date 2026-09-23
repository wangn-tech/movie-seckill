'use client';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import Link from 'next/link';

export default function MovieDetail({ params }: { params: { id: string } }) {
  const id = params.id;
  const [movie, setMovie] = useState<any>({});
  const [schedules, setSchedules] = useState<any[]>([]);

  useEffect(() => {
    api.get(`/movies/${id}`).then((r: any) => setMovie(r.data || {}));
    // 简化：先查影院列表，再取第一个影院的场次
    api.get('/cinemas').then(async (r: any) => {
      if (r.data?.length) {
        const c = r.data[0];
        const s: any = await api.get(`/schedules?movieId=${id}&cinemaId=${c.id}`);
        setSchedules(s.data || []);
      }
    });
  }, [id]);

  return (
    <div>
      <h1 className="text-xl font-bold">{movie.name}</h1>
      <p className="text-gray-600 mb-4">{movie.genre} · {movie.duration}分钟</p>
      <h2 className="font-bold mb-2">选择场次</h2>
      <div className="space-y-2">
        {schedules.map((s) => (
          <Link key={s.id} href={`/seat/${s.id}`} className="block bg-white p-3 rounded shadow hover:shadow-md">
            <div>{s.showDate} {s.showTime} · {s.hallName}</div>
            <div className="text-red-600">¥{s.price} · 余票 {s.availableSeats}</div>
          </Link>
        ))}
      </div>
    </div>
  );
}
