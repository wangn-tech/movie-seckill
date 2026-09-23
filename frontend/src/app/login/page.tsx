'use client';
import { useState } from 'react';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import { useRouter } from 'next/navigation';

export default function LoginPage() {
  const [account, setAccount] = useState('13800000001');
  const [password, setPassword] = useState('123456');
  const setTokens = useAuthStore((s) => s.setTokens);
  const router = useRouter();

  const login = async () => {
    try {
      const res: any = await api.post('/auth/login', { account, password });
      setTokens(res.data);
      router.push('/');
    } catch (e: any) {
      alert(e.response?.data?.message || '登录失败');
    }
  };

  return (
    <div className="max-w-sm mx-auto mt-20 bg-white p-8 rounded shadow">
      <h1 className="text-xl font-bold mb-4">登录</h1>
      <input className="w-full border p-2 mb-3 rounded" placeholder="账号" value={account} onChange={(e) => setAccount(e.target.value)} />
      <input className="w-full border p-2 mb-4 rounded" type="password" placeholder="密码" value={password} onChange={(e) => setPassword(e.target.value)} />
      <button onClick={login} className="w-full bg-red-600 text-white p-2 rounded">登录</button>
      <div className="text-sm text-gray-500 mt-3">测试账号: 13800000001 / 13800000002, 密码 123456</div>
    </div>
  );
}
