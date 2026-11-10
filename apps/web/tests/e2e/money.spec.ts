import { test, expect, type Page } from '@playwright/test'
import type { Money, MoneyTransaction } from '../../src/api/life'

const today = '2026-11-08'
const account = (id: string, name: string, balance = '100000') => ({ id, name, type: 'CHECKING', currency: 'KRW', openingBalance: balance, balance })
async function mockMoney(page: Page, populated = false) {
  const data: Money = { month: '2026-11', accounts: populated ? [account('bank', '생활 은행'), account('saving', '저축 통장', '0')] : [], transactions: [], totals: [], budgets: [], hasMore: false }
  const state = { data, unavailable: false, unauthorized: false, failWrite: false, requests: [] as { path: string; body: Record<string, unknown> }[] }
  await page.clock.install({ time: new Date(`${today}T03:00:00Z`) })
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', ''), method = request.method()
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/auth/session') return state.unauthorized ? reply({ message: '로그인이 필요합니다.' }, 401) : reply({ user: { id: 'money-user', email: 'money@example.test', displayName: '가계부 사용자', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: 'USER' }, onboardingCompleted: true })
    if (path === '/auth/csrf') return reply({ token: 'fixture-csrf', headerName: 'X-CSRF-TOKEN' })
    if (state.unauthorized) return reply({ message: '로그인이 필요합니다.' }, 401)
    if (state.unavailable) return reply({ message: '가계부 연결을 확인해 주세요.' }, 503)
    if (path === '/money' && method === 'GET') return reply({ ...data, month: url.searchParams.get('month') })
    const body = request.postData() ? request.postDataJSON() as Record<string, unknown> : {}
    state.requests.push({ path, body })
    if (state.failWrite) { state.failWrite = false; return reply({ message: '저장하지 못했습니다. 다시 시도해 주세요.' }, 503) }
    if (path === '/money/accounts') {
      const created = { id: `account-${data.accounts.length}`, name: String(body.name), type: String(body.type), currency: String(body.currency), openingBalance: String(body.openingBalance), balance: String(body.openingBalance) }
      data.accounts.push(created); return reply(created, 201)
    }
    if (path === '/money/transactions') {
      const created = { ...body, id: `transaction-${data.transactions.length}`, currency: 'KRW', status: 'POSTED', reversalOf: null } as unknown as MoneyTransaction
      data.transactions.unshift(created)
      if (created.type === 'EXPENSE') { data.accounts[0]!.balance = '95000'; data.totals = [{ currency: 'KRW', income: '0', expense: '5000', net: '-5000' }] }
      if (created.type === 'TRANSFER') { data.accounts[0]!.balance = '75000'; data.accounts[1]!.balance = '20000' }
      return reply(created, 201)
    }
    if (path === '/money/budgets') {
      const created = { id: 'budget-1', category: String(body.category), currency: String(body.currency), amount: String(body.amount), spent: '5000', remaining: '95000', version: body.version === undefined ? 0 : Number(body.version) + 1 }
      data.budgets = [created]; return reply(created)
    }
    if (path.endsWith('/reverse')) {
      const original = data.transactions.find(t => path.includes(t.id))!
      original.status = 'VOID'; data.accounts[0]!.balance = '80000'; data.totals = []; data.budgets.forEach(b => { b.spent = '0'; b.remaining = b.amount })
      data.transactions.unshift({ ...original, id: 'reversal-1', type: 'REVERSAL', status: 'POSTED', reversalOf: original.id })
      return reply(original)
    }
    return reply({ message: '요청을 찾을 수 없습니다.' }, 404)
  })
  return state
}
async function select(page: Page, label: string, option: string) {
  await page.getByLabel(label, { exact: true }).click()
  await page.getByRole('option', { name: option, exact: true }).click()
}
async function addAccount(page: Page, name: string, opening: string) {
  await page.getByRole('button', { name: '계좌 추가', exact: true }).click()
  await page.getByLabel('계좌 이름', { exact: true }).fill(name)
  await page.getByLabel('시작 잔액', { exact: true }).fill(opening)
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByLabel('계좌 이름', { exact: true })).toHaveCount(0)
  await expect(page.getByText(name, { exact: true })).toBeVisible()
}
async function expense(page: Page, amount = '5000') {
  await page.getByRole('button', { name: '거래 기록', exact: true }).click()
  await page.getByLabel('거래 금액', { exact: true }).fill(amount)
  await page.getByLabel('메모', { exact: true }).fill('검증 점심')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByLabel('거래 금액', { exact: true })).toHaveCount(0)
}

test('money starts empty and saves a real account payload without example balances', async ({ page }) => {
  const state = await mockMoney(page)
  await page.goto('/money')
  await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeDisabled()
  await expect(page.getByText('이번 달 확정 거래가 없어요.', { exact: true })).toBeVisible()
  await addAccount(page, '나의 은행', '12345.123456')
  expect(state.requests[0]?.body).toMatchObject({ name: '나의 은행', type: 'CHECKING', currency: 'KRW', openingBalance: '12345.123456' })
  await expect(page.getByText('12,345.123456 KRW', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeEnabled()
})

test('money records expense transfer budget and full cancellation with preserved audit', async ({ page }) => {
  const state = await mockMoney(page, true)
  await page.goto('/money')
  await expense(page)
  const expenseRequest = state.requests.find(r => r.path === '/money/transactions')!
  expect(expenseRequest.body).toMatchObject({ type: 'EXPENSE', accountId: 'bank', amount: '5000', category: '식비', transactionDate: today, memo: '검증 점심' })
  expect(expenseRequest.body.requestId).toMatch(/^[0-9a-f-]{36}$/)
  await expect(page.getByText('지출 · 식비', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '거래 기록', exact: true }).click()
  await select(page, '거래 종류', '이체·카드대금 납부')
  await select(page, '받는 계좌', '저축 통장 (KRW)')
  await page.getByLabel('거래 금액', { exact: true }).fill('20000')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText('이체 · 생활 은행 → 저축 통장', { exact: true })).toBeVisible()
  expect(state.requests.filter(r => r.path === '/money/transactions')[1]?.body).toMatchObject({ type: 'TRANSFER', accountId: 'bank', targetAccountId: 'saving', category: null, amount: '20000' })
  await page.getByRole('button', { name: '예산 추가', exact: true }).click()
  await page.getByLabel('예산 금액', { exact: true }).fill('100000')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText('잔여 95,000 KRW', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '식비 예산 수정', exact: true }).click()
  await page.getByLabel('예산 금액', { exact: true }).fill('200000')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByLabel('예산 금액', { exact: true })).toHaveCount(0)
  expect(state.requests.filter(r => r.path === '/money/budgets')[1]?.body.version).toBe(0)
  await page.getByRole('button', { name: `식비 ${today} 거래 취소`, exact: true }).click()
  await expect(page.getByRole('heading', { name: '거래 전체 취소', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '취소 확인', exact: true }).click()
  await expect(page.getByText('취소 분개 · 식비', { exact: true })).toBeVisible()
  await expect(page.getByText(/취소됨/)).toBeVisible()
  await expect(page.getByText('사용 0 KRW', { exact: true })).toBeVisible()
  expect(state.requests.some(r => r.path.endsWith('/reverse'))).toBe(true)
})

test('a failed money save preserves the draft and request ID for a safe retry', async ({ page }) => {
  const state = await mockMoney(page, true)
  await page.goto('/money')
  await page.getByRole('button', { name: '거래 기록', exact: true }).click()
  await page.getByLabel('거래 금액', { exact: true }).fill('5000')
  state.failWrite = true
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByLabel('거래 금액', { exact: true })).toHaveValue('5000')
  await expect(page.getByRole('alert').last()).toContainText('저장하지 못했습니다.')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByLabel('거래 금액', { exact: true })).toHaveCount(0)
  const writes = state.requests.filter(r => r.path === '/money/transactions')
  expect(writes).toHaveLength(2)
  expect(writes[0]?.body.requestId).toBe(writes[1]?.body.requestId)
  expect(state.data.transactions).toHaveLength(1)
})

test('money read retry and month selection fit a 360px screen', async ({ page }) => {
  const state = await mockMoney(page, true)
  state.unavailable = true
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/money')
  await expect(page.getByRole('alert')).toContainText('가계부 연결을 확인해 주세요.')
  state.unavailable = false
  await page.getByRole('button', { name: '다시 시도', exact: true }).click()
  await expect(page.getByText('생활 은행', { exact: true })).toBeVisible()
  await page.getByLabel('조회 월', { exact: true }).fill('2026-10')
  await expect(page.locator('.tag').filter({ hasText: '2026-10' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '계좌 추가', exact: true }).click()
  await expect(page.getByLabel('계좌 이름', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('expired money sessions remove private balances before login', async ({ page }) => {
  const state = await mockMoney(page, true)
  await page.goto('/money')
  await expect(page.getByText('생활 은행', { exact: true })).toBeVisible()
  state.unauthorized = true
  await page.getByLabel('조회 월', { exact: true }).fill('2026-10')
  await expect(page).toHaveURL(/\/login\?next=/)
  await expect(page.getByText('생활 은행', { exact: true })).toHaveCount(0)
})

test('real API money account expense transfer budget and cancellation persist', async ({ page }) => {
  test.skip(!process.env.E2E_USER_EMAIL || !process.env.E2E_USER_PASSWORD, 'Requires an isolated development user; never provisions or mutates cloud accounts')
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill(process.env.E2E_USER_EMAIL!)
  await page.getByLabel('비밀번호', { exact: true }).fill(process.env.E2E_USER_PASSWORD!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/(?:today|onboarding(?:\?.*)?)$/)
  if (new URL(page.url()).pathname === '/onboarding') await page.getByRole('button', { name: '오늘 시작하기', exact: true }).click()
  await page.goto('/money')
  const suffix = Date.now().toString(), first = `생활 검증 ${suffix}`, second = `저축 검증 ${suffix}`
  await addAccount(page, first, '100000')
  await addAccount(page, second, '0')
  await expense(page)
  await page.getByRole('button', { name: '거래 기록', exact: true }).click()
  await select(page, '거래 종류', '이체·카드대금 납부')
  await select(page, '받는 계좌', `${second} (KRW)`)
  await page.getByLabel('거래 금액', { exact: true }).fill('20000')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText(`이체 · ${first} → ${second}`, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '예산 추가', exact: true }).click()
  await page.getByLabel('예산 금액', { exact: true }).fill('100000')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText('잔여 95,000 KRW', { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByText('75,000 KRW', { exact: true })).toBeVisible()
  await expect(page.getByText('20,000 KRW', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: /식비 .* 거래 취소/ }).click()
  await page.getByRole('button', { name: '취소 확인', exact: true }).click()
  await expect(page.getByText('취소 분개 · 식비', { exact: true })).toBeVisible()
  await expect(page.getByText('잔여 100,000 KRW', { exact: true })).toBeVisible()
  await expect(page.getByText('80,000 KRW', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '로그아웃', exact: true }).click()
  await expect(page).toHaveURL(/\/welcome$/)
})
