export type CurrentUser = {
  userId: string
  email: string
  nickname: string
  picture: string
}

export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    const res = await fetch('/api/me', { cache: 'no-store', credentials: 'same-origin' })
    if (!res.ok) return null
    return (await res.json()) as CurrentUser
  } catch {
    return null
  }
}
