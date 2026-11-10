import { test, expect, type Page } from '@playwright/test'

async function mockAdmin(page: Page, initialRole: 'ADMIN' | 'USER' = 'ADMIN') {
  const state = { role: initialRole, denied: false, unauthorized: false, unavailable: false, cursorRequested: false, administratorReads: 0 }
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    const session = () => ({ user: { id: 'admin-1', email: 'operator@example.test', displayName: '운영자', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: state.role }, onboardingCompleted: true })
    if (path === '/auth/session' || path === '/auth/login') return reply(session())
    if (path === '/auth/csrf') return reply({ token: 'fixture-csrf', headerName: 'X-CSRF-TOKEN' })
    if (path === '/dashboard/today') return reply({ date: '2026-11-08', timezone: 'Asia/Seoul', events: [], tasks: [], eventCount: 0, taskCount: 0, completedTaskCount: 0 })
    if (path.startsWith('/admin/')) {
      state.administratorReads++
      if (state.unauthorized) return reply({ message: '로그인이 필요합니다.' }, 401)
      if (state.denied) return reply({ message: '관리자 권한이 필요합니다.' }, 403)
      if (state.unavailable) return reply({ message: '일시적으로 연결할 수 없습니다.' }, 503)
      if (path === '/admin/invitation') return reply({ enabled: true, inviteToken: 'fixture-invite-not-a-real-secret' })
      if (path === '/admin/users') {
        state.cursorRequested = url.searchParams.get('cursor') === '100'
        return reply({ items: [{ id: state.cursorRequested ? 'member-2' : 'member-1', email: state.cursorRequested ? 'second@example.test' : 'member@example.test', displayName: state.cursorRequested ? '다음 회원' : '일반 회원', role: 'USER', onboardingCompleted: false, createdAt: '2026-11-08T00:00:00Z' }], nextCursor: state.cursorRequested ? null : '100', total: 2 })
      }
    }
    return reply({ message: '요청을 찾을 수 없습니다.' }, 404)
  })
  return state
}

test('administrators land in admin and can reveal a masked invitation without personal records', async ({ page }) => {
  await mockAdmin(page)
  await page.goto('/login')
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('heading', { name: '회원과 초대 관리', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '관리자 화면', exact: true })).toBeVisible()
  await expect(page.getByText('member@example.test', { exact: true })).toBeVisible()
  const invite = page.getByLabel('가입 초대 코드', { exact: true })
  await expect(invite).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '초대 코드 표시', exact: true }).click()
  await expect(invite).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: '초대 코드 숨기기', exact: true }).click()
  await expect(invite).toHaveAttribute('type', 'password')
  await expect(page.getByRole('heading', { name: '오늘의 타임라인', exact: true })).toHaveCount(0)
})

test('ordinary users cannot enter admin or trigger administrative API reads', async ({ page }) => {
  const state = await mockAdmin(page, 'USER')
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/today$/)
  await expect(page.getByRole('link', { name: '관리자 화면', exact: true })).toHaveCount(0)
  await expect(page.getByLabel('가입 초대 코드', { exact: true })).toHaveCount(0)
  expect(state.administratorReads).toBe(0)
})

test('a server-side revocation clears stale members and invitation before returning to today', async ({ page }) => {
  const state = await mockAdmin(page)
  await page.goto('/admin')
  await expect(page.getByText('member@example.test', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '초대 코드 표시', exact: true }).click()
  state.role = 'USER'; state.denied = true
  await page.getByRole('button', { name: '회원 더 불러오기', exact: true }).click()
  await expect(page).toHaveURL(/\/today$/)
  await expect(page.getByText('member@example.test', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('가입 초대 코드', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '관리자 화면', exact: true })).toHaveCount(0)
})

test('member pagination and a 360px administrator screen stay usable', async ({ page }) => {
  const state = await mockAdmin(page)
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/admin')
  await page.getByRole('button', { name: '회원 더 불러오기', exact: true }).click()
  await expect(page.getByText('second@example.test', { exact: true })).toBeVisible()
  expect(state.cursorRequested).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await expect(page.getByRole('link', { name: '관리자 화면', exact: true })).toBeVisible()
})

test('administrator reads offer retry without retaining unavailable data', async ({ page }) => {
  const state = await mockAdmin(page)
  state.unavailable = true
  await page.goto('/admin')
  await expect(page.getByRole('button', { name: '다시 시도', exact: true })).toBeVisible()
  await expect(page.getByLabel('가입 초대 코드', { exact: true })).toHaveCount(0)
  state.unavailable = false
  await page.getByRole('button', { name: '다시 시도', exact: true }).click()
  await expect(page.getByText('member@example.test', { exact: true })).toBeVisible()
})

test('an expired administrator session clears privileged content and requests login', async ({ page }) => {
  const state = await mockAdmin(page)
  await page.goto('/admin')
  await expect(page.getByText('member@example.test', { exact: true })).toBeVisible()
  state.unauthorized = true
  await page.getByRole('button', { name: '회원 더 불러오기', exact: true }).click()
  await expect(page).toHaveURL(/\/login\?next=/)
  await expect(page.getByText('member@example.test', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('가입 초대 코드', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('이메일', { exact: true })).toBeVisible()
})

test('unsupported clipboard access provides a usable invitation fallback', async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined }))
  await mockAdmin(page)
  await page.goto('/admin')
  await page.getByRole('button', { name: '초대 코드 복사', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('자동 복사를 사용할 수 없습니다.')
  await page.getByRole('button', { name: '초대 코드 표시', exact: true }).click()
  await expect(page.getByLabel('가입 초대 코드', { exact: true })).toHaveAttribute('type', 'text')
})

test('real API administrator login exposes only account administration', async ({ page }) => {
  test.skip(!process.env.E2E_ADMIN_EMAIL || !process.env.E2E_ADMIN_PASSWORD, 'Requires an explicitly configured development administrator')
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill(process.env.E2E_ADMIN_EMAIL!)
  await page.getByLabel('비밀번호', { exact: true }).fill(process.env.E2E_ADMIN_PASSWORD!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/(?:onboarding(?:\?.*)?|admin)$/)
  if (new URL(page.url()).pathname === '/onboarding') await page.getByRole('button', { name: '오늘 시작하기', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('heading', { name: '회원과 초대 관리', exact: true })).toBeVisible()
  await expect(page.getByText(process.env.E2E_ADMIN_EMAIL!, { exact: true })).toBeVisible()
  await expect(page.getByLabel('가입 초대 코드', { exact: true })).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '로그아웃', exact: true }).click()
  await expect(page).toHaveURL(/\/welcome$/)
})
