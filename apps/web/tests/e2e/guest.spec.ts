import { test, expect } from '@playwright/test'

test('guest starts without credentials and can open private feature screens', async ({ page }) => {
  let guest = false, creations = 0
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/auth/csrf') return reply({ token: 'guest-csrf', headerName: 'X-CSRF-TOKEN' })
    if (path === '/auth/guest') { guest = true; creations++; return reply({ user: { id: 'guest-1', email: 'guest@example.invalid', displayName: '게스트', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: 'USER', guest: true }, onboardingCompleted: true }) }
    if (path === '/auth/session') return guest ? reply({ user: { id: 'guest-1', email: 'guest@example.invalid', displayName: '게스트', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: 'USER', guest: true }, onboardingCompleted: true }) : reply({ message: '로그인이 필요합니다.' }, 401)
    if (path === '/auth/logout') { guest = false; return route.fulfill({ status: 204 }) }
    if (!guest) return reply({ message: '로그인이 필요합니다.' }, 401)
    if (path.startsWith('/dashboard/today')) return reply({ date: '2026-11-10', timezone: 'Asia/Seoul', eventCount: 0, taskCount: 0, completedTaskCount: 0, events: [], tasks: [] })
    if (path.startsWith('/growth')) return reply({ date: '2026-11-10', timezone: 'Asia/Seoul', totalMinutes: 0, activityCount: 0, activities: [], nextActivityCursor: null, routines: [], occurrences: [], goals: [], reflection: null })
    if (path.startsWith('/money')) return reply({ month: '2026-11', accounts: [], transactions: [], totals: [], budgets: [], hasMore: false })
    if (path.startsWith('/invest')) return reply({ instruments: [], trades: [], totals: [] })
    return reply({ message: '미구현 fixture' }, 404)
  })
  await page.goto('/welcome')
  await page.getByRole('button', { name: '게스트로 체험하기' }).click()
  await expect(page).toHaveURL(/\/today$/)
  await expect(page.getByText('게스트 체험 중')).toBeVisible()
  for (const route of ['/growth', '/money', '/invest']) {
    await page.goto(route)
    await expect(page.getByText('게스트 체험 중')).toBeVisible()
  }
  expect(creations).toBe(1)
  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/welcome$/)
  await page.goto('/money')
  await expect(page).toHaveURL(/\/login\?next=/)
})
