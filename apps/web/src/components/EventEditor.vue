<script setup lang="ts">
import { ref } from 'vue'
import { api, ApiError } from '../api/client'
import type { CalendarEvent } from '../api/planning'
import { addDays, localDateTime, wallTimeToInstant } from '../planningDates'

const props = defineProps<{ event: CalendarEvent | null; date: string; timezone: string }>()
const emit = defineEmits<{ close: []; saved: []; conflict: []; unauthorized: [] }>()
const title = ref(''), allDay = ref(true), startDate = ref(''), endDate = ref('')
const startTime = ref(''), endTime = ref(''), timezone = ref(props.timezone), location = ref(''), notes = ref('')
const version = ref(0), busy = ref(false), error = ref(''), conflict = ref(false)
function fill(event: CalendarEvent | null) {
  timezone.value = event?.timezone || props.timezone; title.value = event?.title || ''; allDay.value = event?.allDay ?? true
  startDate.value = event?.startDate || props.date
  endDate.value = event?.endDateExclusive ? addDays(event.endDateExclusive, -1) : props.date
  startTime.value = event?.startAt ? localDateTime(event.startAt, timezone.value).slice(0, 16) : `${props.date}T09:00`
  endTime.value = event?.endAt ? localDateTime(event.endAt, timezone.value).slice(0, 16) : `${props.date}T10:00`
  location.value = event?.location || ''; notes.value = event?.notes || ''; version.value = event?.version || 0
}
fill(props.event)
async function reload() {
  if (!props.event) return
  busy.value = true
  try { fill(await api<CalendarEvent>(`/calendar/events/${props.event.id}`)); conflict.value = false; error.value = '' }
  catch (err) { if (err instanceof ApiError && err.status === 401) emit('unauthorized'); else error.value = err instanceof Error ? err.message : '최신 내용을 불러오지 못했습니다.' }
  finally { busy.value = false }
}
async function save() {
  busy.value = true; error.value = ''
  try {
    new Intl.DateTimeFormat('ko-KR', { timeZone: timezone.value }).format()
    const startAt = allDay.value ? null : wallTimeToInstant(startTime.value, timezone.value)
    const endAt = allDay.value ? null : wallTimeToInstant(endTime.value, timezone.value)
    if (allDay.value ? endDate.value < startDate.value : endAt! <= startAt!) throw new Error('종료는 시작 이후여야 합니다. 종일 일정은 같은 날짜를 선택할 수 있습니다.')
    await api(props.event ? `/calendar/events/${props.event.id}` : '/calendar/events', {
      method: props.event ? 'PATCH' : 'POST', body: JSON.stringify({ title: title.value.trim(), allDay: allDay.value,
        startDate: allDay.value ? startDate.value : null, endDateExclusive: allDay.value ? addDays(endDate.value, 1) : null,
        startAt, endAt, timezone: timezone.value, location: location.value.trim() || null, notes: notes.value.trim() || null,
        ...(props.event ? { version: version.value } : {}),
      }),
    })
    emit('saved')
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) { emit('unauthorized'); return }
    conflict.value = err instanceof ApiError && err.status === 409
    if (conflict.value) emit('conflict')
    error.value = conflict.value ? '다른 기기에서 변경되었습니다. 입력 내용은 보관했으며, 최신 내용을 불러온 뒤 다시 수정해 주세요.' : err instanceof Error ? err.message : '일정을 저장하지 못했습니다.'
  } finally { busy.value = false }
}
</script>

<template>
  <q-dialog :model-value="true" :persistent="busy" aria-labelledby="event-editor-title" @hide="emit('close')">
    <div class="planning-dialog panel">
      <h2 id="event-editor-title">{{ event ? '일정 수정' : '새 일정' }}</h2>
      <form class="planning-form" @submit.prevent="save">
        <div v-if="error" class="error-message" role="alert">{{ error }}</div>
        <q-btn v-if="conflict" outline label="최신 내용 불러오기" :loading="busy" @click="reload" />
        <q-input v-model="title" outlined label="일정 제목" required maxlength="200" autofocus :disable="busy" />
        <q-checkbox v-model="allDay" label="종일 일정" :disable="busy" />
        <template v-if="allDay"><q-input v-model="startDate" outlined type="date" label="시작 날짜" required :disable="busy" /><q-input v-model="endDate" outlined type="date" label="종료 날짜 (포함)" required :min="startDate" :disable="busy" /></template>
        <template v-else><q-input v-model="startTime" outlined type="datetime-local" label="시작 시각" required :disable="busy" /><q-input v-model="endTime" outlined type="datetime-local" label="종료 시각" required :disable="busy" /></template>
        <q-input v-model="timezone" outlined label="일정 시간대" required maxlength="64" hint="예: Asia/Seoul, America/New_York" :disable="busy" />
        <small v-if="!allDay" class="field-hint">일정 시간대 기준. 서머타임으로 반복되는 시각은 먼저 발생하는 시각으로 저장합니다.</small>
        <q-input v-model="location" outlined label="장소" maxlength="300" :disable="busy" />
        <q-input v-model="notes" outlined type="textarea" label="메모" maxlength="2000" :disable="busy" />
        <div class="planning-actions"><q-btn label="닫기" flat :disable="busy" @click="emit('close')" /><q-btn type="submit" label="저장" color="primary" unelevated :loading="busy" :disable="conflict" /></div>
      </form>
    </div>
  </q-dialog>
</template>
