import { onUnmounted, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api/client'
import { useSession } from '../stores/session'

export function usePersonalData<T>(path: () => string) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false), busy = ref(false), error = ref(''), notice = ref('')
  const router = useRouter(), route = useRoute(), session = useSession()
  let request = 0, active = true
  onUnmounted(() => { active = false; request++ })
  async function fail(cause: unknown) {
    if (!active) return
    if (cause instanceof ApiError && cause.status === 401) {
      data.value = null; session.session = null; session.checked = false
      await router.replace({ path: '/login', query: { next: route.fullPath } }); return
    }
    error.value = cause instanceof Error ? cause.message : '연결을 확인하고 다시 시도해 주세요.'
  }
  async function load() {
    const id = ++request
    loading.value = true; error.value = ''; data.value = null
    try { const result = await api<T>(path()); if (id === request && active) data.value = result }
    catch (cause) { if (id === request) await fail(cause) }
    finally { if (id === request) loading.value = false }
  }
  async function mutate(path: string, method: string, body?: unknown) {
    if (busy.value) return false
    busy.value = true; error.value = ''; notice.value = ''
    try {
      await api(path, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
      await load(); if (active) notice.value = '저장했습니다.'
      return true
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) await load()
      await fail(cause); return false
    } finally { busy.value = false }
  }
  return { data, loading, busy, error, notice, load, mutate }
}
