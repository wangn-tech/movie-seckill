'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { errorMessage, postData } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import type { AuthPayload } from '@/types/api';

export default function LoginPage() {
  const [account, setAccount] = useState('13800000001');
  const [password, setPassword] = useState('123456');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const setTokens = useAuthStore((state) => state.setTokens);
  const router = useRouter();

  const login = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    if (!/^1[3-9]\d{9}$/.test(account)) return setError('请输入正确的手机号');
    if (password.length < 6) return setError('密码至少 6 位');
    setLoading(true);
    try {
      const payload = await postData<AuthPayload>('/auth/login', { account, password });
      setTokens(payload);
      const next = typeof window !== 'undefined'
        ? new URLSearchParams(window.location.search).get('next')
        : null;
      router.push(next?.startsWith('/') ? next : '/');
    } catch (loginError) {
      setError(errorMessage(loginError, '登录失败'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto grid max-w-4xl overflow-hidden rounded-3xl border border-zinc-200 bg-white card-shadow md:grid-cols-2">
      <div className="hidden bg-zinc-950 p-10 text-white md:flex md:flex-col md:justify-between">
        <span className="grid h-12 w-12 place-items-center rounded-2xl bg-red-600 text-xl font-black">影</span>
        <div><p className="text-sm text-red-400">WELCOME BACK</p><h1 className="mt-3 text-4xl font-black">登录后，开始抢座</h1><p className="mt-4 text-sm leading-6 text-zinc-400">体验 Redis Lua 原子预扣、RocketMQ 异步落单和 Outbox 可靠消息。</p></div>
      </div>
      <form onSubmit={login} className="p-7 sm:p-10">
        <h2 className="text-2xl font-black">账号登录</h2>
        <p className="mt-2 text-sm text-zinc-400">演示账号已为你预填</p>
        {error ? <div className="mt-5 rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</div> : null}
        <label className="mt-6 block text-sm font-semibold">手机号<input className="mt-2 w-full rounded-xl border border-zinc-200 px-4 py-3 outline-none focus:border-red-400" value={account} onChange={(event) => setAccount(event.target.value)} autoComplete="username" /></label>
        <label className="mt-4 block text-sm font-semibold">密码<input className="mt-2 w-full rounded-xl border border-zinc-200 px-4 py-3 outline-none focus:border-red-400" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" /></label>
        <button disabled={loading} className="mt-6 w-full rounded-xl bg-red-600 py-3 font-bold text-white hover:bg-red-700 disabled:opacity-50">{loading ? '登录中…' : '登录'}</button>
        <p className="mt-4 text-center text-sm text-zinc-500">没有账号？<Link href="/register" className="font-semibold text-red-600">立即注册</Link></p>
      </form>
    </div>
  );
}
