import { test, expect, type Page } from '@playwright/test'

interface Task {
  id: string; title: string; status: string; priority: string
  dueDate: string | null; dueAt: string | null; estimatedMinutes: number | null
  completedAt: string | null; version: number
}
interface CalendarEvent {
  id: string; title: string; allDay: boolean; startDate: string | null; endDateExclusive: string | null
  startAt: string | null; endAt: string | null; timezone: string; location: string | null; notes: string | null; version: number
}
const today = '2026-11-07'
const task = (title: string): Task => ({ id: 'task-1', title, status: 'TODO', priority: 'P1', dueDate: today, dueAt: null, estimatedMinutes: null, completedAt: null, version: 0 })

async function mockPlanning(page: Page, initialTasks: Task[] = []) {
  const state = { tasks: initialTasks, events: [] as CalendarEvent[], conflict: false, unavailable: false }
  await page.clock.install({ time: new Date(`${today}T03:00:00Z`) })
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '')
    const method = request.method()
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/auth/session') return reply({ user: { id: 'user-1', email: 'test@example.test', displayName: '테스트 사용자', timezone: 'Asia/Seoul', baseCurrency: 'KRW', role: 'USER' }, onboardingCompleted: true })
    if (path === '/auth/csrf') return reply({ token: 'test-csrf', headerName: 'X-CSRF-TOKEN' })
    if (state.unavailable) return reply({ message: '연결을 확인하고 다시 시도해 주세요.' }, 503)
    if (path === '/dashboard/today') return reply({ date: today, timezone: 'Asia/Seoul', events: state.events.filter(e => e.startDate === today), tasks: state.tasks.filter(t => t.dueDate === today), eventCount: state.events.filter(e => e.startDate === today).length, taskCount: state.tasks.filter(t => t.dueDate === today).length, completedTaskCount: state.tasks.filter(t => t.dueDate === today && t.status === 'DONE').length })
    if (path === '/tasks' && method === 'GET') {
      const date = url.searchParams.get('date'), status = url.searchParams.get('status')
      return reply({ items: state.tasks.filter(t => (!date || t.dueDate === date) && (!status || t.status === status)), nextCursor: null })
    }
    if (path === '/tasks' && method === 'POST') {
      const created = { ...task(''), ...request.postDataJSON(), id: `task-${state.tasks.length + 1}` } as Task
      state.tasks.push(created)
      return reply(created, 201)
    }
    if (path.startsWith('/tasks/')) {
      const [, , id, action] = path.split('/'), index = state.tasks.findIndex(t => t.id === id), current = state.tasks[index]
      if (!current) return reply({ message: '할 일을 찾을 수 없습니다.' }, 404)
      if (method === 'GET') return reply(current)
      if (method === 'DELETE') { state.tasks.splice(index, 1); return route.fulfill({ status: 204 }) }
      const body = request.postDataJSON() as Partial<Task>
      if (state.conflict) {
        state.conflict = false
        state.tasks[index] = { ...current, title: '다른 기기에서 바꾼 제목', version: current.version + 1 }
        return reply({ message: '다른 기기에서 변경되었습니다. 최신 내용을 확인해 주세요.' }, 409)
      }
      if (body.version !== current.version) return reply({ message: '최신 내용을 확인해 주세요.' }, 409)
      const updated = action ? { ...current, status: action === 'complete' ? 'DONE' : 'TODO', completedAt: action === 'complete' ? `${today}T03:00:00Z` : null, version: current.version + 1 } : { ...current, ...body, version: current.version + 1 }
      state.tasks[index] = updated
      return reply(updated)
    }
    if (path === '/calendar/events' && method === 'GET') {
      const from = url.searchParams.get('from')!, to = url.searchParams.get('to')!
      return reply({ items: state.events.filter(e => e.allDay ? e.startDate! < to && e.endDateExclusive! > from : e.startAt!.slice(0, 10) < to && e.endAt!.slice(0, 10) >= from), nextCursor: null })
    }
    if (path === '/calendar/events' && method === 'POST') {
      const created = { ...request.postDataJSON(), id: `event-${state.events.length + 1}`, version: 0 } as CalendarEvent
      state.events.push(created)
      return reply(created, 201)
    }
    if (path.startsWith('/calendar/events/')) {
      const id = path.split('/').at(-1), index = state.events.findIndex(e => e.id === id)
      if (index < 0) return reply({ message: '일정을 찾을 수 없습니다.' }, 404)
      if (method === 'GET') return reply(state.events[index])
      if (method === 'DELETE') { state.events.splice(index, 1); return route.fulfill({ status: 204 }) }
      const updated = { ...state.events[index], ...request.postDataJSON(), version: state.events[index]!.version + 1 } as CalendarEvent
      state.events[index] = updated
      return reply(updated)
    }
    return reply({ message: `Unhandled API: ${method} ${path}` }, 404)
  })
  return state
}

test('today quick entry persists a task and completion can be undone', async ({ page }) => {
  const state = await mockPlanning(page)
  await page.goto('/today')
  await page.getByRole('button', { name: '할 일 추가', exact: true }).click()
  await page.getByLabel('할 일 제목', { exact: true }).fill('책 읽기')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByRole('button', { name: '책 읽기 완료', exact: true })).toBeVisible()
  expect(state.tasks[0]?.dueDate).toBe(today)
  await page.getByRole('button', { name: '책 읽기 완료', exact: true }).click()
  await expect(page.getByRole('button', { name: '책 읽기 완료 취소', exact: true })).toBeVisible()
  expect(state.tasks[0]?.status).toBe('DONE')
  await page.getByRole('button', { name: '책 읽기 완료 취소', exact: true }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: '책 읽기 완료', exact: true })).toBeVisible()
  expect(state.tasks[0]?.status).toBe('TODO')
})

test('task edits survive reload and deletion requires confirmation', async ({ page }) => {
  const state = await mockPlanning(page, [task('원래 제목')])
  await page.goto('/tasks')
  await page.getByRole('button', { name: '원래 제목 수정', exact: true }).click()
  await page.getByLabel('할 일 제목', { exact: true }).fill('수정한 제목')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: '수정한 제목 수정', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '수정한 제목 삭제', exact: true }).click()
  expect(state.tasks).toHaveLength(1)
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page.getByRole('button', { name: '수정한 제목 수정', exact: true })).toHaveCount(0)
  expect(state.tasks).toHaveLength(0)
})

test('a stale edit preserves the draft until latest contents are explicitly loaded', async ({ page }) => {
  const state = await mockPlanning(page, [task('첫 제목')])
  await page.goto('/tasks')
  await page.getByRole('button', { name: '첫 제목 수정', exact: true }).click()
  await page.getByLabel('할 일 제목', { exact: true }).fill('내가 쓰던 초안')
  state.conflict = true
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByLabel('할 일 제목', { exact: true })).toHaveValue('내가 쓰던 초안')
  await expect(page.getByRole('button', { name: '최신 내용 불러오기', exact: true })).toBeVisible()
  expect(state.tasks[0]?.title).toBe('다른 기기에서 바꾼 제목')
  await page.getByRole('button', { name: '최신 내용 불러오기', exact: true }).click()
  await expect(page.getByLabel('할 일 제목', { exact: true })).toHaveValue('다른 기기에서 바꾼 제목')
  await page.getByLabel('할 일 제목', { exact: true }).fill('충돌 확인 후 수정')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByRole('button', { name: '충돌 확인 후 수정 수정', exact: true })).toBeVisible()
  expect(state.tasks[0]?.version).toBe(2)
})

test('editing an undated task preserves its missing deadline', async ({ page }) => {
  const state = await mockPlanning(page, [{ ...task('날짜 없는 할 일'), dueDate: null }])
  await page.goto('/tasks')
  await page.getByRole('button', { name: '날짜 없는 할 일 수정', exact: true }).click()
  await expect(page.getByLabel('마감 날짜', { exact: true })).toHaveValue('')
  await page.getByLabel('할 일 제목', { exact: true }).fill('제목만 수정')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page.getByRole('button', { name: '제목만 수정 수정', exact: true })).toBeVisible()
  expect(state.tasks[0]).toMatchObject({ dueDate: null, dueAt: null })
})

test('clearing the calendar date does not break the current view', async ({ page }) => {
  await mockPlanning(page)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/calendar')
  await page.getByLabel('조회 날짜', { exact: true }).fill('')
  await expect(page.getByRole('button', { name: `${today} 일정 보기`, exact: true })).toBeVisible()
  await page.getByLabel('조회 날짜', { exact: true }).fill('2026-11-08')
  await expect(page.getByRole('button', { name: '2026-11-08 일정 보기', exact: true })).toHaveAttribute('aria-pressed', 'true')
  expect(errors).toEqual([])
})

test('a tomorrow all-day event uses an exclusive end and appears in month, week and day views', async ({ page }) => {
  const state = await mockPlanning(page)
  await page.goto('/calendar')
  await page.getByRole('button', { name: '일정 추가', exact: true }).click()
  await page.getByLabel('일정 제목', { exact: true }).fill('내일의 약속')
  await page.getByLabel('시작 날짜', { exact: true }).fill('2026-11-08')
  await page.getByLabel('종료 날짜 (포함)', { exact: true }).fill('2026-11-08')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect.poll(() => state.events.length).toBe(1)
  expect(state.events[0]).toMatchObject({ allDay: true, startDate: '2026-11-08', endDateExclusive: '2026-11-09', timezone: 'Asia/Seoul' })
  await expect(page.getByText('내일의 약속', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '주', exact: true }).click()
  await expect(page.getByText('내일의 약속', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '일', exact: true }).click()
  await page.getByRole('button', { name: '다음', exact: true }).click()
  await expect(page.getByText('내일의 약속', { exact: true }).first()).toBeVisible()
  await page.reload()
  await page.getByRole('button', { name: '월', exact: true }).click()
  await expect(page.getByText('내일의 약속', { exact: true }).first()).toBeVisible()
})

test('planning screens and editors fit a 360px viewport', async ({ page }) => {
  await mockPlanning(page, [task('작은 화면에서도 사용할 수 있는 긴 할 일 제목')])
  await page.setViewportSize({ width: 360, height: 800 })
  for (const path of ['/today', '/tasks', '/calendar']) {
    await page.goto(path)
    await expect(page.getByRole('button', { name: path === '/calendar' ? '일정 추가' : '할 일 추가', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
  await page.getByRole('button', { name: '일정 추가', exact: true }).click()
  await expect(page.getByLabel('일정 제목', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('calendar changes persist and an event is removed only after confirmation', async ({ page }) => {
  const state = await mockPlanning(page)
  state.events.push({ id: 'event-1', title: '변경 전 일정', allDay: true, startDate: today, endDateExclusive: '2026-11-08', startAt: null, endAt: null, timezone: 'Asia/Seoul', location: null, notes: null, version: 0 })
  await page.goto('/calendar')
  await page.getByRole('button', { name: '변경 전 일정 수정', exact: true }).click()
  await page.getByLabel('일정 제목', { exact: true }).fill('변경 후 일정')
  await page.getByLabel('장소', { exact: true }).fill('도서관')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect.poll(() => state.events[0]?.version).toBe(1)
  expect(state.events[0]?.location).toBe('도서관')
  await page.reload()
  await page.getByRole('button', { name: '변경 후 일정 삭제', exact: true }).click()
  expect(state.events).toHaveLength(1)
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page.getByText('변경 후 일정', { exact: true })).toHaveCount(0)
  expect(state.events).toHaveLength(0)
})

test('a failed task read offers retry and recovers without losing the session', async ({ page }) => {
  const state = await mockPlanning(page, [task('복구한 할 일')])
  state.unavailable = true
  await page.goto('/tasks')
  await expect(page.getByRole('button', { name: '다시 시도', exact: true })).toBeVisible()
  state.unavailable = false
  await page.getByRole('button', { name: '다시 시도', exact: true }).click()
  await expect(page.getByRole('button', { name: '복구한 할 일 수정', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/tasks$/)
})

test('real API saves today tasks and tomorrow events after signup', async ({ page }) => {
  test.skip(!process.env.E2E_INVITE_TOKEN, 'Requires a development API and an explicit invitation')
  const email = `planning-${Date.now()}@example.test`
  await page.goto('/signup')
  await page.getByLabel('표시 이름', { exact: true }).fill('계획 테스트')
  await page.getByLabel('이메일', { exact: true }).fill(email)
  await page.getByLabel('비밀번호', { exact: true }).fill('planning-test-password')
  await page.getByLabel('비밀번호 확인', { exact: true }).fill('planning-test-password')
  await page.getByLabel('초대 코드', { exact: true }).fill(process.env.E2E_INVITE_TOKEN!)
  await page.getByRole('button', { name: '계정 만들기', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('이메일', { exact: true }).fill(email)
  await page.getByLabel('비밀번호', { exact: true }).fill('planning-test-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/onboarding(?:\?.*)?$/)
  await page.getByRole('button', { name: '오늘 시작하기', exact: true }).click()
  await expect(page).toHaveURL(/\/today$/)
  await page.getByRole('button', { name: '할 일 추가', exact: true }).click()
  await page.getByLabel('할 일 제목', { exact: true }).fill('오늘 계획 검증')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await page.getByRole('button', { name: '오늘 계획 검증 완료', exact: true }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: '오늘 계획 검증 완료 취소', exact: true })).toBeVisible()

  const localToday = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
  const tomorrow = new Date(`${localToday}T12:00:00Z`)
  tomorrow.setUTCDate(tomorrow.getUTCDate() + 1)
  const tomorrowDate = tomorrow.toISOString().slice(0, 10)
  await page.goto('/calendar')
  await page.getByRole('button', { name: '일정 추가', exact: true }).click()
  await page.getByLabel('일정 제목', { exact: true }).fill('내일 일정 검증')
  await page.getByLabel('시작 날짜', { exact: true }).fill(tomorrowDate)
  await page.getByLabel('종료 날짜 (포함)', { exact: true }).fill(tomorrowDate)
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await page.reload()
  if (tomorrowDate.slice(0, 7) !== localToday.slice(0, 7)) await page.getByRole('button', { name: '다음', exact: true }).click()
  await expect(page.getByText('내일 일정 검증', { exact: true }).first()).toBeVisible()
})
