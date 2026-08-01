'use client'

import { useEffect, useState } from 'react'
import { fetchCurrentUser, type CurrentUser } from '@/lib/api'

export default function Home() {
  const [user, setUser] = useState<CurrentUser | null | undefined>(undefined)

  useEffect(() => {
    fetchCurrentUser().then(setUser)
  }, [])

  if (user === undefined) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-gray-500">加载中…</p>
      </main>
    )
  }

  if (!user) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4">
        <h1 className="text-3xl font-bold">demo 消费方</h1>
        <p className="text-gray-600">未登录</p>
        <a href="/auth/login" className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
          登录（跳 identity SSO）
        </a>
      </main>
    )
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-3xl font-bold">已登录</h1>
      <dl className="text-center">
        <dt className="text-gray-500">userId</dt><dd className="font-mono">{user.userId}</dd>
        <dt className="text-gray-500">nickname</dt><dd>{user.nickname}</dd>
        <dt className="text-gray-500">email</dt><dd>{user.email}</dd>
      </dl>
      <form action="/auth/logout" method="post">
        <button type="submit" className="rounded border px-4 py-2 hover:bg-gray-100">登出</button>
      </form>
    </main>
  )
}
