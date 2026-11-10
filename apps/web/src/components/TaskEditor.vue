<script setup lang="ts">
import { ref } from 'vue'
import { api, ApiError } from '../api/client'
import { taskPriorities, taskStatuses, type Task } from '../api/planning'
import { localDateTime, wallTimeToInstant } from '../planningDates'

const props = defineProps<{ task: Task | null; date: string; timezone: string }>()
const emit = defineEmits<{ close: []; saved: []; conflict: []; unauthorized: [] }>()
const title = ref(''), status = ref<Task['status']>('TODO'), priority = ref<Task['priority']>('P2')
const dueDate = ref(''), dueTime = ref(''), timed = ref(false), minutes = ref<number | string | null>(null)
const version = ref(0), busy = ref(false), error = ref(''), conflict = ref(false)
function fill(task: Task | null) {
  title.value = task?.title || ''; status.value = task?.status || 'TODO'; priority.value = task?.priority || 'P2'
  dueDate.value = task ? task.dueDate || '' : props.date
  dueTime.value = task?.dueAt ? localDateTime(task.dueAt, props.timezone).slice(0, 16) : `${props.date}T09:00`
  timed.value = !!task?.dueAt; minutes.value = task?.estimatedMinutes ?? null; version.value = task?.version || 0
}
fill(props.task)
async function reload() {
  if (!props.task) return
  busy.value = true
  try { fill(await api<Task>(`/tasks/${props.task.id}`)); conflict.value = false; error.value = '' }
  catch (err) { if (err instanceof ApiError && err.status === 401) emit('unauthorized'); else error.value = err instanceof Error ? err.message : '최신 내용을 불러오지 못했습니다.' }
  finally { busy.value = false }
}
async function save() {
  busy.value = true; error.value = ''
  try {
    const estimatedMinutes = minutes.value === '' || minutes.value === null ? null : Number(minutes.value)
    if (estimatedMinutes !== null && (!Number.isInteger(estimatedMinutes) || estimatedMinutes < 1)) throw new Error('예상 시간은 1분 이상의 정수로 입력해 주세요.')
    await api(props.task ? `/tasks/${props.task.id}` : '/tasks', {
      method: props.task ? 'PATCH' : 'POST', body: JSON.stringify({ title: title.value.trim(), status: status.value,
        priority: priority.value, dueDate: timed.value ? null : dueDate.value || null,
        dueAt: timed.value ? wallTimeToInstant(dueTime.value, props.timezone) : null, estimatedMinutes,
        ...(props.task ? { version: version.value } : {}),
      }),
    })
    emit('saved')
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) { emit('unauthorized'); return }
    conflict.value = err instanceof ApiError && err.status === 409
    if (conflict.value) emit('conflict')
    error.value = conflict.value ? '다른 기기에서 변경되었습니다. 입력 내용은 보관했으며, 최신 내용을 불러온 뒤 다시 수정해 주세요.' : err instanceof Error ? err.message : '할 일을 저장하지 못했습니다.'
  } finally { busy.value = false }
}
</script>

<template>
  <q-dialog :model-value="true" :persistent="busy" aria-labelledby="task-editor-title" @hide="emit('close')">
    <div class="planning-dialog panel">
      <h2 id="task-editor-title">{{ task ? '할 일 수정' : '새 할 일' }}</h2>
      <form class="planning-form" @submit.prevent="save">
        <div v-if="error" class="error-message" role="alert">{{ error }}</div>
        <q-btn v-if="conflict" outline label="최신 내용 불러오기" :loading="busy" @click="reload" />
        <q-input v-model="title" outlined label="할 일 제목" required maxlength="200" autofocus :disable="busy" />
        <div class="planning-form-grid">
          <q-select v-model="status" outlined label="상태" :options="taskStatuses" emit-value map-options :disable="busy" />
          <q-select v-model="priority" outlined label="우선순위" :options="taskPriorities" :disable="busy" />
        </div>
        <q-checkbox v-model="timed" label="시간 지정 마감" :disable="busy" />
        <q-input v-if="timed" v-model="dueTime" outlined type="datetime-local" label="마감 시각" required :disable="busy" />
        <q-input v-else v-model="dueDate" outlined type="date" label="마감 날짜" :disable="busy" clearable />
        <small v-if="timed" class="field-hint">{{ timezone }} 기준. 서머타임으로 반복되는 시각은 먼저 발생하는 시각으로 저장합니다.</small>
        <q-input v-model="minutes" outlined type="number" min="1" step="1" label="예상 시간 (분)" :disable="busy" />
        <p class="field-hint">예상 시간과 완료 여부는 실제 활동 시간으로 합산하지 않습니다.</p>
        <div class="planning-actions"><q-btn label="닫기" flat :disable="busy" @click="emit('close')" /><q-btn type="submit" label="저장" color="primary" unelevated :loading="busy" :disable="conflict" /></div>
      </form>
    </div>
  </q-dialog>
</template>
