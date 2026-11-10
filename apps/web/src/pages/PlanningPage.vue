<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api/client'
import { taskPriorities, taskStatuses, type Task, type CalendarEvent, type Dashboard, type Page } from '../api/planning'
import { addDays, calendarRange, dateInZone } from '../planningDates'
import { useSession } from '../stores/session'
import TaskEditor from '../components/TaskEditor.vue'
import EventEditor from '../components/EventEditor.vue'

const route = useRoute(), router = useRouter(), session = useSession()
const timezone = computed(() => session.session?.user.timezone || 'Asia/Seoul')
const now = ref(new Date())
const clock = setInterval(() => { now.value = new Date() }, 60_000)
onUnmounted(() => clearInterval(clock))
const today = computed(() => dateInZone(now.value, timezone.value))
const mode = computed(() => route.path.slice(1))
const tasks = ref<Task[]>([]), events = ref<CalendarEvent[]>([]), dashboard = ref<Dashboard | null>(null)
const loading = ref(false), busy = ref(false), error = ref(''), notice = ref(''), nextCursor = ref<string | null>(null)
const selectedDate = ref(today.value), view = ref<'month' | 'week' | 'day'>('month')
const taskView = ref('all'), statusFilter = ref(''), priorityFilter = ref(''), dateFilter = ref('')
const taskOpen = ref(false), eventOpen = ref(false), selectedTask = ref<Task | null>(null), selectedEvent = ref<CalendarEvent | null>(null)
const deleting = ref<{ kind: 'task' | 'event'; item: Task | CalendarEvent } | null>(null)
let requestId = 0
const range = computed(() => calendarRange(selectedDate.value, view.value))
const days = computed(() => {
  const values: string[] = []
  for (let day = range.value.from; day < range.value.to; day = addDays(day, 1)) values.push(day)
  return values
})
watch(today, (value, old) => { if (selectedDate.value === old) selectedDate.value = value; if (mode.value === 'today') void load() })
function selectDate(value: string | number | null) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return
  const parsed = new Date(`${value}T12:00:00Z`)
  if (Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value) selectedDate.value = value
}
const dateLabel = computed(() => new Intl.DateTimeFormat('ko-KR', { dateStyle: 'full', timeZone: 'UTC' }).format(new Date(`${selectedDate.value}T12:00:00Z`)))
const taskViews = [{ label: '전체', value: 'all' }, { label: '오늘', value: 'today' }, { label: '예정', value: 'upcoming' }, { label: '밀린 일', value: 'overdue' }, { label: '완료', value: 'completed' }]
const dayEvents = computed(() => events.value.filter(event => occursOn(event, selectedDate.value)))
const orderedEvents = computed(() => [...(mode.value === 'today' ? dashboard.value?.events || [] : dayEvents.value)].sort((a, b) => Number(b.allDay) - Number(a.allDay) || (a.startAt || a.startDate || '').localeCompare(b.startAt || b.startDate || '')))
const todayTasks = computed(() => dashboard.value?.tasks || [])

function occursOn(event: CalendarEvent, day: string) {
  if (event.allDay) return event.startDate! <= day && event.endDateExclusive! > day
  const start = dateInZone(event.startAt!, timezone.value)
  const last = dateInZone(new Date(new Date(event.endAt!).getTime() - 1), timezone.value)
  return start <= day && last >= day
}
function eventTime(event: CalendarEvent) {
  if (event.allDay) return '종일'
  const format = new Intl.DateTimeFormat('ko-KR', { timeZone: timezone.value, month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
  return `${format.format(new Date(event.startAt!))} – ${format.format(new Date(event.endAt!))}`
}
function overlapping(event: CalendarEvent) {
  if (event.allDay) return false
  return events.value.some(other => other.id !== event.id && !other.allDay && Date.parse(other.startAt!) < Date.parse(event.endAt!) && Date.parse(other.endAt!) > Date.parse(event.startAt!))
}
function taskDue(task: Task) {
  if (task.dueAt) return new Intl.DateTimeFormat('ko-KR', { timeZone: timezone.value, dateStyle: 'medium', timeStyle: 'short' }).format(new Date(task.dueAt))
  return task.dueDate || '마감 없음'
}
async function handleError(err: unknown) {
  if (err instanceof ApiError && err.status === 401) {
    session.session = null; session.checked = false
    await router.replace({ path: '/login', query: { next: route.fullPath } }); return
  }
  error.value = err instanceof ApiError && err.status === 409 ? '다른 기기에서 변경되었습니다. 최신 목록을 확인하고 다시 시도해 주세요.' : err instanceof Error ? err.message : '연결을 확인하고 다시 시도해 주세요.'
}
async function load(append = false) {
  const id = ++requestId
  loading.value = true; error.value = ''
  try {
    if (mode.value === 'today') {
      const result = await api<Dashboard>(`/dashboard/today?date=${today.value}`)
      if (id === requestId) dashboard.value = result
    } else if (mode.value === 'tasks') {
      const query = new URLSearchParams({ size: '100', view: taskView.value })
      if (statusFilter.value) query.set('status', statusFilter.value)
      if (priorityFilter.value) query.set('priority', priorityFilter.value)
      if (dateFilter.value) query.set('date', dateFilter.value)
      if (append && nextCursor.value) query.set('cursor', nextCursor.value)
      const result = await api<Page<Task>>(`/tasks?${query}`)
      if (id === requestId) { tasks.value = append ? [...tasks.value, ...result.items] : result.items; nextCursor.value = result.nextCursor }
    } else {
      const query = new URLSearchParams({ from: range.value.from, to: range.value.to, size: '100' })
      if (append && nextCursor.value) query.set('cursor', nextCursor.value)
      const result = await api<Page<CalendarEvent>>(`/calendar/events?${query}`)
      if (id === requestId) { events.value = append ? [...events.value, ...result.items] : result.items; nextCursor.value = result.nextCursor }
    }
  } catch (err) { if (id === requestId) await handleError(err) }
  finally { if (id === requestId) loading.value = false }
}
watch([mode, range, taskView, statusFilter, priorityFilter, dateFilter, timezone], () => { nextCursor.value = null; tasks.value = []; events.value = []; dashboard.value = null; void load() }, { immediate: true })
function editTask(task: Task | null = null) { selectedTask.value = task; taskOpen.value = true }
function editEvent(event: CalendarEvent | null = null) { selectedEvent.value = event; eventOpen.value = true }
async function saved() {
  taskOpen.value = false; eventOpen.value = false; notice.value = '저장했습니다.'
  await load()
}
async function toggleTask(task: Task) {
  busy.value = true; notice.value = ''
  try {
    await api(`/tasks/${task.id}/${task.status === 'DONE' ? 'reopen' : 'complete'}`, { method: 'POST', body: JSON.stringify({ version: task.version }) })
    await load(); notice.value = task.status === 'DONE' ? '완료를 취소했습니다.' : '할 일을 완료했습니다.'
  } catch (err) { await handleError(err); if (err instanceof ApiError && err.status === 409) { const message = error.value; await load(); error.value = message } }
  finally { busy.value = false }
}
async function remove() {
  const target = deleting.value
  if (!target) return
  busy.value = true; error.value = ''
  try {
    await api(`${target.kind === 'task' ? '/tasks' : '/calendar/events'}/${target.item.id}?version=${target.item.version}`, { method: 'DELETE' })
    deleting.value = null; await load(); notice.value = '삭제했습니다.'
  } catch (err) { deleting.value = null; await handleError(err); if (err instanceof ApiError && err.status === 409) { const message = error.value; await load(); error.value = message } }
  finally { busy.value = false }
}
function navigate(direction: number) {
  if (view.value === 'month') {
    const value = new Date(`${selectedDate.value.slice(0, 7)}-01T12:00:00Z`)
    value.setUTCMonth(value.getUTCMonth() + direction); selectedDate.value = value.toISOString().slice(0, 10)
  } else selectedDate.value = addDays(selectedDate.value, direction * (view.value === 'week' ? 7 : 1))
}
</script>

<template>
  <div class="planning-page">
    <div class="page-heading planning-heading"><div><div class="eyebrow">{{ mode === 'today' ? dateLabel : 'MY DAILY PLAN' }}</div><h1 v-if="mode === 'today'">{{ session.session?.user.displayName }}님, 좋은 하루예요<span class="greeting-dot">.</span></h1><h1 v-else>{{ mode === 'tasks' ? '할 일' : '나의 일정' }}</h1><p>{{ mode === 'tasks' ? '지금 필요한 일부터, 하나씩 정리해 보세요.' : mode === 'calendar' ? `${timezone} 기준으로 일정을 확인합니다.` : '작은 시작 하나로, 오늘을 채워 보세요.' }}</p></div><div class="planning-actions"><q-btn v-if="mode !== 'calendar'" color="primary" unelevated label="할 일 추가" @click="editTask()" /><q-btn v-if="mode !== 'tasks'" color="primary" :outline="mode === 'today'" :unelevated="mode !== 'today'" label="일정 추가" @click="editEvent()" /></div></div>
    <div v-if="error" class="error-message" role="alert">{{ error }} <q-btn flat label="다시 시도" :loading="loading" @click="load()" /></div>
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    <div v-if="mode === 'tasks'" class="panel planning-filters"><q-select v-model="taskView" label="보기" outlined :options="taskViews" emit-value map-options /><q-select v-model="statusFilter" label="상태 필터" outlined :options="[{ label: '모든 상태', value: '' }, ...taskStatuses]" emit-value map-options /><q-select v-model="priorityFilter" label="우선순위 필터" outlined :options="[{ label: '모든 우선순위', value: '' }, ...taskPriorities.map(value => ({ label: value, value }))]" emit-value map-options /><q-input v-model="dateFilter" label="날짜 필터" type="date" outlined clearable /></div>
    <template v-if="mode === 'calendar'"><div class="panel calendar-toolbar"><div class="planning-actions"><q-btn flat label="이전" @click="navigate(-1)" /><q-btn flat label="오늘" @click="selectedDate = today" /><q-btn flat label="다음" @click="navigate(1)" /></div><strong>{{ dateLabel }}</strong><div class="planning-actions"><q-btn v-for="item in [{ value: 'month', label: '월' }, { value: 'week', label: '주' }, { value: 'day', label: '일' }] as const" :key="item.value" :label="item.label" :color="view === item.value ? 'primary' : undefined" :flat="view !== item.value" :aria-pressed="view === item.value" @click="view = item.value" /></div><q-input :model-value="selectedDate" @update:model-value="selectDate" label="조회 날짜" type="date" outlined /></div></template>
    <div v-if="loading" class="planning-loading" role="status"><q-spinner color="primary" size="24px" /> 불러오는 중입니다.</div>
    <template v-if="mode === 'today' && dashboard && !loading">
      <div class="summary-grid"><div class="summary-card"><span class="summary-label">오늘의 일정 <span aria-hidden="true">▦</span></span><strong>{{ dashboard.eventCount }}개</strong><small>등록된 일정</small></div><div class="summary-card"><span class="summary-label">오늘의 할 일 <span aria-hidden="true">☑</span></span><strong>{{ dashboard.completedTaskCount }} / {{ dashboard.taskCount }}</strong><small>오늘 마감 · 완료한 일</small></div><div class="summary-card"><span class="summary-label">오늘의 지출 <span aria-hidden="true">₩</span></span><strong>—</strong><small>가계부 연결 전</small></div><div class="summary-card"><span class="summary-label">나의 성장 <span aria-hidden="true">↗</span></span><strong>—</strong><small>실제 활동 기록 준비 중</small></div></div>
    </template>
    <div v-if="mode === 'calendar' && !loading" class="calendar-grid" :class="{ 'calendar-single': view === 'day' }" :aria-label="`${view === 'month' ? '월' : view === 'week' ? '주' : '일'} 달력`"><button v-for="day in days" :key="day" type="button" class="calendar-cell" :class="{ selected: day === selectedDate, 'calendar-today': day === today, muted: view === 'month' && day.slice(0, 7) !== selectedDate.slice(0, 7) }" :aria-label="`${day} 일정 보기`" :aria-pressed="day === selectedDate" @click="selectedDate = day"><span>{{ Number(day.slice(8)) }}<small>{{ ['일', '월', '화', '수', '목', '금', '토'][new Date(`${day}T12:00:00Z`).getUTCDay()] }}</small></span><span v-for="event in events.filter(item => occursOn(item, day)).slice(0, 2)" :key="event.id" class="calendar-event"><span v-if="event.allDay" aria-hidden="true">종일 </span><span>{{ event.title }}</span></span><small v-if="events.filter(item => occursOn(item, day)).length > 2">+{{ events.filter(item => occursOn(item, day)).length - 2 }}개</small></button></div>
    <div :class="{ 'dashboard-grid': mode === 'today' }" v-if="!loading && (mode !== 'today' || dashboard)">
      <section v-if="mode !== 'tasks'" class="panel planning-list"><div class="panel-heading"><h2>{{ mode === 'today' ? '오늘의 타임라인' : `${selectedDate} 일정` }}</h2><RouterLink v-if="mode === 'today'" to="/calendar">전체 일정</RouterLink></div><p v-if="!orderedEvents.length" class="empty-state">등록된 일정이 없습니다. 새 일정을 추가해 보세요.</p><article v-for="event in orderedEvents" :key="event.id" class="planning-row"><div class="planning-row-content"><strong>{{ event.title }}</strong><small>{{ eventTime(event) }}</small><small v-if="event.timezone !== timezone">원래 시간대: {{ event.timezone }}</small><small v-if="event.location">{{ event.location }}</small><small v-if="event.notes" class="planning-notes">{{ event.notes }}</small><span v-if="mode === 'calendar' && overlapping(event)" class="overlap-label">시간 겹침</span></div><div class="planning-row-actions"><q-btn flat label="수정" :aria-label="`${event.title} 수정`" :disable="busy" @click="editEvent(event)" /><q-btn flat label="삭제" :aria-label="`${event.title} 삭제`" :disable="busy" @click="deleting = { kind: 'event', item: event }" /></div></article><p v-if="mode === 'today' && dashboard && dashboard.eventCount > dashboard.events.length" class="field-hint">일부 일정만 표시합니다. 전체 일정에서 확인하세요.</p></section>
      <section v-if="mode !== 'calendar'" class="panel planning-list"><div class="panel-heading"><h2>{{ mode === 'today' ? '집중할 일' : '할 일 목록' }}</h2><RouterLink v-if="mode === 'today'" to="/tasks">전체 할 일</RouterLink></div><p v-if="!(mode === 'today' ? todayTasks : tasks).length" class="empty-state">등록된 할 일이 없습니다. 작은 일 하나부터 시작해 보세요.</p><article v-for="task in mode === 'today' ? todayTasks : tasks" :key="task.id" class="planning-row" :class="{ 'task-completed': task.status === 'DONE' }"><div class="planning-row-content"><strong>{{ task.title }}</strong><small>{{ task.priority }} · {{ taskStatuses.find(item => item.value === task.status)?.label }} · {{ taskDue(task) }}</small><small v-if="task.estimatedMinutes">예상 {{ task.estimatedMinutes }}분 · 실제 활동과 별도</small></div><div class="planning-row-actions"><q-btn v-if="task.status !== 'CANCELED'" flat :label="task.status === 'DONE' ? '완료 취소' : '완료'" :aria-label="`${task.title} ${task.status === 'DONE' ? '완료 취소' : '완료'}`" :disable="busy" color="primary" @click="toggleTask(task)" /><q-btn flat label="수정" :aria-label="`${task.title} 수정`" :disable="busy" @click="editTask(task)" /><q-btn flat label="삭제" :aria-label="`${task.title} 삭제`" :disable="busy" @click="deleting = { kind: 'task', item: task }" /></div></article><p v-if="mode === 'today' && dashboard && dashboard.taskCount > dashboard.tasks.length" class="field-hint">일부 할 일만 표시합니다. 전체 할 일에서 확인하세요.</p></section>
    </div>
    <div v-if="nextCursor && mode !== 'today'" class="planning-load-more"><p>아직 불러오지 않은 기록이 있습니다. 더 불러와 전체 기록을 확인하세요.</p><q-btn label="더 불러오기" outline color="primary" :loading="loading" @click="load(true)" /></div>
    <TaskEditor v-if="taskOpen" :task="selectedTask" :date="mode === 'today' ? today : dateFilter || selectedDate" :timezone="timezone" @close="taskOpen = false" @saved="saved" @conflict="load()" @unauthorized="handleError(new ApiError(401, '로그인이 필요합니다.'))" />
    <EventEditor v-if="eventOpen" :event="selectedEvent" :date="mode === 'today' ? today : selectedDate" :timezone="timezone" @close="eventOpen = false" @saved="saved" @conflict="load()" @unauthorized="handleError(new ApiError(401, '로그인이 필요합니다.'))" />
    <q-dialog :model-value="!!deleting" :persistent="busy" aria-labelledby="delete-title" @hide="deleting = null"><div class="planning-dialog panel"><h2 id="delete-title">삭제 확인</h2><p>‘{{ deleting?.item.title }}’ 기록을 삭제할까요?</p><div class="planning-actions"><q-btn flat label="닫기" :disable="busy" @click="deleting = null" /><q-btn color="negative" unelevated label="삭제 확인" :loading="busy" @click="remove" /></div></div></q-dialog>
  </div>
</template>
