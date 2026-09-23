'use client';
import { useState } from 'react';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

export default function LoginPage() {
  const [account, setAccount] = useState('13800000001');
  const [password, setPassword] = useState('123456');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const setTokens = useAuthStore((s) => s.setTokens);
  const router = useRouter();

  const login = async () => {
    setError('');
    if (!/^1[3-9]\d{9}$/.test(account)) {
      setError('请输入正确的手机号');
      return;
    }
    if (!password || password.length < 6) {
      setError('密码至少 6 位');
      return;
    }
    setLoading(true);
    try {
      const res: any = await api.post('/auth/login', { account, password });
      setTokens(res.data);
      router.push('/');
    } catch (e: any) {
      setError(e.response?.data?.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-sm mx-auto mt-20 bg-white p-8 rounded shadow">
      <h1 className="text-xl font-bold mb-4">登录</h1>
      {error && <div className="mb-3 text-red-600 text-sm bg-red-50 p-2 rounded">{error}</div>}
      <input className="w-full border p-2 mb-3 rounded" placeholder="手机号" value={account}
        onChange={(e) => setAccount(e.target.value)} />
      <input className="w-full border p-2 mb-4 rounded" type="password" placeholder="密码" value={password}
        onChange={(e) => setPassword(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && login()} />
      <button onClick={login} disabled={loading}
        className="w-full bg-red-600 text-white p-2 rounded disabled:opacity-50">
        {loading ? '登录中...' : '登录'}
      </button>
      <div className="text-sm text-gray-500 mt-3">测试账号: 13800000001 / 13800000002, 密码 123456</div>
      <div className="text-sm text-gray-500 mt-2 text-center">
        没有账号？<Link href="/register" className="text-red-600">去注册</Link>
      </div>
    </div>
  );
}
