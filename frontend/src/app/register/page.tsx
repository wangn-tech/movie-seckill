'use client';
import { useState } from 'react';
import { api } from '@/lib/api';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

export default function RegisterPage() {
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const router = useRouter();

  const register = async () => {
    try {
      await api.post('/auth/register', { account, password, nickname });
      alert('注册成功，请登录');
      router.push('/login');
    } catch (e: any) {
      alert(e.response?.data?.message || '注册失败');
    }
  };

  return (
    <div className="max-w-sm mx-auto mt-20 bg-white p-8 rounded shadow">
      <h1 className="text-xl font-bold mb-4">注册</h1>
      <input className="w-full border p-2 mb-3 rounded" placeholder="手机号" value={account}
        onChange={(e) => setAccount(e.target.value)} />
      <input className="w-full border p-2 mb-3 rounded" type="password" placeholder="密码" value={password}
        onChange={(e) => setPassword(e.target.value)} />
      <input className="w-full border p-2 mb-4 rounded" placeholder="昵称（可选）" value={nickname}
        onChange={(e) => setNickname(e.target.value)} />
      <button onClick={register} className="w-full bg-red-600 text-white p-2 rounded">注册</button>
      <div className="text-sm text-gray-500 mt-3 text-center">
        已有账号？<Link href="/login" className="text-red-600">去登录</Link>
      </div>
    </div>
  );
}
