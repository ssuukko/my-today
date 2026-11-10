export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}
export interface Session {
  user: { id: string; email: string; displayName: string; timezone: string; baseCurrency: string; role: 'ADMIN' | 'USER'; guest: boolean }
  onboardingCompleted: boolean
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body) headers.set('Content-Type', 'application/json')
  if (options.method && !['GET', 'HEAD'].includes(options.method)) {
    const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    if (!response.ok) throw new ApiError(response.status, '보안 연결을 준비하지 못했습니다. 다시 시도해 주세요.')
    const csrf: { token: string; headerName: string } = await response.json()
    headers.set(csrf.headerName, csrf.token)
  }
  const response = await fetch(`/api/v1${path}`, { ...options, headers, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new ApiError(response.status, body.message || body.detail || '요청을 처리하지 못했습니다. 다시 시도해 주세요.')
  }
  return response.status === 204 ? undefined as T : response.json()
}
