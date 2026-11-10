import { test, expect } from '@playwright/test'

test('first onboarding celebrates once, then explains every menu and stays usable on mobile', async ({ page }) => {
  let onboarding = false
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    const session = () => ({ user: { id: 'first-user', email: 'first@example.test', displayName: '새 사용자', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: 'USER', guest: false }, onboardingCompleted: onboarding })
    if (path === '/auth/session') return reply(session())
    if (path === '/auth/csrf') return reply({ token: 'fixture-csrf', headerName: 'X-CSRF-TOKEN' })
    if (path === '/users/me/settings') { onboarding = true; return reply(session()) }
    if (path.startsWith('/dashboard/today')) return reply({ date: '2026-11-10', timezone: 'Asia/Seoul', events: [], tasks: [], eventCount: 0, taskCount: 0, completedTaskCount: 0 })
    return reply({ message: 'Fixture API not found' }, 404)
  })
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/onboarding')
  await page.getByRole('button', { name: '오늘 시작하기' }).click()
  await expect(page).toHaveURL(/\/today$/)
  await expect(page.getByRole('heading', { name: '새 사용자님, 시작을 축하해요!' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '사용 가이드 보기' }).click()
  for (const title of ['오늘', '일정', '할 일', '성장', '가계부', '투자']) {
    await expect(page.getByRole('dialog').getByRole('heading', { name: title, exact: true })).toBeVisible()
    await page.getByRole('button', { name: title === '투자' ? '시작하기' : '다음' }).click()
  }
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: '새 사용자님, 시작을 축하해요!' })).toHaveCount(0)
  await page.getByRole('button', { name: '사용 가이드' }).click()
  await expect(page.getByRole('dialog').getByRole('heading', { name: '오늘', exact: true })).toBeVisible()
  await expect(page.getByText('새 사용자님, 시작을 축하해요!')).toHaveCount(0)
  await page.getByRole('button', { name: '나중에' }).click()
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('button', { name: '사용 가이드' }).click()
  await expect(page.getByRole('dialog').getByRole('heading', { name: '오늘', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '나중에' }).click()
})
