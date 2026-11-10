import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { safeNext } from '../src/navigation'
import { api, ApiError } from '../src/api/client'
import { useSession } from '../src/stores/session'
beforeEach(() => { setActivePinia(createPinia()); vi.unstubAllGlobals() })
describe('safe return destination', () => {
  it('allows known internal screens only', () => {
    expect(safeNext('/money?month=2026-11')).toBe('/money?month=2026-11')
    for (const value of ['https://evil.test', '//evil.test', '/\\evil.test', '/login', '/onboarding', '/%2f%2fevil.test', '/today\n', undefined, ['/money']]) expect(safeNext(value)).toBe('/today')
  })
  it('uses role-aware defaults and reserves admin destinations for administrators', () => {
    expect(safeNext(undefined, 'ADMIN')).toBe('/admin')
    expect(safeNext('https://evil.test', 'ADMIN')).toBe('/admin')
    expect(safeNext('/admin?section=users', 'ADMIN')).toBe('/admin?section=users')
    expect(safeNext('/today', 'ADMIN')).toBe('/today')
    expect(safeNext('/admin', 'USER')).toBe('/today')
  })
})
describe('server session', () => {
  it('distinguishes an anonymous session from an outage and clears stale personal data', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}', { status: 401 }))
    vi.stubGlobal('fetch', fetch)
    const store = useSession()
    await store.refresh()
    expect(store.checked).toBe(true); expect(store.session).toBeNull(); expect(store.unavailable).toBe(false)
    store.session = { user: { id: 'user-a', email: 'a@example.test', displayName: 'A', timezone: 'UTC', baseCurrency: 'KRW', role: 'USER', guest: false }, onboardingCompleted: true }
    fetch.mockRejectedValueOnce(new TypeError('offline'))
    await expect(store.refresh()).rejects.toThrow('offline')
    expect(store.session).toBeNull(); expect(store.checked).toBe(false); expect(store.unavailable).toBe(true)
  })
  it('sends a fresh server-issued CSRF token with each mutation', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-one', headerName: 'X-CSRF-TOKEN' }))).mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetch)
    await api('/auth/logout', { method: 'POST' })
    expect(fetch.mock.calls[0][0]).toBe('/api/v1/auth/csrf')
    expect(fetch.mock.calls[1][1].headers.get('X-CSRF-TOKEN')).toBe('csrf-one')
    expect(fetch.mock.calls[1][1].credentials).toBe('same-origin')
  })
  it('does not submit changes when CSRF initialization fails', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}', { status: 503 }))
    vi.stubGlobal('fetch', fetch)
    await expect(api('/auth/signup', { method: 'POST' })).rejects.toBeInstanceOf(ApiError)
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})
