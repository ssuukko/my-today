export function safeNext(value: unknown, role: 'ADMIN' | 'USER' = 'USER'): string {
  const fallback = role === 'ADMIN' ? '/admin' : '/today'
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\s\u0000-\u001f]/u.test(value)) return fallback
  const path = value.split(/[?#]/)[0]
  return ['/today', '/calendar', '/tasks', '/growth', '/money', '/invest', '/settings', ...(role === 'ADMIN' ? ['/admin'] : [])].includes(path) ? value : fallback
}
