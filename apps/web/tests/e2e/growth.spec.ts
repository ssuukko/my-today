import { test, expect, type Page } from '@playwright/test'
import type { Activity, Goal, Occurrence, Reflection, Routine } from '../../src/api/life'

const today = '2026-11-09'
const activity = (title = '실제 공부', minutes = 40): Activity => ({ id: 'activity-1', title, category: '공부', activityDate: today, durationMinutes: minutes, quantity: null, unit: null, notes: null, version: 0 })

async function mockGrowth(page: Page) {
  const state = {
    activities: [] as Activity[], goals: [] as Goal[], routines: [] as Routine[], occurrences: [] as Occurrence[],
    reflections: {} as Record<string, Reflection>, unavailable: false, unauthorized: false, conflict: false, pageSize: 100,
  }
  await page.clock.install({ time: new Date(`${today}T03:00:00Z`) })
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', ''), method = request.method()
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/auth/csrf') return reply({ token: 'growth-fixture-csrf', headerName: 'X-CSRF-TOKEN' })
    if (state.unauthorized) return reply({ message: '로그인이 필요합니다.' }, 401)
    if (path === '/auth/session') return reply({ user: { id: 'growth-user', email: 'growth@example.test', displayName: '성장 사용자', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: 'USER' }, onboardingCompleted: true })
    if (state.unavailable) return reply({ message: '성장 기록에 연결할 수 없습니다.' }, 503)
    const goals = () => state.goals.map(goal => ({ ...goal, actualMinutes: state.activities.filter(item => item.category === goal.category && item.activityDate >= goal.startDate && item.activityDate <= goal.endDate).reduce((sum, item) => sum + item.durationMinutes, 0) }))
    if (path === '/growth' && method === 'GET') {
      const date = url.searchParams.get('date') || today, activities = state.activities.filter(item => item.activityDate === date)
      for (const routine of state.routines) {
        const weekday = new Date(`${date}T12:00:00Z`).getUTCDay() || 7
        if (routine.active && routine.startDate <= date && routine.weekdays.includes(weekday) && !state.occurrences.some(item => item.routineId === routine.id && item.scheduledDate === date))
          state.occurrences.push({ routineId: routine.id, title: routine.title, scheduledDate: date, targetMinutes: routine.targetMinutes, status: 'PENDING', version: 0 })
      }
      return reply({ date, timezone: 'Asia/Seoul', totalMinutes: activities.reduce((sum, item) => sum + item.durationMinutes, 0), activityCount: activities.length, activities: activities.slice(0, state.pageSize), nextActivityCursor: activities.length > state.pageSize ? String(state.pageSize) : null, routines: state.routines, occurrences: state.occurrences.filter(item => item.scheduledDate === date), goals: goals(), reflection: state.reflections[date] || null })
    }
    if (path === '/growth/activities' && method === 'GET') {
      const offset = Number(url.searchParams.get('cursor') || 0), date = url.searchParams.get('from'), rows = state.activities.filter(item => item.activityDate === date)
      return reply({ items: rows.slice(offset, offset + 100), nextCursor: rows.length > offset + 100 ? String(offset + 100) : null })
    }
    if (path === '/growth/activities' && method === 'POST') {
      const created = { ...request.postDataJSON(), id: `activity-${state.activities.length + 1}`, version: 0 } as Activity
      state.activities.push(created); return reply(created, 201)
    }
    if (path.startsWith('/growth/activities/')) {
      const index = state.activities.findIndex(item => item.id === path.split('/').at(-1)), current = state.activities[index]
      if (!current) return reply({ message: '활동을 찾을 수 없습니다.' }, 404)
      if (method === 'DELETE') {
        if (Number(url.searchParams.get('version')) !== current.version) return reply({ message: '최신 기록을 확인해 주세요.' }, 409)
        state.activities.splice(index, 1); return route.fulfill({ status: 204 })
      }
      const body = request.postDataJSON() as Activity
      if (state.conflict) {
        state.conflict = false; state.activities[index] = { ...current, title: '다른 기기의 활동', version: current.version + 1 }
        return reply({ message: '다른 기기에서 변경되었습니다. 새로 불러온 뒤 다시 시도해 주세요.' }, 409)
      }
      if (body.version !== current.version) return reply({ message: '최신 기록을 확인해 주세요.' }, 409)
      const updated = { ...current, ...body, version: current.version + 1 }
      state.activities[index] = updated; return reply(updated)
    }
    if (path === '/growth/goals' && method === 'POST') {
      const created = { ...request.postDataJSON(), id: `goal-${state.goals.length + 1}`, actualMinutes: 0, version: 0 } as Goal
      state.goals.push(created); return reply(created, 201)
    }
    if (path === '/growth/routines' && method === 'POST') {
      const created = { ...request.postDataJSON(), id: `routine-${state.routines.length + 1}`, version: 0 } as Routine
      state.routines.push(created); return reply(created, 201)
    }
    if (/\/growth\/routines\/[^/]+\/occurrences\//.test(path)) {
      const parts = path.split('/'), current = state.occurrences.find(item => item.routineId === parts[3] && item.scheduledDate === parts[5])
      if (!current) return reply({ message: '루틴을 찾을 수 없습니다.' }, 404)
      const body = request.postDataJSON() as Pick<Occurrence, 'status' | 'version'>
      if (current.version !== body.version) return reply({ message: '최신 루틴을 확인해 주세요.' }, 409)
      current.status = body.status; current.version++; return reply(current)
    }
    if (path.startsWith('/growth/reflections/') && method === 'PUT') {
      const date = path.split('/').at(-1)!, body = request.postDataJSON() as Reflection, previous = state.reflections[date]
      if (previous && body.version !== previous.version) return reply({ message: '최신 회고를 확인해 주세요.' }, 409)
      state.reflections[date] = { ...body, date, version: previous ? previous.version + 1 : 0 }
      return reply(state.reflections[date])
    }
    return reply({ message: `Unhandled growth API: ${method} ${path}` }, 404)
  })
  return state
}

test('growth activity edits and deletion recalculate actual-only goal progress', async ({ page }) => {
  const state = await mockGrowth(page)
  await page.goto('/growth')
  await page.getByRole('button', { name: '목표 추가', exact: true }).click()
  await page.getByLabel('제목', { exact: true }).fill('매일 한 시간 공부')
  await page.getByLabel('목표 시간 (분)', { exact: true }).fill('60')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText('0 / 60분', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '활동 기록', exact: true }).click()
  await page.getByLabel('제목', { exact: true }).fill('Java 실제 공부')
  await page.getByLabel('실제 시간 (분)', { exact: true }).fill('40')
  await page.getByLabel('수량 (선택)', { exact: true }).fill('12.5')
  await page.getByLabel('단위 (페이지·회 등)', { exact: true }).fill('페이지')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByText('40 / 60분', { exact: true })).toBeVisible()
  expect(state.activities[0]).toMatchObject({ durationMinutes: 40, quantity: '12.5', unit: '페이지', activityDate: today })
  await page.getByRole('button', { name: 'Java 실제 공부 활동 수정', exact: true }).click()
  await page.getByLabel('실제 시간 (분)', { exact: true }).fill('20')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await page.reload()
  await expect(page.getByText('20 / 60분', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Java 실제 공부 활동 삭제', exact: true }).click()
  expect(state.activities).toHaveLength(1)
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page.getByText('0 / 60분', { exact: true })).toBeVisible()
  expect(state.activities).toHaveLength(0)
})

test('routine checks do not create activity minutes and reflections persist after reload', async ({ page }) => {
  const state = await mockGrowth(page)
  await page.goto('/growth')
  await page.getByRole('button', { name: '루틴 추가', exact: true }).click()
  await page.getByLabel('제목', { exact: true }).fill('점심 산책')
  await page.getByLabel('목표 시간 (분)', { exact: true }).fill('20')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await page.getByRole('button', { name: '점심 산책 완료', exact: true }).click()
  await expect(page.getByRole('button', { name: '점심 산책 완료 취소', exact: true })).toBeVisible()
  await expect(page.locator('.summary-card').filter({ hasText: '실제 활동 시간' })).toContainText('0분')
  expect(state.activities).toHaveLength(0)
  expect(state.occurrences[0]?.status).toBe('DONE')
  await page.getByRole('button', { name: '건너뛰기', exact: true }).click()
  await expect(page.getByText('목표 20분 · 건너뜀', { exact: true })).toBeVisible()
  await page.getByLabel('잘한 일', { exact: true }).fill('실제 한 일을 기록했다')
  await page.getByLabel('놓친 일', { exact: true }).fill('오전 독서')
  await page.getByLabel('내일 바꿀 한 가지', { exact: true }).fill('책을 먼저 펼치기')
  await page.getByRole('button', { name: '회고 저장', exact: true }).click()
  await expect.poll(() => state.reflections[today]?.well).toBe('실제 한 일을 기록했다')
  await page.reload()
  await expect(page.getByLabel('잘한 일', { exact: true })).toHaveValue('실제 한 일을 기록했다')
  await page.getByLabel('잘한 일', { exact: true }).fill('수정한 회고')
  await page.getByRole('button', { name: '회고 저장', exact: true }).click()
  await expect.poll(() => state.reflections[today]?.version).toBe(1)
})

test('growth stale edits preserve the draft and permit reopening the latest record', async ({ page }) => {
  const state = await mockGrowth(page)
  state.activities.push(activity())
  await page.goto('/growth')
  await page.getByRole('button', { name: '실제 공부 활동 수정', exact: true }).click()
  await page.getByLabel('제목', { exact: true }).fill('보존할 초안')
  state.conflict = true
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByRole('alert').first()).toContainText('다른 기기에서 변경되었습니다.')
  await expect(page.getByLabel('제목', { exact: true })).toHaveValue('보존할 초안')
  await page.getByRole('button', { name: '닫기', exact: true }).click()
  await page.getByRole('button', { name: '다른 기기의 활동 활동 수정', exact: true }).click()
  await expect(page.getByLabel('제목', { exact: true })).toHaveValue('다른 기기의 활동')
  await page.getByLabel('제목', { exact: true }).fill('최신 내용 확인 후 수정')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByRole('button', { name: '최신 내용 확인 후 수정 활동 수정', exact: true })).toBeVisible()
  expect(state.activities[0]?.version).toBe(2)
})

test('growth read failure retries and all editors fit a 360px viewport', async ({ page }) => {
  const state = await mockGrowth(page)
  state.unavailable = true
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/growth')
  await expect(page.getByRole('alert')).toContainText('연결할 수 없습니다.')
  state.unavailable = false
  await page.getByRole('button', { name: '다시 시도', exact: true }).click()
  await expect(page.getByText('아직 기록이 없어요. 실제로 한 일을 남겨 보세요.', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  for (const name of ['활동 기록', '목표 추가', '루틴 추가']) {
    await page.getByRole('button', { name, exact: true }).click()
    await expect(page.getByLabel('제목', { exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: '닫기', exact: true }).click()
  }
})

test('growth activity pagination and date changes keep daily totals separate', async ({ page }) => {
  const state = await mockGrowth(page)
  state.pageSize = 1
  state.activities.push(activity('첫 활동', 40), { ...activity('다음 활동', 20), id: 'activity-2' })
  await page.goto('/growth')
  await expect(page.locator('.summary-card').filter({ hasText: '실제 활동 시간' })).toContainText('60분')
  await expect(page.getByRole('button', { name: '다음 활동 활동 수정', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '활동 더 보기', exact: true }).click()
  await expect(page.getByRole('button', { name: '다음 활동 활동 수정', exact: true })).toBeVisible()
  await page.getByLabel('조회 날짜', { exact: true }).fill('2026-11-10')
  await expect(page.locator('.summary-card').filter({ hasText: '실제 활동 시간' })).toContainText('0분')
  await expect(page.getByRole('button', { name: '첫 활동 활동 수정', exact: true })).toHaveCount(0)
})

test('expired growth sessions clear personal records before login', async ({ page }) => {
  const state = await mockGrowth(page)
  state.activities.push(activity('비공개 성장 기록'))
  await page.goto('/growth')
  await expect(page.getByText('비공개 성장 기록', { exact: true })).toBeVisible()
  state.unauthorized = true
  await page.getByLabel('조회 날짜', { exact: true }).fill('2026-11-10')
  await expect(page).toHaveURL(/\/login\?next=/)
  await expect(page.getByText('비공개 성장 기록', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('이메일', { exact: true })).toBeVisible()
})

test('real API growth activities recalculate goals and keep routine checks separate', async ({ page }) => {
  test.skip(process.env.E2E_ISOLATED_API !== '1' || !process.env.E2E_USER_EMAIL || !process.env.E2E_USER_PASSWORD, 'Requires explicit isolated API and a dedicated test user')
  const suffix = Date.now(), title = `실제 성장 검증 ${suffix}`, goal = `실제 시간 목표 ${suffix}`, routine = `실제 루틴 ${suffix}`
  await page.goto('/login')
  await page.getByLabel('이메일', { exact: true }).fill(process.env.E2E_USER_EMAIL!)
  await page.getByLabel('비밀번호', { exact: true }).fill(process.env.E2E_USER_PASSWORD!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/(?:onboarding(?:\?.*)?|today|admin)$/)
  if (new URL(page.url()).pathname === '/onboarding') await page.getByRole('button', { name: '오늘 시작하기', exact: true }).click()
  await page.goto('/growth')
  await page.getByRole('button', { name: '목표 추가', exact: true }).click()
  await page.getByLabel('제목', { exact: true }).fill(goal)
  await page.getByLabel('분야', { exact: true }).fill(`검증${suffix}`)
  await page.getByLabel('목표 시간 (분)', { exact: true }).fill('60')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.locator('article').filter({ has: page.getByText(goal, { exact: true }) })).toContainText('0 / 60분')
  await page.getByRole('button', { name: '활동 기록', exact: true }).click()
  await page.getByLabel('제목', { exact: true }).fill(title)
  await page.getByLabel('분야', { exact: true }).fill(`검증${suffix}`)
  await page.getByLabel('실제 시간 (분)', { exact: true }).fill('40')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await page.reload()
  await expect(page.locator('article').filter({ has: page.getByText(goal, { exact: true }) })).toContainText('40 / 60분')
  await page.getByRole('button', { name: `${title} 활동 수정`, exact: true }).click()
  await page.getByLabel('실제 시간 (분)', { exact: true }).fill('20')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.locator('article').filter({ has: page.getByText(goal, { exact: true }) })).toContainText('20 / 60분')
  await page.getByRole('button', { name: '루틴 추가', exact: true }).click()
  await page.getByLabel('제목', { exact: true }).fill(routine)
  for (const label of ['월', '화', '수', '목', '금', '토', '일']) await page.getByRole('checkbox', { name: label, exact: true }).check()
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await page.getByRole('button', { name: `${routine} 완료`, exact: true }).click()
  await expect(page.getByRole('button', { name: `${routine} 완료 취소`, exact: true })).toBeVisible()
  await expect(page.locator('article').filter({ has: page.getByText(goal, { exact: true }) })).toContainText('20 / 60분')
  await page.getByLabel('잘한 일', { exact: true }).fill(`성장 회고 ${suffix}`)
  await page.getByRole('button', { name: '회고 저장', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '저장했습니다.' })).toBeVisible()
  await page.reload()
  await expect(page.getByLabel('잘한 일', { exact: true })).toHaveValue(`성장 회고 ${suffix}`)
  await page.getByRole('button', { name: `${title} 활동 삭제`, exact: true }).click()
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page.locator('article').filter({ has: page.getByText(goal, { exact: true }) })).toContainText('0 / 60분')
  await page.getByRole('button', { name: `${goal} 목표 삭제`, exact: true }).click()
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page.getByText(goal, { exact: true })).toHaveCount(0)
  await page.locator('summary').filter({ hasText: '루틴 관리' }).click()
  await page.getByRole('button', { name: `${routine} 루틴 삭제`, exact: true }).click()
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page.getByRole('button', { name: `${routine} 루틴 삭제`, exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '로그아웃', exact: true }).click()
  await expect(page).toHaveURL(/\/welcome$/)
})
