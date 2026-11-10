import { test, expect, type Page } from '@playwright/test'

interface Instrument {
  id: string; exchange: string; symbol: string; name: string; currency: string
  price: string | null; quotedAt: string | null; quoteType: 'MANUAL' | 'UNKNOWN'
  quantity: string; remainingCost: string; realizedPnl: string; marketValue: string | null; unrealizedPnl: string | null
}
interface Trade {
  id: string; instrumentId: string; side: string; quantity: string; price: string
  fees: string; tax: string; tradedAt: string; voided: boolean; realizedPnl: string
}
const instrument = (id = 'instrument-1'): Instrument => ({ id, exchange: 'NASDAQ', symbol: 'AAPL', name: '애플 테스트', currency: 'USD', price: null, quotedAt: null, quoteType: 'UNKNOWN', quantity: '0', remainingCost: '0', realizedPnl: '0', marketValue: '0', unrealizedPnl: '0' })

async function mockInvest(page: Page, initial: Instrument[] = []) {
  const state = { instruments: initial, trades: [] as Trade[], unavailable: false, unauthorized: false, rejected: false, tradePayloads: [] as Record<string, unknown>[] }
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', ''), method = request.method()
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/auth/session') return reply({ user: { id: 'investment-user', email: 'invest@example.test', displayName: '투자 사용자', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: 'USER' }, onboardingCompleted: true })
    if (path === '/auth/csrf') return reply({ token: 'invest-fixture-csrf', headerName: 'X-CSRF-TOKEN' })
    if (state.unauthorized) return reply({ message: '로그인이 필요합니다.' }, 401)
    if (state.unavailable) return reply({ message: '연결을 확인하고 다시 시도해 주세요.' }, 503)
    if (path === '/invest' && method === 'GET') return reply({ instruments: state.instruments, trades: [...state.trades].reverse(), totals: [] })
    if (path === '/invest/instruments' && method === 'POST') {
      const added = { ...instrument(`instrument-${state.instruments.length + 1}`), ...request.postDataJSON() } as Instrument
      state.instruments.push(added); return reply(added, 201)
    }
    if (/^\/invest\/instruments\/[^/]+\/quote$/.test(path) && method === 'PUT') {
      const item = state.instruments.find(i => i.id === path.split('/')[3])!
      const body = request.postDataJSON() as { price: string; quotedAt: string }
      Object.assign(item, body, { quoteType: 'MANUAL' }); return reply(item)
    }
    if (path === '/invest/trades' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>
      state.tradePayloads.push(body)
      if (state.rejected) return reply({ message: '보유 수량보다 많이 매도할 수 없습니다.' }, 409)
      const item = state.instruments.find(i => i.id === body.instrumentId)!
      const trade = { ...body, id: `trade-${state.trades.length + 1}`, voided: false, realizedPnl: '0' } as unknown as Trade
      state.trades.push(trade)
      item.quantity = trade.quantity; item.remainingCost = '101'; item.marketValue = null; item.unrealizedPnl = null
      return reply(trade, 201)
    }
    if (/^\/invest\/trades\/[^/]+\/void$/.test(path) && method === 'POST') {
      const trade = state.trades.find(t => t.id === path.split('/')[3])!
      trade.voided = true
      Object.assign(state.instruments.find(i => i.id === trade.instrumentId)!, { quantity: '0', remainingCost: '0', marketValue: '0', unrealizedPnl: '0' })
      return reply(trade)
    }
    return reply({ message: `Unhandled investment API: ${method} ${path}` }, 404)
  })
  return state
}

async function addInstrument(page: Page, symbol = 'TEST', name = '브라우저 테스트 종목') {
  await page.getByRole('button', { name: '종목 추가', exact: true }).click()
  await page.getByLabel('거래소', { exact: true }).fill('NASDAQ')
  await page.getByLabel('종목 코드', { exact: true }).fill(symbol)
  await page.getByLabel('종목 이름', { exact: true }).fill(name)
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
}

async function recordBuy(page: Page) {
  await page.getByRole('button', { name: '매매 기록', exact: true }).click()
  await page.getByLabel('수량', { exact: true }).fill('1')
  await page.getByLabel('단가', { exact: true }).fill('100')
  await page.getByLabel('수수료', { exact: true }).fill('1')
  await page.getByLabel('세금', { exact: true }).fill('0')
  await page.getByRole('button', { name: '저장', exact: true }).click()
}

test('investment watchlist additions persist and manual prices retain their timestamp', async ({ page }) => {
  const state = await mockInvest(page)
  await page.goto('/invest')
  await addInstrument(page)
  await expect.poll(() => state.instruments.length).toBe(1)
  await expect(page.getByText('브라우저 테스트 종목', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: / 수동 가격 입력$/ }).first().click()
  await page.getByLabel('수동 가격', { exact: true }).fill('125.125')
  await page.getByLabel('가격 기준 시각', { exact: true }).fill('2026-01-02T12:30')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect.poll(() => state.instruments[0]?.quoteType).toBe('MANUAL')
  expect(state.instruments[0]?.price).toBe('125.125')
  expect(state.instruments[0]?.quotedAt).toContain('2026-01-02')
  await page.reload()
  await expect(page.getByText(/수동 가격/).first()).toBeVisible()
  await expect(page.getByText('브라우저 테스트 종목', { exact: true }).first()).toBeVisible()
})

test('buy records send exact decimal strings and latest cancellation preserves history', async ({ page }) => {
  const state = await mockInvest(page, [instrument()])
  await page.goto('/invest')
  await recordBuy(page)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect.poll(() => state.trades.length).toBe(1)
  expect(state.tradePayloads[0]).toMatchObject({ instrumentId: 'instrument-1', side: 'BUY', quantity: '1', price: '100', fees: '1', tax: '0' })
  expect(state.tradePayloads[0]?.requestId).toMatch(/^[0-9a-f-]{36}$/i)
  await page.reload()
  await page.getByRole('button', { name: / 최근 매매 취소$/ }).first().click()
  await page.getByRole('button', { name: '취소 확인', exact: true }).click()
  await expect.poll(() => state.trades[0]?.voided).toBe(true)
  expect(state.trades).toHaveLength(1)
  await page.reload()
  await expect(page.getByText(/ · 취소됨$/).first()).toBeVisible()
})

test('investment rejected writes preserve drafts and keep the idempotency key for retry', async ({ page }) => {
  const state = await mockInvest(page, [instrument()])
  state.rejected = true
  await page.goto('/invest')
  await recordBuy(page)
  await expect(page.getByRole('alert').last()).toContainText('보유 수량보다 많이 매도할 수 없습니다.')
  await expect(page.getByLabel('수량', { exact: true })).toHaveValue('1')
  state.rejected = false
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(state.tradePayloads).toHaveLength(2)
  expect(state.tradePayloads[0]?.requestId).toBe(state.tradePayloads[1]?.requestId)
})

test('investment outage retries and editors remain usable at 360px', async ({ page }) => {
  const state = await mockInvest(page, [instrument()])
  state.unavailable = true
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/invest')
  await expect(page.getByRole('button', { name: '다시 시도', exact: true })).toBeVisible()
  state.unavailable = false
  await page.getByRole('button', { name: '다시 시도', exact: true }).click()
  await expect(page.getByText('애플 테스트', { exact: true }).first()).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '매매 기록', exact: true }).click()
  await expect(page.getByLabel('수량', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('real API investment records persist on an isolated development database', async ({ page }) => {
  test.skip(!process.env.E2E_USER_EMAIL || !process.env.E2E_USER_PASSWORD || process.env.E2E_ISOLATED_API !== '1', 'Requires an isolated development API and explicit test-user credentials')
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill(process.env.E2E_USER_EMAIL!)
  await page.getByLabel('비밀번호', { exact: true }).fill(process.env.E2E_USER_PASSWORD!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/(?:today|onboarding(?:\?.*)?)$/)
  if (new URL(page.url()).pathname === '/onboarding') await page.getByRole('button', { name: '오늘 시작하기', exact: true }).click()
  await page.goto('/invest')
  await addInstrument(page, `E2E${Date.now()}`, '실제 API 투자 검증')
  await recordBuy(page)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('실제 API 투자 검증', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: / 최근 매매 취소$/ }).first().click()
  await page.getByRole('button', { name: '취소 확인', exact: true }).click()
  await expect(page.getByText(/ · 취소됨$/).first()).toBeVisible()
  await page.getByRole('button', { name: '로그아웃', exact: true }).click()
  await expect(page).toHaveURL(/\/welcome$/)
})
