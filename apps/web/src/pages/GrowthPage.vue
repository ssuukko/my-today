<script setup lang="ts">
import { ref, watch } from 'vue'
import { useSession } from '../stores/session'
import { usePersonalData } from '../composables/usePersonalData'
import { api } from '../api/client'
import { dateInZone } from '../planningDates'
import { decimal } from '../decimal'
import type { Activity, Goal, Growth, Routine } from '../api/life'

const session = useSession()
const today = dateInZone(new Date(), session.session!.user.timezone)
const date = ref(today)
const { data, loading, busy, error, notice, load, mutate } = usePersonalData<Growth>(() => `/growth?date=${date.value}`)
const editor = ref<'activity' | 'goal' | 'routine' | null>(null)
const selected = ref<Activity | Goal | Routine | null>(null)
const title = ref(''), category = ref('공부'), minutes = ref<number | string>(30)
const quantity = ref(''), unit = ref(''), notes = ref(''), activityDate = ref(today)
const startDate = ref(today), endDate = ref(today), weekdays = ref([1, 2, 3, 4, 5]), active = ref(true)
const reflection = ref({ well: '', missed: '', next: '' })
const deleting = ref<{ kind: string; id: string; version: number; title: string } | null>(null)
const dayOptions = ['월', '화', '수', '목', '금', '토', '일'].map((label, i) => ({ label, value: i + 1 }))
watch(date, () => { notice.value = ''; void load() }, { immediate: true })
watch(data, value => { reflection.value = { well: value?.reflection?.well || '', missed: value?.reflection?.missed || '', next: value?.reflection?.next || '' } })
function edit(kind: 'activity' | 'goal' | 'routine', item: Activity | Goal | Routine | null = null) {
  error.value = ''; editor.value = kind; selected.value = item; title.value = item?.title || ''
  category.value = item && 'category' in item ? item.category : '공부'
  minutes.value = item && 'durationMinutes' in item ? item.durationMinutes : item && 'targetMinutes' in item ? item.targetMinutes : 30
  activityDate.value = item && 'activityDate' in item ? item.activityDate : date.value
  quantity.value = item && 'quantity' in item ? item.quantity || '' : ''; unit.value = item && 'unit' in item ? item.unit || '' : ''
  notes.value = item && 'notes' in item ? item.notes || '' : ''
  startDate.value = item && 'startDate' in item ? item.startDate : date.value
  endDate.value = item && 'endDate' in item ? item.endDate : date.value
  weekdays.value = item && 'weekdays' in item ? [...item.weekdays] : [1, 2, 3, 4, 5]
  active.value = item && 'active' in item ? item.active : true
}
async function save() {
  const kind = editor.value
  const body = kind === 'activity'
    ? { title: title.value.trim(), category: category.value.trim(), activityDate: activityDate.value, durationMinutes: Number(minutes.value), quantity: quantity.value || null, unit: unit.value || null, notes: notes.value || null }
    : kind === 'goal'
      ? { title: title.value.trim(), category: category.value.trim(), startDate: startDate.value, endDate: endDate.value, targetMinutes: Number(minutes.value) }
      : { title: title.value.trim(), startDate: startDate.value, targetMinutes: Number(minutes.value), weekdays: weekdays.value, active: active.value }
  const collection = kind === 'activity' ? 'activities' : kind === 'goal' ? 'goals' : 'routines'
  if (await mutate(`/growth/${collection}${selected.value ? `/${selected.value.id}` : ''}`, selected.value ? 'PATCH' : 'POST', { ...body, ...(selected.value ? { version: selected.value.version } : {}) })) editor.value = null
}
async function more() {
  if (!data.value?.nextActivityCursor) return
  busy.value = true
  try {
    const result = await api<{ items: Activity[]; nextCursor: string | null }>(`/growth/activities?from=${date.value}&to=${date.value}&cursor=${data.value.nextActivityCursor}`)
    data.value = { ...data.value, activities: [...data.value.activities, ...result.items], nextActivityCursor: result.nextCursor }
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '기록을 불러오지 못했습니다.' }
  finally { busy.value = false }
}
async function remove() {
  if (deleting.value && await mutate(`/growth/${deleting.value.kind}/${deleting.value.id}?version=${deleting.value.version}`, 'DELETE')) deleting.value = null
}
async function saveReflection() {
  await mutate(`/growth/reflections/${date.value}`, 'PUT', { ...reflection.value, ...(data.value?.reflection ? { version: data.value.reflection.version } : {}) })
}
</script>
<template>
  <div class="page-heading planning-heading"><div><div class="eyebrow">MY GROWTH</div><h1>작은 실천이 쌓이는 곳</h1><p>완료한 계획과 실제 활동은 따로 기록해요.</p></div><q-btn label="활동 기록" color="primary" unelevated :disable="busy || loading" @click="edit('activity')" /></div>
  <div class="panel feature-toolbar"><q-input v-model="date" type="date" outlined label="조회 날짜" required :disable="busy" /><span>실제 활동만 목표에 합산합니다.</span></div>
  <div v-if="error" class="error-message" role="alert">{{ error }} <q-btn label="다시 시도" flat :disable="busy" @click="load" /></div>
  <p v-if="notice" role="status" class="success-message">{{ notice }}</p>
  <div v-if="loading" class="planning-loading" role="status"><q-spinner /> 성장 기록을 불러오고 있어요.</div>
  <template v-if="data">
    <div class="summary-grid"><div class="summary-card"><span class="summary-label">실제 활동 시간</span><strong>{{ data.totalMinutes }}분</strong><small>{{ data.date }} 기준</small></div><div class="summary-card"><span class="summary-label">활동 기록</span><strong>{{ data.activityCount }}건</strong><small>계획 시간은 포함하지 않아요</small></div></div>
    <div class="dashboard-grid feature-grid">
      <section class="panel planning-list"><div class="panel-heading"><h2>실제 활동</h2></div><p v-if="!data.activities.length" class="feature-empty">아직 기록이 없어요. 실제로 한 일을 남겨 보세요.</p><article v-for="item in data.activities" :key="item.id" class="planning-row"><div class="planning-row-content"><strong>{{ item.title }}</strong><small>{{ item.category }} · {{ item.durationMinutes }}분<span v-if="item.quantity"> · {{ decimal(item.quantity) }} {{ item.unit }}</span></small><small v-if="item.notes" class="planning-notes">{{ item.notes }}</small></div><div class="planning-row-actions"><q-btn flat label="수정" :aria-label="`${item.title} 활동 수정`" :disable="busy" @click="edit('activity', item)" /><q-btn flat label="삭제" :aria-label="`${item.title} 활동 삭제`" :disable="busy" @click="deleting = {kind:'activities', ...item}" /></div></article><q-btn v-if="data.nextActivityCursor" label="활동 더 보기" outline :loading="busy" @click="more" /></section>
      <section class="panel planning-list"><div class="panel-heading"><h2>오늘의 루틴</h2><q-btn flat label="루틴 추가" :disable="busy" @click="edit('routine')" /></div><p v-if="!data.occurrences.length" class="feature-empty">선택한 날짜에 예정된 루틴이 없어요.</p><article v-for="item in data.occurrences" :key="item.routineId" class="planning-row"><div class="planning-row-content"><strong>{{ item.title }}</strong><small>목표 {{ item.targetMinutes }}분 · {{ {PENDING:'대기', DONE:'완료', SKIPPED:'건너뜀'}[item.status] }}</small></div><div class="planning-row-actions"><q-btn flat :label="item.status === 'DONE' ? '완료 취소' : '완료'" :aria-label="`${item.title} ${item.status === 'DONE' ? '완료 취소' : '완료'}`" :disable="busy || date > today" @click="mutate(`/growth/routines/${item.routineId}/occurrences/${date}`, 'PUT', {status:item.status === 'DONE' ? 'PENDING' : 'DONE', version:item.version})" /><q-btn flat :label="item.status === 'SKIPPED' ? '건너뛰기 취소' : '건너뛰기'" :disable="busy || date > today" @click="mutate(`/growth/routines/${item.routineId}/occurrences/${date}`, 'PUT', {status:item.status === 'SKIPPED' ? 'PENDING' : 'SKIPPED',version:item.version})" /></div></article><details class="feature-details"><summary>루틴 관리 ({{ data.routines.length }})</summary><article v-for="item in data.routines" :key="item.id" class="planning-row"><div class="planning-row-content"><strong>{{ item.title }}</strong><small>{{ item.weekdays.map(n => dayOptions[n-1]!.label).join('·') }} · {{ item.targetMinutes }}분 · {{ item.active ? '활성' : '일시 중지' }}</small></div><div class="planning-row-actions"><q-btn flat label="수정" :aria-label="`${item.title} 루틴 수정`" :disable="busy" @click="edit('routine',item)"/><q-btn flat label="삭제" :aria-label="`${item.title} 루틴 삭제`" :disable="busy" @click="deleting = {kind:'routines', ...item}" /></div></article></details><p class="field-hint">루틴 완료는 실제 활동 시간을 자동으로 추가하지 않습니다.</p></section>
      <section class="panel planning-list"><div class="panel-heading"><h2>시간 목표</h2><q-btn flat label="목표 추가" :disable="busy" @click="edit('goal')" /></div><p v-if="!data.goals.length" class="feature-empty">분야와 기간을 정해 목표를 만들어 보세요.</p><article v-for="item in data.goals" :key="item.id" class="planning-row"><div class="planning-row-content"><strong>{{ item.title }}</strong><small>{{ item.category }} · {{ item.startDate }} ~ {{ item.endDate }}</small><span>{{ item.actualMinutes }} / {{ item.targetMinutes }}분</span><q-linear-progress :value="Math.min(1,item.actualMinutes/item.targetMinutes)" :aria-label="`${item.title} 진행률`" rounded /><small>같은 분야·기간의 실제 활동 시간 합계</small></div><div class="planning-row-actions"><q-btn label="수정" flat :aria-label="`${item.title} 목표 수정`" :disable="busy" @click="edit('goal',item)"/><q-btn label="삭제" flat :aria-label="`${item.title} 목표 삭제`" :disable="busy" @click="deleting = {kind:'goals', ...item}" /></div></article></section>
      <section class="panel"><h2>하루 회고</h2><form class="planning-form q-mt-md" @submit.prevent="saveReflection"><q-input v-model="reflection.well" outlined type="textarea" rows="2" maxlength="2000" label="잘한 일" :disable="busy"/><q-input v-model="reflection.missed" outlined type="textarea" rows="2" maxlength="2000" label="놓친 일" :disable="busy"/><q-input v-model="reflection.next" outlined type="textarea" rows="2" maxlength="2000" label="내일 바꿀 한 가지" :disable="busy"/><q-btn type="submit" label="회고 저장" outline color="primary" :loading="busy" /></form></section>
    </div>
  </template>
  <q-dialog :model-value="!!editor" :persistent="busy" @hide="editor = null"><div class="panel planning-dialog"><h2>{{ editor === 'activity' ? '실제 활동 기록' : editor === 'goal' ? '시간 목표' : '반복 루틴' }}</h2><form class="planning-form" @submit.prevent="save"><div v-if="error" role="alert" class="error-message">{{ error }}</div><q-input v-model="title" label="제목" outlined required maxlength="200" :disable="busy" autofocus/><q-input v-if="editor !== 'routine'" v-model="category" label="분야" outlined required maxlength="60" :disable="busy"/><q-input v-model="minutes" type="number" :min="editor === 'activity' ? 0 : 1" :max="editor === 'goal' ? 525600 : 1440" step="1" :label="editor === 'activity' ? '실제 시간 (분)' : '목표 시간 (분)'" outlined required :disable="busy"/>
    <template v-if="editor === 'activity'"><q-input v-model="activityDate" type="date" label="활동 날짜" outlined required :disable="busy"/><div class="planning-form-grid"><q-input v-model="quantity" label="수량 (선택)" inputmode="decimal" outlined :disable="busy"/><q-input v-model="unit" label="단위 (페이지·회 등)" maxlength="30" outlined :disable="busy"/></div><q-input v-model="notes" label="메모" type="textarea" maxlength="5000" outlined :disable="busy"/></template>
    <template v-else><q-input v-model="startDate" type="date" label="시작 날짜" outlined required :disable="busy || (!!selected && editor === 'routine')"/><q-input v-if="editor === 'goal'" v-model="endDate" type="date" label="종료 날짜" outlined required :disable="busy"/><template v-else><q-option-group v-model="weekdays" :options="dayOptions" type="checkbox" inline :disable="busy" aria-label="반복 요일"/><q-checkbox v-model="active" label="루틴 활성화" :disable="busy"/><p class="field-hint">수정은 오늘부터 적용되며 과거 목표 시간은 유지됩니다.</p></template></template>
    <div class="planning-actions"><q-btn flat label="닫기" :disable="busy" @click="editor = null"/><q-btn type="submit" color="primary" label="저장" :loading="busy"/></div></form></div></q-dialog>
  <q-dialog :model-value="!!deleting" :persistent="busy" @hide="deleting = null"><div class="panel planning-dialog"><h2>기록 삭제</h2><p>{{ deleting?.title }} 기록을 삭제할까요?</p><p v-if="error" role="alert" class="error-message">{{ error }}</p><div class="planning-actions"><q-btn flat label="닫기" :disable="busy" @click="deleting = null"/><q-btn label="삭제 확인" color="negative" :loading="busy" @click="remove"/></div></div></q-dialog>
</template>
