'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { errorMessage, postData } from '@/lib/api';

export default function RegisterPage() {
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const register = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    if (!/^1[3-9]\d{9}$/.test(account)) return setError('请输入正确的 11 位手机号');
    if (password.length < 6) return setError('密码至少 6 位');
    setLoading(true);
    try {
      await postData<number>('/auth/register', { account, password, nickname: nickname || null });
      router.push('/login');
    } catch (registerError) {
      setError(errorMessage(registerError, '注册失败'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={register} className="mx-auto max-w-md rounded-3xl border border-zinc-200 bg-white p-7 card-shadow sm:p-10">
      <p className="text-xs font-semibold text-red-600">CREATE ACCOUNT</p>
      <h1 className="mt-2 text-3xl font-black">注册演示账号</h1>
      <p className="mt-2 text-sm text-zinc-400">注册后将获得 1000 元模拟余额</p>
      {error ? <div className="mt-5 rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</div> : null}
      <label className="mt-6 block text-sm font-semibold">手机号<input className="mt-2 w-full rounded-xl border border-zinc-200 px-4 py-3 focus:border-red-400 focus:outline-none" value={account} onChange={(event) => setAccount(event.target.value)} autoComplete="username" /></label>
      <label className="mt-4 block text-sm font-semibold">密码<input className="mt-2 w-full rounded-xl border border-zinc-200 px-4 py-3 focus:border-red-400 focus:outline-none" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" /></label>
      <label className="mt-4 block text-sm font-semibold">昵称（可选）<input className="mt-2 w-full rounded-xl border border-zinc-200 px-4 py-3 focus:border-red-400 focus:outline-none" value={nickname} onChange={(event) => setNickname(event.target.value)} /></label>
      <button disabled={loading} className="mt-6 w-full rounded-xl bg-red-600 py-3 font-bold text-white hover:bg-red-700 disabled:opacity-50">{loading ? '注册中…' : '创建账号'}</button>
      <p className="mt-4 text-center text-sm text-zinc-500">已有账号？<Link href="/login" className="font-semibold text-red-600">返回登录</Link></p>
    </form>
  );
}
